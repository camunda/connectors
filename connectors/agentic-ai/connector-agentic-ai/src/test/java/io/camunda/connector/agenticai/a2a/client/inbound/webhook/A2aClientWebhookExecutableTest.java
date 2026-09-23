/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.a2a.spec.Task;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus.TaskState;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class A2aClientWebhookExecutableTest {

  private static final String HMAC_HEADER = "HMAC-Signature-Header";
  private static final String TASK_JSON =
      """
          {
            "kind": "task",
            "id": "task-123",
            "contextId": "ctx-123",
            "status": {
              "state": "completed"
            },
            "metadata": {
              "key1": "value1",
              "key2": "value2"
            },
            "artifacts": [],
            "history": []
          }
          """;

  private A2aClientWebhookExecutable webhookExecutable;

  @BeforeEach
  void beforeEach() {
    A2aSdkObjectConverter a2aSdkObjectConverter = mock(A2aSdkObjectConverter.class);
    webhookExecutable = new A2aClientWebhookExecutable(a2aSdkObjectConverter, new ObjectMapper());

    lenient()
        .when(a2aSdkObjectConverter.convert(any(Task.class)))
        .thenAnswer(
            invocation -> {
              Task task = invocation.getArgument(0);
              return new A2aTask(
                  task.getId(),
                  task.getContextId(),
                  new A2aTaskStatus(TaskState.WORKING, null, null),
                  Map.of("key1", "value1", "key2", "value2"),
                  List.of(),
                  List.of());
            });
  }

  @Test
  void triggerWebhook_ValidA2aTask_ReturnsMappedRequest() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "a2aWebhookContext",
                        "clientResponse", "=task",
                        "auth", Map.of("type", "NONE"))))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    assertThat(result.request()).isNotNull();
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
    A2aTask resultTask = (A2aTask) result.request().body();
    assertThat(resultTask.id()).isEqualTo("task-123");
    assertThat(resultTask.contextId()).isEqualTo("ctx-123");
  }

  @Test
  void triggerWebhook_InvalidJson_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "a2aWebhookContext",
                        "clientResponse", "=task",
                        "auth", Map.of("type", "NONE"))))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.rawBody()).thenReturn("invalid json".getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);

    assertThatThrownBy(() -> webhookExecutable.triggerWebhook(payload))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void triggerWebhook_HmacEnabled_ValidSignature_Success() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name())))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HMAC_HEADER,
                "82874661fa330e9fa686ab66f78bad7dd198cdb1812e6fe84e7e83c1735501e1"));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
  }

  @Test
  void triggerWebhook_HmacEnabled_InvalidSignature_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name())))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HMAC_HEADER,
                "invalid-signature"));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);

    var exception = catchException(() -> webhookExecutable.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookSecurityException.class);
    assertThat(exception).hasMessageContaining("HMAC signature check didn't pass");
  }

  @Test
  void activate_HmacTimestampScopeWithoutHeaderConfigured_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]")))
            // hmacTimestampHeader intentionally omitted
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_HmacTimestampHeaderMatchesSignatureHeader_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        HMAC_HEADER.toLowerCase())))
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("must be different");
  }

  @Test
  void activate_HmacToleranceZeroOrNegative_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp",
                        "hmacTolerance",
                        "PT0S")))
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_HmacToleranceMalformed_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp",
                        "hmacTolerance",
                        "300")))
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_HmacToleranceWithFractionalSeconds_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp",
                        "hmacTolerance",
                        "PT0.5S")))
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("whole-second duration");
  }

  @Test
  void activate_HmacToleranceIgnoredWhenHmacDisabled_DoesNotFailDeployment() {
    // The tolerance field is only meaningful (and only shown in the Modeler) while HMAC is
    // enabled; a leftover invalid value from a previous configuration must not block activation
    // once HMAC is switched off.
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        disabled.name(),
                        "hmacTolerance",
                        "PT0S")))
            .build();

    assertThat(catchException(() -> webhookExecutable.activate(ctx))).isNull();
  }

  @Test
  void activate_HmacToleranceIgnoredWhenTimestampScopeNotSelected_DoesNotFailDeployment() {
    // Same rationale as the HMAC-disabled case above, but for HMAC enabled with a scope that
    // doesn't include 'timestamp' (e.g. after switching back to the default 'body' scope): the
    // tolerance value is still irrelevant and a leftover invalid one must not block activation.
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\"]",
                        "hmacTolerance",
                        "PT0S")))
            .build();

    assertThat(catchException(() -> webhookExecutable.activate(ctx))).isNull();
  }

  @Test
  void activate_UnsupportedHmacScopeCombination_ThrowsException() {
    // [timestamp, url] strips down to [url] alone, which the encoding-strategy factory has no
    // strategy for — must fail at activation, not on every incoming request.
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"timestamp\",\"url\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp")))
            .build();

    assertThatThrownBy(() -> webhookExecutable.activate(ctx))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_UnsupportedHmacScopeCombinationIgnoredWhenHmacDisabled_DoesNotFailDeployment() {
    // The same combination must not block activation when HMAC is disabled entirely — the scope
    // configuration is then irrelevant, matching the other HMAC-disabled passthrough cases above.
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        disabled.name(),
                        "hmacScopes",
                        "=[\"timestamp\",\"url\"]")))
            .build();

    assertThat(catchException(() -> webhookExecutable.activate(ctx))).isNull();
  }

  @Test
  void triggerWebhook_HmacTimestampWithinTolerance_Success()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = Instant.now().getEpochSecond();
    String timestamp = Long.toString(now);
    byte[] body = TASK_JSON.getBytes(StandardCharsets.UTF_8);
    String signature = hmacHex("mySecret123", timestamp, body);

    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp")))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HMAC_HEADER,
                signature,
                "X-HMAC-Timestamp",
                timestamp));
    when(payload.rawBody()).thenReturn(body);

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
  }

  @Test
  void triggerWebhook_HmacTimestampStale_ThrowsException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // an hour old; well outside the default 300-second tolerance
    long staleTimestamp = Instant.now().getEpochSecond() - 3600;
    String timestamp = Long.toString(staleTimestamp);
    byte[] body = TASK_JSON.getBytes(StandardCharsets.UTF_8);
    String signature = hmacHex("mySecret123", timestamp, body);

    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp")))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HMAC_HEADER,
                signature,
                "X-HMAC-Timestamp",
                timestamp));
    when(payload.rawBody()).thenReturn(body);

    webhookExecutable.activate(ctx);

    var exception = catchException(() -> webhookExecutable.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookSecurityException.class);
  }

  @Test
  void triggerWebhook_HmacTimestampMissing_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecret123",
                        "hmacHeader",
                        HMAC_HEADER,
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes",
                        "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader",
                        "X-HMAC-Timestamp")))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HMAC_HEADER,
                "82874661fa330e9fa686ab66f78bad7dd198cdb1812e6fe84e7e83c1735501e1"));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);

    var exception = catchException(() -> webhookExecutable.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookSecurityException.class);
  }

  private static String hmacHex(String secret, String timestamp, byte[] body)
      throws NoSuchAlgorithmException, InvalidKeyException {
    byte[] timestampPrefix = (timestamp + ":").getBytes(StandardCharsets.UTF_8);
    byte[] bytesToSign = new byte[timestampPrefix.length + body.length];
    System.arraycopy(timestampPrefix, 0, bytesToSign, 0, timestampPrefix.length);
    System.arraycopy(body, 0, bytesToSign, timestampPrefix.length, body.length);

    Mac mac = Mac.getInstance(HMACAlgoCustomerChoice.sha_256.getAlgoReference());
    mac.init(
        new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            HMACAlgoCustomerChoice.sha_256.getAlgoReference()));
    return HexFormat.of().formatHex(mac.doFinal(bytesToSign));
  }

  @Test
  void triggerWebhook_HmacDisabled_NoSignatureValidation() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "NONE"),
                        "shouldValidateHmac",
                        disabled.name())))
            .build();

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
  }

  @Test
  void triggerWebhook_BasicAuth_ValidCredentials_Success() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "BASIC", "username", "user", "password", "pass"))))
            .build();

    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HttpHeaders.AUTHORIZATION,
                basicAuth));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
  }

  @Test
  void triggerWebhook_BasicAuth_InvalidCredentials_ThrowsException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "a2aWebhookContext",
                        "clientResponse",
                        "=task",
                        "auth",
                        Map.of("type", "BASIC", "username", "user", "password", "pass"))))
            .build();

    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("wrong:creds".getBytes());

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                HttpHeaders.AUTHORIZATION,
                basicAuth));

    webhookExecutable.activate(ctx);

    var exception = catchException(() -> webhookExecutable.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookSecurityException.class);
  }

  @Test
  void triggerWebhook_WithHeadersAndParams_PassedToResult() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "a2aWebhookContext",
                        "clientResponse", "=task",
                        "auth", Map.of("type", "NONE"))))
            .build();

    Map<String, String> headers =
        Map.of(
            HttpHeaders.CONTENT_TYPE,
            MediaType.JSON_UTF_8.toString(),
            "X-Custom-Header",
            "customValue");
    Map<String, String> params = Map.of("param1", "value1", "param2", "value2");

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers()).thenReturn(headers);
    when(payload.params()).thenReturn(params);
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertThat(result).isNotNull();
    MappedHttpRequest request = result.request();
    assertThat(request.headers()).isEqualTo(headers);
    assertThat(request.params()).isEqualTo(params);
  }
}
