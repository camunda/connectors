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
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import io.camunda.connector.slack.outbound.model.MessageType;
import io.camunda.connector.slack.outbound.model.SlackTokenConfiguration;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class SlackRequestCredentialTest {

  private static final String CREDENTIAL_TOKEN = "xoxb-token-from-credential";
  private static final String INLINE_TOKEN = "xoxb-token-from-element-template";
  private static final String MESSAGE_TEXT = "ada.lovelace@example.com was invited";

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

  @Test
  void theRequestRedactsTheCredentialAndTheDataItCarries() {
    var request =
        new SlackRequest<>(new SlackTokenConfiguration(CREDENTIAL_TOKEN), null, messageData());

    assertThat(request.toString())
        .doesNotContain(CREDENTIAL_TOKEN)
        .doesNotContain(MESSAGE_TEXT)
        .contains("slackCredential=[REDACTED]")
        .contains("data=[REDACTED]");
  }

  @Test
  void theRequestRedactsTheInlineTokenAndReportsThatNoCredentialIsBound() {
    var request = new SlackRequest<>(null, INLINE_TOKEN, messageData());

    assertThat(request.toString())
        .doesNotContain(INLINE_TOKEN)
        .doesNotContain(MESSAGE_TEXT)
        .contains("slackCredential=null")
        .contains("data=[REDACTED]");
  }

  private static ChatPostMessageData messageData() {
    return new ChatPostMessageData(
        "#general", null, MessageType.plainText, MESSAGE_TEXT, null, null);
  }
}
