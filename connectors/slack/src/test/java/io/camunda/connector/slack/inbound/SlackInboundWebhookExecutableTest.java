/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound;

import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.FIELD_CHALLENGE;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.FIELD_TYPE;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.HEADER_SLACK_REQUEST_TIMESTAMP;
import static io.camunda.connector.slack.inbound.SlackInboundWebhookExecutable.HEADER_SLACK_SIGNATURE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
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

  private static final String SLACK_SIGNING_KEY = "mySecretValue";

  @Mock private InboundConnectorContext ctx;
  @Mock private InboundConnectorProperties props;

  private SlackInboundWebhookExecutable testObject;

  @BeforeEach
  void beforeEach() {
    testObject = new SlackInboundWebhookExecutable();
  }

  @Test
  void triggerWebhook_RegularEvent_HappyCase() throws Exception {
    when(props.getProperties())
        .thenReturn(
            Map.of(
                "inbound.context", "slackTest", "inbound.slackSigningSecret", SLACK_SIGNING_KEY));
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
    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry(FIELD_TYPE, "myType");
  }

  @Test
  void triggerWebhook_InvalidSignature_ThrowsException() throws Exception {
    when(props.getProperties())
        .thenReturn(
            Map.of(
                "inbound.context", "slackTest", "inbound.slackSigningSecret", SLACK_SIGNING_KEY));
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
    when(props.getProperties())
        .thenReturn(
            Map.of(
                "inbound.context", "slackTest", "inbound.slackSigningSecret", SLACK_SIGNING_KEY));
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
    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry(FIELD_CHALLENGE, "aAaAaAaAaAaAaAaAaAaA");
  }

  // generate fresh signature because it expires fast
  private static String slackCurrentSignature(String requestTimestamp, String requestBody) {
    SlackSignature.Generator gen = new SlackSignature.Generator(SLACK_SIGNING_KEY);
    return gen.generate(requestTimestamp, requestBody);
  }
}
