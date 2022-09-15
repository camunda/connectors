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

package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SendGridRequestTest extends BaseTest {

  private SendGridRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    validator = new Validator();
    context = getContextBuilderWithSecrets().build();
  }

  @ParameterizedTest(name = "Validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request without one required field
    request = gson.fromJson(input, SendGridRequest.class);
    // When
    request.validateWith(validator);
    // Then expect exception that one required field not set
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("errors: Property required:");
  }

  @ParameterizedTest(name = "Should replace secrets in request")
  @MethodSource("successReplaceSecretsRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistRequest(String input) {
    // Given request with secrets
    request = gson.fromJson(input, SendGridRequest.class);
    // When
    request.replaceSecrets(context.getSecretStore());
    // Then should replace secrets
    assertThat(request.getApiKey()).isEqualTo(ActualValue.API_KEY);
    assertThat(request.getFrom().getEmail()).isEqualTo(ActualValue.SENDER_EMAIL);
    assertThat(request.getFrom().getName()).isEqualTo(ActualValue.SENDER_NAME);
    assertThat(request.getTo().getEmail()).isEqualTo(ActualValue.RECEIVER_EMAIL);
    assertThat(request.getTo().getName()).isEqualTo(ActualValue.RECEIVER_NAME);
  }

  @ParameterizedTest(name = "Should replace secrets in content")
  @MethodSource("successReplaceSecretsContentRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistContentRequest(String input) {
    // Given request with secrets
    request = gson.fromJson(input, SendGridRequest.class);
    // When
    request.replaceSecrets(context.getSecretStore());
    // Then should replace secrets
    assertThat(request.getContent().getSubject()).isEqualTo(ActualValue.Content.SUBJECT);
    assertThat(request.getContent().getType()).isEqualTo(ActualValue.Content.TYPE);
    assertThat(request.getContent().getValue()).isEqualTo(ActualValue.Content.VALUE);
  }

  @ParameterizedTest(name = "Should replace secrets in template")
  @MethodSource("successReplaceSecretsTemplateRequestCases")
  void replaceSecrets_shouldReplaceSecretsWhenExistTemplateRequest(String input) {
    // Given request with secrets
    request = gson.fromJson(input, SendGridRequest.class);
    // When
    request.replaceSecrets(context.getSecretStore());
    // Then should replace secrets
    assertThat(request.getTemplate().getId()).isEqualTo(ActualValue.Template.ID);
    assertThat(request.getTemplate().getData().get(ActualValue.Template.Data.KEY_SHIP_ADDRESS))
        .isEqualTo(ActualValue.Template.Data.SHIP_ADDRESS);
    assertThat(request.getTemplate().getData().get(ActualValue.Template.Data.KEY_ACCOUNT_NAME))
        .isEqualTo(ActualValue.Template.Data.ACCOUNT_NAME);
    assertThat(request.getTemplate().getData().get(ActualValue.Template.Data.KEY_SHIP_ZIP))
        .isEqualTo(ActualValue.Template.Data.SHIP_ZIP);
  }
}
