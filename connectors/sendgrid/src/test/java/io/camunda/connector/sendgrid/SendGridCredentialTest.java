/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.sendgrid.model.SendGridRequest;
import org.junit.jupiter.api.Test;

class SendGridCredentialTest extends BaseTest {

  private static final String MAIL =
      """
      "from": {"name": "John Doe", "email": "john.doe@example.com"},
      "to": {"name": "Jane Doe", "email": "jane.doe@example.com"},
      "content": {"subject": "Hi", "type": "text/plain", "value": "Hello"}
      """;

  private SendGridRequest bind(String authentication) {
    return getContextBuilderWithSecrets()
        .variables("{" + authentication + MAIL + "}")
        .build()
        .bindVariables(SendGridRequest.class);
  }

  @Test
  void usesCredentialKey_whenOnlyCredentialBound() {
    var request = bind("\"sendGridCredential\": {\"apiKey\": \"credential-key\"},");

    assertThat(request.getApiKey()).isEqualTo("credential-key");
  }

  @Test
  void usesInlineKey_whenNoCredentialBound() {
    var request = bind("\"apiKey\": \"inline-key\",");

    assertThat(request.getApiKey()).isEqualTo("inline-key");
  }

  @Test
  void credentialWins_whenBothPresent() {
    var request =
        bind(
            "\"sendGridCredential\": {\"apiKey\": \"credential-key\"}, \"apiKey\": \"inline-key\",");

    assertThat(request.getApiKey()).isEqualTo("credential-key");
  }

  @Test
  void ignoresBlankInlineKey_whenCredentialBound() {
    var request =
        bind("\"sendGridCredential\": {\"apiKey\": \"credential-key\"}, \"apiKey\": \"\",");

    assertThat(request.getApiKey()).isEqualTo("credential-key");
  }

  @Test
  void resolvesSecretsInCredential() {
    var request = bind("\"sendGridCredential\": {\"apiKey\": \"{{secrets.SENDGRID_API_KEY}}\"},");

    assertThat(request.getApiKey()).isEqualTo(ActualValue.API_KEY);
  }

  @Test
  void failsNamingBothSources_whenNeitherPresent() {
    assertThatThrownBy(() -> bind("\"apiKey\": \" \","))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No API key provided by the reusable credential or the element template");
  }

  @Test
  void fails_whenCredentialHasNoKey() {
    assertThatThrownBy(() -> bind("\"sendGridCredential\": {\"apiKey\": \"\"},"))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void redactsCredentialKey() {
    var request = bind("\"sendGridCredential\": {\"apiKey\": \"credential-key\"},");

    assertThat(request.toString()).doesNotContain("credential-key");
  }
}
