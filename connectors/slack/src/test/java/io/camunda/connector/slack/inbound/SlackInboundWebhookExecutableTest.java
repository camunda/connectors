/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound;

import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.COMMAND_RESPONSE_TEXT_DEFAULT_VALUE;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.COMMAND_RESPONSE_TEXT_KEY;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.COMMAND_RESPONSE_TYPE_DEFAULT_VALUE;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.COMMAND_RESPONSE_TYPE_KEY;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.FORM_VALUE_COMMAND;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.HEADER_SLACK_REQUEST_TIMESTAMP;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.HEADER_SLACK_SIGNATURE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlackInboundWebhookExecutableTest {

  private static final String URL_VERIFICATION_REQUEST =
      "{\"token\":\"qQqQqQqQqQqQqQqQqQ\",\"challenge\":\"aAaAaAaAaAaAaAaAaAaA\",\"type\":\"url_verification\"}";

  private static final String ARBITRARY_SLACK_REQUEST =
      "{\"token\":\"qQqQqQqQqQqQqQqQqQ\",\"type\":\"myType\",\"event\":{\"user\":{\"id\":\"aAaAaAaAaAaAaA\"}}}";

  private static final String SLASH_COMMAND =
      "token=qwertyuiop"
          + "&team_id=T0500000000"
          + "&team_domain=mydomain"
          + "&channel_id=C050000000"
          + "&channel_name=channel1"
          + "&user_id=U050000000"
          + "&user_name=tester.testerson"
          + "&command=%2Ftest123"
          + "&text=hello+world"
          + "&api_app_id=A050001111"
          + "&is_enterprise_install=false"
          + "&response_url=https%3A%2F%2Fwww.test.com"
          + "&trigger_id=1111111.222222.333333";

  private static final String SLACK_SIGNING_KEY = "mySecretValue";
  protected static final String FIELD_TYPE = "type";
  protected static final String FIELD_CHALLENGE = "challenge";

  @Mock private InboundConnectorContext ctx;

  private SlackInboundWebhookExecutable testObject;

  @BeforeEach
  void beforeEach() {
    testObject = new SlackInboundWebhookExecutable();
  }

  @Test
  void triggerWebhook_RegularEvent_HappyCase() throws Exception {
    Map<String, Object> props =
        Map.of("inbound", Map.of("context", "slackTest", "slackSigningSecret", SLACK_SIGNING_KEY));
    when(ctx.getProperties()).thenReturn(props);

    final var requestTimeStamp = String.valueOf(now().toInstant().toEpochMilli());
    Map<String, String> headers =
        Map.of(
            HEADER_SLACK_SIGNATURE,
            slackCurrentSignature(requestTimeStamp, ARBITRARY_SLACK_REQUEST),
            HEADER_SLACK_REQUEST_TIMESTAMP,
            requestTimeStamp);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(ARBITRARY_SLACK_REQUEST.getBytes(UTF_8));

    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    assertNotNull(result);
    assertThat(result.request().body()).isInstanceOf(Map.class);
    assertThat((Map) result.request().body()).containsEntry(FIELD_TYPE, "myType");
  }

  @Test
  void triggerWebhook_InvalidSignature_ThrowsException() throws Exception {
    Map<String, Object> props =
        Map.of("inbound", Map.of("context", "slackTest", "slackSigningSecret", SLACK_SIGNING_KEY));
    when(ctx.getProperties()).thenReturn(props);

    final var requestTimeStamp = String.valueOf(now().toInstant().toEpochMilli());
    Map<String, String> headers =
        Map.of(
            HEADER_SLACK_SIGNATURE,
            slackCurrentSignature(
                String.valueOf(now().minusDays(1).toInstant().toEpochMilli()),
                ARBITRARY_SLACK_REQUEST),
            HEADER_SLACK_REQUEST_TIMESTAMP,
            requestTimeStamp);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(ARBITRARY_SLACK_REQUEST.getBytes(UTF_8));

    testObject.activate(ctx);
    assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_UrlVerificationEvent_ReturnsChallengeBack() throws Exception {
    Map<String, Object> props =
        Map.of("inbound", Map.of("context", "slackTest", "slackSigningSecret", SLACK_SIGNING_KEY));
    when(ctx.getProperties()).thenReturn(props);

    final var requestTimeStamp = String.valueOf(now().toInstant().toEpochMilli());
    Map<String, String> headers =
        Map.of(
            HEADER_SLACK_SIGNATURE,
            slackCurrentSignature(requestTimeStamp, URL_VERIFICATION_REQUEST),
            HEADER_SLACK_REQUEST_TIMESTAMP,
            requestTimeStamp);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(URL_VERIFICATION_REQUEST.getBytes(UTF_8));

    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    assertNotNull(result);
    assertThat(result.request().body()).isInstanceOf(Map.class);
    assertThat((Map) result.request().body())
        .containsEntry(FIELD_CHALLENGE, "aAaAaAaAaAaAaAaAaAaA");
  }

  @Test
  void triggerWebhook_SlashCommand_HappyCase() throws Exception {
    Map<String, Object> props =
        Map.of("inbound", Map.of("context", "slackTest", "slackSigningSecret", SLACK_SIGNING_KEY));
    when(ctx.getProperties()).thenReturn(props);

    final var requestTimeStamp = String.valueOf(now().toInstant().toEpochMilli());
    Map<String, String> headers =
        Map.of(
            HEADER_SLACK_SIGNATURE,
            slackCurrentSignature(requestTimeStamp, SLASH_COMMAND),
            HEADER_SLACK_REQUEST_TIMESTAMP,
            requestTimeStamp,
            HttpHeaders.CONTENT_TYPE,
            MediaType.FORM_DATA.toString());
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(SLASH_COMMAND.getBytes(UTF_8));

    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    assertNotNull(result);
    assertThat(result.request().body()).isInstanceOf(Map.class);
    assertThat((Map) result.response().body())
        .containsEntry(COMMAND_RESPONSE_TYPE_KEY, COMMAND_RESPONSE_TYPE_DEFAULT_VALUE);
    assertThat((Map) result.response().body())
        .containsEntry(COMMAND_RESPONSE_TEXT_KEY, COMMAND_RESPONSE_TEXT_DEFAULT_VALUE);
    assertThat((Map) result.connectorData()).containsEntry(FORM_VALUE_COMMAND, "/test123");
    assertThat((Map) result.connectorData()).containsEntry("text", "hello world");
  }

  @Test
  void triggerWebhook_SlashCommandMalformedContentType_HappyCase() throws Exception {
    Map<String, Object> props =
        Map.of("inbound", Map.of("context", "slackTest", "slackSigningSecret", SLACK_SIGNING_KEY));
    when(ctx.getProperties()).thenReturn(props);

    final var requestTimeStamp = String.valueOf(now().toInstant().toEpochMilli());
    Map<String, String> headers =
        Map.of(
            HEADER_SLACK_SIGNATURE,
            slackCurrentSignature(requestTimeStamp, SLASH_COMMAND),
            HEADER_SLACK_REQUEST_TIMESTAMP,
            requestTimeStamp,
            "cOnTent-tyPE",
            MediaType.FORM_DATA.toString());
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(SLASH_COMMAND.getBytes(UTF_8));

    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    assertNotNull(result);
    assertThat(result.request().body()).isInstanceOf(Map.class);
    assertThat((Map) result.response().body())
        .containsEntry(COMMAND_RESPONSE_TYPE_KEY, COMMAND_RESPONSE_TYPE_DEFAULT_VALUE);
    assertThat((Map) result.response().body())
        .containsEntry(COMMAND_RESPONSE_TEXT_KEY, COMMAND_RESPONSE_TEXT_DEFAULT_VALUE);
    assertThat((Map) result.connectorData()).containsEntry(FORM_VALUE_COMMAND, "/test123");
    assertThat((Map) result.connectorData()).containsEntry("text", "hello world");
  }

  // generate fresh signature because it expires fast
  private static String slackCurrentSignature(String requestTimestamp, String requestBody) {
    SlackSignature.Generator gen = new SlackSignature.Generator(SLACK_SIGNING_KEY);
    return gen.generate(requestTimestamp, requestBody);
  }
}
