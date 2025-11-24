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
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
  void triggerWebhook_ValidA2aTask_HappyCase() {
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

    assertNotNull(result);
    assertNotNull(result.request());
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

    assertThrows(RuntimeException.class, () -> webhookExecutable.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_HmacEnabled_ValidSignature_Success() {
    String secret = "mySecret123";
    String hmacSignature = calculateHmac(TASK_JSON, secret, HMACAlgoCustomerChoice.sha_256);

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
                        secret,
                        "hmacHeader",
                        "X-Hub-Signature",
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
                "X-Hub-Signature",
                hmacSignature));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);
    var result = webhookExecutable.triggerWebhook(payload);

    assertNotNull(result);
    assertThat(result.request().body()).isInstanceOf(A2aTask.class);
  }

  @Test
  void triggerWebhook_HmacEnabled_InvalidSignature_ThrowsException() {
    String secret = "mySecret123";

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
                        secret,
                        "hmacHeader",
                        "X-Hub-Signature",
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
                "X-Hub-Signature",
                "invalid-signature"));
    when(payload.rawBody()).thenReturn(TASK_JSON.getBytes(StandardCharsets.UTF_8));

    webhookExecutable.activate(ctx);

    var exception = catchException(() -> webhookExecutable.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookSecurityException.class);
    assertThat(exception).hasMessageContaining("HMAC signature check didn't pass");
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

    assertNotNull(result);
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

    assertNotNull(result);
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

    assertNotNull(result);
    MappedHttpRequest request = result.request();
    assertThat(request.headers()).isEqualTo(headers);
    assertThat(request.params()).isEqualTo(params);
  }

  private String calculateHmac(String data, String secret, HMACAlgoCustomerChoice algorithm) {
    String algorithmName =
        switch (algorithm) {
          case sha_1 -> "HmacSHA1";
          case sha_256 -> "HmacSHA256";
          case sha_512 -> "HmacSHA512";
        };

    try {
      Mac mac = Mac.getInstance(algorithmName);
      SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), algorithmName);
      mac.init(secretKeySpec);
      byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hmacBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
