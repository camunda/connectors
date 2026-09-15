/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.outbound.TestValidationProvider;
import io.camunda.connector.slack.inbound.model.SlackSigningSecretConfiguration;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties;
import io.camunda.connector.slack.inbound.model.SlackWebhookProperties.SlackConnectorPropertiesWrapper;
import org.junit.jupiter.api.Test;

class SlackWebhookPropertiesCredentialTest {

  private static final String CREDENTIAL_SECRET = "signing-secret-from-credential";
  private static final String INLINE_SECRET = "signing-secret-from-element-template";

  private static String properties(String slackCredential, String slackSigningSecret) {
    return """
        {
          "inbound": {
            "context": "slackTest",
            %s
            %s
            "verificationExpression": null
          }
        }
        """
        .formatted(slackCredential, slackSigningSecret);
  }

  private static SlackWebhookProperties bind(String properties) {
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();
    return new SlackWebhookProperties(
        context.bindProperties(SlackConnectorPropertiesWrapper.class));
  }

  @Test
  void credentialBoundAndNoInlineSecret_usesTheCredentialSecret() {
    var props =
        bind(
            properties(
                "\"slackCredential\": { \"signingSecret\": \"" + CREDENTIAL_SECRET + "\" },", ""));

    assertThat(props.slackSigningSecret()).isEqualTo(CREDENTIAL_SECRET);
  }

  @Test
  void noCredentialAndInlineSecret_usesTheInlineSecret() {
    var props = bind(properties("", "\"slackSigningSecret\": \"" + INLINE_SECRET + "\","));

    assertThat(props.slackSigningSecret()).isEqualTo(INLINE_SECRET);
  }

  @Test
  void bothPresent_theCredentialWins() {
    var props =
        bind(
            properties(
                "\"slackCredential\": { \"signingSecret\": \"" + CREDENTIAL_SECRET + "\" },",
                "\"slackSigningSecret\": \"" + INLINE_SECRET + "\","));

    assertThat(props.slackSigningSecret()).isEqualTo(CREDENTIAL_SECRET);
  }

  @Test
  void credentialBoundAndTheInlineFieldLeftBlankByModeler_usesTheCredentialSecret() {
    var props =
        bind(
            properties(
                "\"slackCredential\": { \"signingSecret\": \"" + CREDENTIAL_SECRET + "\" },",
                "\"slackSigningSecret\": \"\","));

    assertThat(props.slackSigningSecret()).isEqualTo(CREDENTIAL_SECRET);
  }

  @Test
  void aBoundCredentialSuppliesTheSecretTheSignatureVerifierIsBuiltFrom() {
    var props =
        bind(
            properties(
                "\"slackCredential\": { \"signingSecret\": \"" + CREDENTIAL_SECRET + "\" },", ""));

    assertThat(props.signatureVerifier()).isNotNull();
  }

  @Test
  void neitherPresent_bindingFailsNamingBothSources() {
    assertThatThrownBy(() -> bind(properties("", "")))
        .hasMessageContaining("reusable credential or the element template");
  }

  @Test
  void credentialWithABlankSecret_isRejectedByTheCascadingValidation() {
    assertThatThrownBy(
            () -> bind(properties("\"slackCredential\": { \"signingSecret\": \"  \" },", "")))
        .hasMessageContaining("signingSecret");
  }

  @Test
  void theCredentialDoesNotLeakItsSecretIntoToString() {
    assertThat(new SlackSigningSecretConfiguration(CREDENTIAL_SECRET).toString())
        .doesNotContain(CREDENTIAL_SECRET)
        .contains("REDACTED");
  }

  @Test
  void thePropertiesRedactTheBoundCredential() {
    var properties =
        new SlackWebhookProperties(
            "ctx", new SlackSigningSecretConfiguration(CREDENTIAL_SECRET), null, null);

    assertThat(properties.toString())
        .doesNotContain(CREDENTIAL_SECRET)
        .contains("slackCredential=[REDACTED]");
  }

  @Test
  void thePropertiesRedactTheInlineSecretAndReportThatNoCredentialIsBound() {
    var properties = new SlackWebhookProperties("ctx", null, INLINE_SECRET, null);

    assertThat(properties.toString())
        .doesNotContain(INLINE_SECRET)
        .contains("slackCredential=null")
        .contains("slackSigningSecret=[REDACTED]");
  }
}
