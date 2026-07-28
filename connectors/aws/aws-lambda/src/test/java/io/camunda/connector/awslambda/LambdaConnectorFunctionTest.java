/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

class LambdaConnectorFunctionTest extends BaseTest {

  private LambdaClient lambdaClient;
  private AwsLambdaSupplier supplier;
  private LambdaConnectorFunction function;
  private InvokeResponse invokeResponse;

  @BeforeEach
  public void init() {
    supplier = mock(AwsLambdaSupplier.class);
    lambdaClient = mock(LambdaClient.class);
    function = new LambdaConnectorFunction(supplier, objectMapper);
    invokeResponse =
        InvokeResponse.builder()
            .statusCode(200)
            .payload(ACTUAL_SDK_BYTES_PAYLOAD)
            .executedVersion(EXECUTED_VERSION)
            .build();
  }

  @ParameterizedTest(name = "execute connector with valid data")
  @MethodSource("successRequestCases")
  public void execute_shouldExecuteAndReturnStatusOkAndActualPayload(String input) {
    // Given valid data
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    ArgumentCaptor<String> regionCaptor = ArgumentCaptor.forClass(String.class);
    when(supplier.awsLambdaService(any(), regionCaptor.capture())).thenReturn(lambdaClient);
    when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);
    // When connector execute
    Object execute = function.execute(context);
    // Then return connector result and result status = 200 and payload
    assertThat(execute).isInstanceOf(AwsLambdaResult.class);
    AwsLambdaResult result = (AwsLambdaResult) execute;
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getPayload()).isEqualTo(ACTUAL_PAYLOAD);
    // Then the region actually passed to the client supplier must be the one AwsUtils computes
    // from the bound request (configuration.region, falling back to the deprecated
    // awsFunction.region) - several success cases omit configuration.region entirely and rely on
    // that fallback, so this pins LambdaConnectorFunction's own region-resolution call rather than
    // letting an untyped any() swallow a wrong-source or swapped-argument regression.
    var request = context.bindVariables(AwsLambdaRequest.class);
    var expectedRegion =
        AwsUtils.extractRegionOrDefault(
            request.getConfiguration(), request.getAwsFunction().getRegion());
    assertThat(regionCaptor.getValue()).isEqualTo(expectedRegion);
  }

  @ParameterizedTest(name = "execute connector with invalid data # {index}")
  @MethodSource("failRequestCases")
  public void execute_shouldThrowExceptionWhenDataNotValid(String input) {
    // Given invalid data (without all required fields)
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    when(supplier.awsLambdaService(any(), any())).thenReturn(lambdaClient);
    when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);
    // When connector execute
    // Then throw IllegalArgumentException
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> function.execute(context),
            "ConnectorInputException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }
}
