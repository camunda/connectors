/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HttpWebhookExecutableTest {

  private HttpWebhookExecutable testObject;

  @BeforeEach
  void beforeEach() {
    testObject = new HttpWebhookExecutable();
  }

  @Test
  void triggerWebhook_JsonBody_HappyCase() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "auth", Map.of("type", "NONE"))))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNull(result.response());
    assertThat((Map) result.request().body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_ResponseExpression_HappyCase() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "responseExpression",
                        "=if request.body.key != null then {body: request.body.key} else null")))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNotNull(result.response());
    assertThat((Map) result.request().body()).containsEntry("key", "value");

    var request = new MappedHttpRequest(Map.of("key", "value"), null, null);
    var context = new WebhookResultContext(request, null, null);
    var response = result.response().apply(context);
    assertEquals("value", response.body());
  }

  @Test
  void triggerWebhook_FormDataBody_HappyCase() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("key1=value1&key2=value2".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertThat((Map) result.request().body()).containsEntry("key1", "value1");
    assertThat((Map) result.request().body()).containsEntry("key2", "value2");
  }

  @Test
  void triggerWebhook_UnknownJsonLikeBody_HappyCase() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.GEO_JSON.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertThat((Map) result.request().body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_BinaryData_RaisesException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_BINARY.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("Zm9sbG93IHRoZSB3aGl0ZSByYWJiaXQ=".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_HttpMethodNotAllowed_RaisesException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "get",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.post.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode())
        .isEqualTo(HttpResponseStatus.METHOD_NOT_ALLOWED.code());
  }

  @Test
  void triggerWebhook_HmacSignatureMatches_HappyCase() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "shouldValidateHmac", enabled.name(),
                        "hmacSecret", "mySecretKey",
                        "hmacHeader", "X-HMAC-Sig",
                        "hmacAlgorithm", HMACAlgoCustomerChoice.sha_256.name(),
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertThat((Map) result.request().body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_HmacSignatureDidntMatch_RaisesException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "webhookContext",
                        "method", "any",
                        "shouldValidateHmac", enabled.name(),
                        "hmacSecret", "mySecretKey",
                        "hmacHeader", "X-HMAC-Sig",
                        "hmacAlgorithm", HMACAlgoCustomerChoice.sha_256.name(),
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                "X-HMAC-Sig",
                "123132313214533154234132534123452")); // not correct HMAC
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode())
        .isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());
  }

  @Test
  void triggerWebhook_BadApiKey_RaisesException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "shouldValidateHmac",
                        disabled.name(),
                        "auth",
                        Map.of(
                            "type", "APIKEY",
                            "apiKey", "myApiKey",
                            "apiKeyLocator", "=request.headers.Authorization"))))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HttpHeaders.CONTENT_TYPE,
                MediaType.JSON_UTF_8.toString(),
                "Authorization",
                "notMyApiKey")); // not correct API key
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode())
        .isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());
  }

  @Test
  void triggerWebhook_MissingApiKey_RaisesException() {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "shouldValidateHmac",
                        disabled.name(),
                        "auth",
                        Map.of(
                            "type", "APIKEY",
                            "apiKey", "myApiKey",
                            "apiKeyLocator", "=request.headers.authorization"))))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode())
        .isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());
  }

  @Test
  void triggerWebhook_VerificationExpression_ReturnsChallenge() {
    final var verificationExpression =
        "=if request.body.challenge != null then {\"body\": {\"challenge\":request.body.challenge}} else null";
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
  }

  @Test
  void triggerWebhook_VerificationExpressionWithModifiedBody_ReturnsChallenge() {
    final var verificationExpression =
        "=if request.body.challenge != null then {\"body\": {\"challenge123\":request.body.challenge + \"QQQ\"}} else null";
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge123", "12345QQQ");
  }

  @Test
  void triggerWebhook_VerificationExpressionWithFoldedBody_ReturnsChallenge() {
    final var verificationExpression =
        "=if request.body.event_type = \"verification\" then {\"body\": {\"challenge\":request.body.event.challenge}} else null";
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn(
            "{\"event_type\": \"verification\", \"event\": {\"challenge\": \"12345\"}}"
                .getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
  }

  @Test
  void triggerWebhook_VerificationExpressionWithStatusCode_ReturnsChallenge() {
    final var verificationExpression =
        "=if request.body.challenge != null then {\"body\": {\"challenge\":request.body.challenge}, \"statusCode\": 409} else null";
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.statusCode()).isEqualTo(409);
    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
  }

  @Test
  void triggerWebhook_VerificationExpressionWithCustomHeaders_ReturnsChallenge() {
    final var verificationExpression =
        "=if request.body.challenge != null then {\"body\": {\"challenge\":request.body.challenge}, \"headers\":{\"Content-Type\":\"application/camunda-bin\"}} else null";
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context",
                        "webhookContext",
                        "method",
                        "any",
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
    assertThat(result.headers()).containsEntry("Content-Type", "application/camunda-bin");
    assertThat(result.headers()).hasSize(1);
  }
}
