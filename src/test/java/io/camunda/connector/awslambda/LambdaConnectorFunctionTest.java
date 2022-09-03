/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.awslambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeResult;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LambdaConnectorFunctionTest extends BaseTest {

  private AWSLambda awsLambda;
  private AwsLambdaSupplier supplier;
  private LambdaConnectorFunction function;
  private InvokeResult invokeResult;

  @BeforeEach
  public void init() {
    supplier = mock(AwsLambdaSupplier.class);
    awsLambda = mock(AWSLambda.class);
    function = new LambdaConnectorFunction(supplier, gson);
    invokeResult =
        new InvokeResult()
            .withStatusCode(200)
            .withPayload(ACTUAL_BYTEBUFFER_PAYLOAD)
            .withExecutedVersion(EXECUTED_VERSION);
  }

  @ParameterizedTest(name = "execute connector with valid data")
  @MethodSource("successRequestCases")
  public void execute_shouldExecuteAndReturnStatusOkAndActualPayload(String input) {
    // Given valid data
    AwsLambdaRequest connectorRequest = gson.fromJson(input, AwsLambdaRequest.class);
    ConnectorContext context = getContextBuilderWithSecrets().variables(connectorRequest).build();
    when(supplier.awsLambdaService(any(), any())).thenReturn(awsLambda);
    when(awsLambda.invoke(any())).thenReturn(invokeResult);
    // When connector execute
    Object execute = function.execute(context);
    // Then return connector result and result status = 200 and payload
    assertThat(execute).isInstanceOf(AwsLambdaResult.class);
    AwsLambdaResult result = (AwsLambdaResult) execute;
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getPayload()).isEqualTo(ACTUAL_PAYLOAD);
  }

  @ParameterizedTest(name = "execute connector with invalid data # {index}")
  @MethodSource("failRequestCases")
  public void execute_shouldThrowExceptionWhenDataNotValid(String input) {
    // Given invalid data (without all required fields)
    AwsLambdaRequest connectorRequest = gson.fromJson(input, AwsLambdaRequest.class);
    ConnectorContext context = getContextBuilderWithSecrets().variables(connectorRequest).build();
    when(supplier.awsLambdaService(any(), any())).thenReturn(awsLambda);
    when(awsLambda.invoke(any())).thenReturn(invokeResult);
    // When connector execute
    // Then throw IllegalArgumentException
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> function.execute(context),
            "IllegalArgumentException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage().contains("Property required:")).isTrue();
  }
}
