/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.model.impl.AwsBaseAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.awslambda.BaseTest;
import io.camunda.connector.impl.ConnectorInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AwsLambdaRequestTest extends BaseTest {

  private AwsLambdaRequest request;
  private OutboundConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new AwsLambdaRequest();
    request.setAuthentication(new AwsBaseAuthentication());
    request.setConfiguration(new AwsBaseConfiguration());
    request.setAwsFunction(new FunctionRequestData());
    context = getContextBuilderWithSecrets().build(); // builder with secrets
  }

  @ParameterizedTest(name = "Should throw exception when validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(AwsLambdaRequest request) {
    // Given request , where one field is null
    // When request validate
    // Then we except exception with message
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  @ParameterizedTest(name = "Should replace secrets")
  @MethodSource("successSecretsRequestCases")
  void replaceSecrets_shouldReplaceSecrets(AwsLambdaRequest request) {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    // When replace secrets
    context.replaceSecrets(request);
    // Then
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getConfiguration().getRegion()).isEqualTo(ACTUAL_FUNCTION_REGION);
    assertThat(request.getAwsFunction().getFunctionName()).isEqualTo(ACTUAL_FUNCTION_NAME);
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    // Given request with data that not started from secrets. and context with secret store
    request.getAuthentication().setSecretKey(ACTUAL_SECRET_KEY);
    request.getAuthentication().setAccessKey(ACTUAL_ACCESS_KEY);
    request.getConfiguration().setRegion(ACTUAL_FUNCTION_REGION);
    request.getAwsFunction().setFunctionName(ACTUAL_FUNCTION_NAME);
    request.getAwsFunction().setPayload(ACTUAL_PAYLOAD);
    // When replace secrets
    context.replaceSecrets(request);
    // Then secrets must be not replaced
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getConfiguration().getRegion()).isEqualTo(ACTUAL_FUNCTION_REGION);
    assertThat(request.getAwsFunction().getFunctionName()).isEqualTo(ACTUAL_FUNCTION_NAME);
    assertThat(request.getAwsFunction().getPayload()).isEqualTo(ACTUAL_PAYLOAD);
  }
}
