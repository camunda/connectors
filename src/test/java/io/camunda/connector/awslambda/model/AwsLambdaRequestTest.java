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

package io.camunda.connector.awslambda.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
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
    request.setAuthentication(new AuthenticationRequestData());
    request.setAwsFunction(new FunctionRequestData());
    context = getContextBuilderWithSecrets().build(); // builder with secrets
  }

  @ParameterizedTest(name = "Should throw exception when validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request , where one field is null
    request = gson.fromJson(input, AwsLambdaRequest.class);
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
  void replaceSecrets_shouldReplaceSecrets(String input) {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    request = gson.fromJson(input, AwsLambdaRequest.class);
    // When replace secrets
    context.replaceSecrets(request);
    // Then
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getAwsFunction().getRegion()).isEqualTo(ACTUAL_FUNCTION_REGION);
    assertThat(request.getAwsFunction().getFunctionName()).isEqualTo(ACTUAL_FUNCTION_NAME);
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    // Given request with data that not started from secrets. and context with secret store
    request.getAuthentication().setSecretKey(ACTUAL_SECRET_KEY);
    request.getAuthentication().setAccessKey(ACTUAL_ACCESS_KEY);
    request.getAwsFunction().setRegion(ACTUAL_FUNCTION_REGION);
    request.getAwsFunction().setFunctionName(ACTUAL_FUNCTION_NAME);
    request.getAwsFunction().setPayload(ACTUAL_PAYLOAD);
    // When replace secrets
    context.replaceSecrets(request);
    // Then secrets must be not replaced
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getAwsFunction().getRegion()).isEqualTo(ACTUAL_FUNCTION_REGION);
    assertThat(request.getAwsFunction().getFunctionName()).isEqualTo(ACTUAL_FUNCTION_NAME);
    assertThat(request.getAwsFunction().getPayload()).isEqualTo(ACTUAL_PAYLOAD);
  }
}
