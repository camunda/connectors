/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;
import static io.camunda.connector.inbound.utils.HttpWebhookUtil.FORM_DATA_CONTENT_TYPE;
import static io.camunda.connector.inbound.utils.HttpWebhookUtil.HEADER_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.*;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
  void triggerWebhook_ResponseIsArrayOfPrimitive_HappyCase() {
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn(("[ \"test1\", \"test2\" ]").getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNotNull(result.response());
    assertThat((List<String>) result.request().body()).contains("test1", "test2");

    var request = new MappedHttpRequest(Map.of("key", "value"), null, null);
    var context = new WebhookResultContext(request, null, null);
    var response = result.response().apply(context);
    assertEquals("value", response.body());
  }

  @Test
  void triggerWebhook_ResponseIsArrayOfObject_HappyCase() {
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn(
            ("[{\"key\": \"value\"}, {\"key\": \"value\"}]").getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNotNull(result.response());
    assertThat((List<Map>) result.request().body())
        .contains(Map.of("key", "value"), Map.of("key", "value"));

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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, FORM_DATA_CONTENT_TYPE));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/geo+json"));
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
        .thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/octet-stream"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(405);
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
                HEADER_CONTENT_TYPE,
                "application/json",
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
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "123132313214533154234132534123452")); // not correct HMAC
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
  }

  @Test
  void triggerWebhook_HmacTimestampWithinTolerance_HappyCase()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = Instant.now().getEpochSecond();
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(now);
    String signature = hmacHex("mySecretKey", timestamp, body);

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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                signature,
                "X-HMAC-Timestamp",
                timestamp));
    Mockito.when(payload.rawBody()).thenReturn(body);

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertThat((Map) result.request().body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_HmacTimestampStale_RaisesException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // an hour old; well outside the default 300-second tolerance
    long staleTimestamp = Instant.now().getEpochSecond() - 3600;
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(staleTimestamp);
    String signature = hmacHex("mySecretKey", timestamp, body);

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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                signature,
                "X-HMAC-Timestamp",
                timestamp));
    Mockito.when(payload.rawBody()).thenReturn(body);

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
  }

  @Test
  void activate_HmacTimestampScopeWithoutHeaderConfigured_RaisesException() {
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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        // hmacTimestampHeader intentionally omitted
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
  }

  @Test
  void activate_HmacToleranceZeroOrNegative_RaisesException() {
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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "hmacTolerance", "PT0S",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
  }

  @Test
  void activate_HmacToleranceMalformed_RaisesException() {
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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "hmacTolerance", "300",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
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
                        "context", "webhookContext",
                        "method", "any",
                        "shouldValidateHmac", disabled.name(),
                        "hmacTolerance", "PT0S",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThat(catchException(() -> testObject.activate(ctx))).isNull();
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
                        "context", "webhookContext",
                        "method", "any",
                        "shouldValidateHmac", enabled.name(),
                        "hmacSecret", "mySecretKey",
                        "hmacHeader", "X-HMAC-Sig",
                        "hmacAlgorithm", HMACAlgoCustomerChoice.sha_256.name(),
                        "hmacScopes", "=[\"body\"]",
                        "hmacTolerance", "PT0S",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThat(catchException(() -> testObject.activate(ctx))).isNull();
  }

  @Test
  void activate_UnsupportedHmacScopeCombination_RaisesException() {
    // [timestamp, url] strips down to [url] alone, which the encoding-strategy factory has no
    // strategy for — must fail at activation, not on every incoming request.
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
                        "hmacScopes", "=[\"timestamp\",\"url\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
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
                        "context", "webhookContext",
                        "method", "any",
                        "shouldValidateHmac", disabled.name(),
                        "hmacScopes", "=[\"timestamp\",\"url\"]",
                        "auth", Map.of("type", "NONE"))))
            .build();

    assertThat(catchException(() -> testObject.activate(ctx))).isNull();
  }

  @Test
  void triggerWebhook_HmacTimestampMissing_RaisesException() {
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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
  }

  @Test
  void triggerWebhook_HmacCustomToleranceExceeded_RaisesException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // 90 seconds old; within the default 300s tolerance, but outside a custom 60s tolerance bound
    // via a property, expressed as an ISO-8601 duration.
    long timestampValue = Instant.now().getEpochSecond() - 90;
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(timestampValue);
    String signature = hmacHex("mySecretKey", timestamp, body);

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
                        "hmacScopes", "=[\"body\",\"timestamp\"]",
                        "hmacTimestampHeader", "X-HMAC-Timestamp",
                        "hmacTolerance", "PT60S",
                        "auth", Map.of("type", "NONE"))))
            .build();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                signature,
                "X-HMAC-Timestamp",
                timestamp));
    Mockito.when(payload.rawBody()).thenReturn(body);

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
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
    return Hex.encodeHexString(mac.doFinal(bytesToSign));
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
                HEADER_CONTENT_TYPE,
                "application/json",
                "Authorization",
                "notMyApiKey")); // not correct API key
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    var exception = catchException(() -> testObject.triggerWebhook(payload));
    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
    assertThat(result.headers()).containsEntry("Content-Type", "application/camunda-bin");
    assertThat(result.headers()).hasSize(1);
  }

  @Test
  void triggerWebhook_XmlBody_HappyCase() {
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/xml"));
    Mockito.when(payload.rawBody())
        .thenReturn(
            "<request><id>123</id><status>active</status></request>"
                .getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNull(result.response());
    // XML is stored as string
    assertThat(result.request().body()).isInstanceOf(String.class);
    assertThat((String) result.request().body()).contains("<id>123</id>");
  }

  @Test
  void triggerWebhook_XmlBodyWithTextXmlContentType_HappyCase() {
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
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "text/xml"));
    Mockito.when(payload.rawBody())
        .thenReturn("<?xml version=\"1.0\"?><data>test</data>".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    assertNull(result.response());
    assertThat(result.request().body()).isInstanceOf(String.class);
  }
}
