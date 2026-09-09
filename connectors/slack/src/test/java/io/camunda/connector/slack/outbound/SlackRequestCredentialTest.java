/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.slack.outbound.model.SlackTokenConfiguration;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

/**
 * Covers the outbound OAuth token matrix for the reusable Slack credential. Everything runs through
 * the real {@code bindVariables} path: a Modeler-generated diagram carries an empty string for the
 * inline token once a credential is bound, which only the full deserialization path exercises.
 */
class SlackRequestCredentialTest {

  private static final String CREDENTIAL_TOKEN = "xoxb-token-from-credential";
  private static final String INLINE_TOKEN = "xoxb-token-from-element-template";

  private static String variables(String slackCredential, String token) {
    return """
        {
          %s
          %s
          "method": "chat.postMessage",
          "data": { "channel": "C123ABC456", "text": "hello" }
        }
        """
        .formatted(slackCredential, token);
  }

  private static OutboundConnectorContextBuilder contextBuilder() {
    return OutboundConnectorContextBuilder.create().validation(new DefaultValidationProvider());
  }

  @Test
  void credentialBoundAndNoInlineToken_usesTheCredentialToken() {
    var context =
        contextBuilder()
            .variables(
                variables("\"slackCredential\": { \"token\": \"" + CREDENTIAL_TOKEN + "\" },", ""))
            .build();

    SlackRequest<?> request = context.bindVariables(SlackRequest.class);

    assertThat(request.token()).isEqualTo(CREDENTIAL_TOKEN);
  }

  @Test
  void noCredentialAndInlineToken_usesTheInlineToken() {
    var context =
        contextBuilder().variables(variables("", "\"token\": \"" + INLINE_TOKEN + "\",")).build();

    SlackRequest<?> request = context.bindVariables(SlackRequest.class);

    assertThat(request.token()).isEqualTo(INLINE_TOKEN);
  }

  @Test
  void bothPresent_theCredentialWins() {
    var context =
        contextBuilder()
            .variables(
                variables(
                    "\"slackCredential\": { \"token\": \"" + CREDENTIAL_TOKEN + "\" },",
                    "\"token\": \"" + INLINE_TOKEN + "\","))
            .build();

    SlackRequest<?> request = context.bindVariables(SlackRequest.class);

    assertThat(request.token()).isEqualTo(CREDENTIAL_TOKEN);
  }

  @Test
  void credentialBoundAndTheInlineFieldLeftBlankByModeler_usesTheCredentialToken() {
    var context =
        contextBuilder()
            .variables(
                variables(
                    "\"slackCredential\": { \"token\": \"" + CREDENTIAL_TOKEN + "\" },",
                    "\"token\": \"\","))
            .build();

    SlackRequest<?> request = context.bindVariables(SlackRequest.class);

    assertThat(request.token()).isEqualTo(CREDENTIAL_TOKEN);
  }

  @Test
  void neitherPresent_bindingFailsNamingBothSources() {
    var context = contextBuilder().variables(variables("", "")).build();

    assertThatThrownBy(() -> context.bindVariables(SlackRequest.class))
        .hasMessageContaining("reusable credential or the element template");
  }

  @Test
  void credentialWithABlankToken_isRejectedByTheCascadingValidation() {
    var context =
        contextBuilder()
            .variables(variables("\"slackCredential\": { \"token\": \"  \" },", ""))
            .build();

    assertThatThrownBy(() -> context.bindVariables(SlackRequest.class))
        .hasMessageContaining("token");
  }

  @Test
  void theCredentialDoesNotLeakItsTokenIntoToString() {
    assertThat(new SlackTokenConfiguration(CREDENTIAL_TOKEN).toString())
        .doesNotContain(CREDENTIAL_TOKEN)
        .contains("REDACTED");
  }
}
