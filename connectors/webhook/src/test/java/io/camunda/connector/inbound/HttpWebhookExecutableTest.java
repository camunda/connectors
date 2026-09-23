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
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.*;
import io.camunda.connector.inbound.model.DynamicWebhookProperties;
import io.camunda.connector.inbound.model.DynamicWebhookProperties.DynamicWebhookPropertiesWrapper;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

  private WebhookResult triggerSimpleWebhook() {
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
                        Map.of("type", "NONE"))))
            .build();
    testObject.activate(ctx);
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
    return testObject.triggerWebhook(payload);
  }

  @Test
  void response_resolvesResponseExpressionFromActivatedElement() {
    // the response function (applied by the runtime after correlation) resolves from the element
    // that actually matched, via its element-scoped properties
    var result = triggerSimpleWebhook();
    var wrapper =
        new DynamicWebhookPropertiesWrapper(
            new DynamicWebhookProperties(
                c -> WebhookHttpResponse.ok("response-from-element"), null));
    var activatedElement = Mockito.mock(ProcessElement.class);
    Mockito.when(activatedElement.bindProperties(DynamicWebhookPropertiesWrapper.class))
        .thenReturn(wrapper);
    var success =
        new CorrelationResult.Success.ProcessInstanceCreated(activatedElement, 1L, "<default>");
    var resultContext =
        new WebhookResultContext(
            new MappedHttpRequest(Map.of(), Map.of(), Map.of()), Map.of(), success);

    var response = result.response().apply(resultContext);

    assertNotNull(response);
    assertEquals("response-from-element", response.body());
  }

  @Test
  void response_returnsNullWhenNoCorrelation() {
    var result = triggerSimpleWebhook();
    var resultContext =
        new WebhookResultContext(
            new MappedHttpRequest(Map.of(), Map.of(), Map.of()), Map.of(), null);
    assertNull(result.response().apply(resultContext));
  }

  @Test
  void activate_deprecatedResponseBodyExpression_failsDeploymentWithMigrationHint() {
    InboundConnectorContext ctx =
        contextWithElements(
            elementWithRawProperties(
                Map.of("inbound.responseBodyExpression", "={\"foo\": \"bar\"}")));

    var exception = catchException(() -> testObject.activate(ctx));

    assertThat(exception)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("responseBodyExpression")
        .hasMessageContaining("responseExpression");
  }

  @Test
  void activate_deprecatedResponseBodyExpressionOnNonFirstDeduplicatedElement_failsDeployment() {
    // The representative (first) element only uses responseExpression; a second element in the same
    // deduplication group carries the legacy property. Deployment must still fail, even though
    // context.getProperties() only reflects the first element.
    InboundConnectorContext ctx =
        contextWithElements(
            elementWithRawProperties(Map.of("inbound.responseExpression", "={body: request.body}")),
            elementWithRawProperties(
                Map.of("inbound.responseBodyExpression", "={\"foo\": \"bar\"}")));

    var exception = catchException(() -> testObject.activate(ctx));

    assertThat(exception).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_blankResponseBodyExpression_doesNotFailDeployment() {
    InboundConnectorContext ctx =
        contextWithElements(
            elementWithRawProperties(Map.of("inbound.responseBodyExpression", "   ")));

    assertThat(catchException(() -> testObject.activate(ctx))).isNull();
  }

  @Test
  void activate_responseExpressionOnly_doesNotFailDeployment() {
    InboundConnectorContext ctx =
        contextWithElements(
            elementWithRawProperties(
                Map.of("inbound.responseExpression", "={body: request.body}")));

    assertThat(catchException(() -> testObject.activate(ctx))).isNull();
  }

  private InboundConnectorContext contextWithFailingJwtActivation() {
    return InboundConnectorContextBuilder.create()
        .properties(
            Map.of(
                "inbound",
                Map.of(
                    "context",
                    "webhookContext",
                    "method",
                    "any",
                    "auth",
                    Map.of(
                        "type",
                        "JWT",
                        "jwt",
                        Map.of("jwkUrl", "https://example.com/.well-known/jwks.json")))))
        .build();
  }

  @Test
  void triggerWebhook_afterFailedActivation_rejectsCleanlyInsteadOfNpe() {
    // A v15-shaped JWT webhook (no issuer/audience) fails to activate, per this PR's own change.
    // Regression for the QA finding: a request against the never-activated executable must not
    // NPE on the null `props` field left behind by the aborted activate().
    InboundConnectorContext ctx = contextWithFailingJwtActivation();
    assertThat(catchException(() -> testObject.activate(ctx))).isNotNull();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());

    var exception = catchException(() -> testObject.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(503);
  }

  @Test
  void verify_afterFailedActivation_rejectsCleanlyInsteadOfNpe() {
    InboundConnectorContext ctx = contextWithFailingJwtActivation();
    assertThat(catchException(() -> testObject.activate(ctx))).isNotNull();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());

    var exception = catchException(() -> testObject.verify(payload));

    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(503);
  }

  @Test
  void triggerWebhook_afterPartialActivationPastProps_rejectsCleanlyInsteadOfNpe() {
    // activate() sets props, then authChecker, then hmacVerifier, in that order. A JWT webhook
    // with valid issuer/audience but a scheme-less jwkUrl passes the JWTProperties constructor
    // (so `props` is set) but fails at WebhookAuthorizationHandler.getHandlerForAuth
    // (jwkUrl.toURL()
    // requires an absolute URI), leaving `authChecker` null while `props` is non-null - a
    // narrower partial-activation state than the one covered above.
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
                        Map.of(
                            "type",
                            "JWT",
                            "jwt",
                            Map.of(
                                "jwkUrl", "not-a-valid-url",
                                "issuer", "https://idp.local",
                                "audience", "api1")))))
            .build();
    assertThat(catchException(() -> testObject.activate(ctx))).isNotNull();
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());

    var exception = catchException(() -> testObject.triggerWebhook(payload));

    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(503);
  }

  private static ProcessElement elementWithRawProperties(Map<String, String> rawProperties) {
    var element = Mockito.mock(ProcessElement.class);
    Mockito.when(element.properties()).thenReturn(rawProperties);
    return element;
  }

  private static InboundConnectorContext contextWithElements(ProcessElement... elements) {
    var definition =
        new InboundConnectorDefinition(
            "io.camunda:webhook:1", "<default>", "dedup-id", List.of(elements), "default");
    return InboundConnectorContextBuilder.create()
        .properties(
            Map.of(
                "inbound",
                Map.of(
                    "context", "webhookContext",
                    "method", "any",
                    "auth", Map.of("type", "NONE"))))
        .definition(definition)
        .build();
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

    assertThat((Map) result.request().body()).containsEntry("key", "value");
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
    assertThat((List<String>) result.request().body()).contains("test1", "test2");
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
    assertThat((List<Map>) result.request().body())
        .contains(Map.of("key", "value"), Map.of("key", "value"));
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
  void activate_HmacTimestampHeaderMatchesSignatureHeader_RaisesException() {
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
                        "hmacTimestampHeader", "x-hmac-sig",
                        "auth", Map.of("type", "NONE"))))
            .build();

    var exception = assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
    assertThat(exception).hasMessageContaining("must be different");
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
  void activate_HmacToleranceWithFractionalSeconds_RaisesException() {
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
                        "hmacTolerance", "PT0.5S",
                        "auth", Map.of("type", "NONE"))))
            .build();

    ConnectorInputException exception =
        assertThrows(ConnectorInputException.class, () -> testObject.activate(ctx));
    assertThat(exception).hasMessageContaining("whole-second duration");
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
  void activate_EmptyHmacScopes_RaisesException() {
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
                        "hmacScopes", "=[]",
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

  /**
   * Replaces the activated {@link HttpWebhookExecutable}'s compiled verification expression with a
   * bare Mockito mock, so a test can assert on invocation counts against it directly — proving the
   * FEEL evaluator was (or wasn't) reached, rather than inferring it from the exception thrown.
   */
  @SuppressWarnings("unchecked")
  private static Function<Map<String, Object>, WebhookHttpResponse>
      replaceVerificationExpressionWithMock(HttpWebhookExecutable executable) throws Exception {
    var mockExpression =
        (Function<Map<String, Object>, WebhookHttpResponse>) Mockito.mock(Function.class);
    var propsField = HttpWebhookExecutable.class.getDeclaredField("props");
    propsField.setAccessible(true);
    var currentProps = (WebhookConnectorProperties) propsField.get(executable);
    var replacedProps =
        new WebhookConnectorProperties(
            currentProps.method(),
            currentProps.context(),
            currentProps.shouldValidateHmac(),
            currentProps.hmacSecret(),
            currentProps.hmacHeader(),
            currentProps.hmacAlgorithm(),
            currentProps.hmacScopes(),
            currentProps.auth(),
            mockExpression);
    propsField.set(executable, replacedProps);
    return mockExpression;
  }

  @Test
  void verify_HmacSignatureDidntMatch_RaisesExceptionBeforeEvaluatingExpression() throws Exception {
    // Regression test for https://github.com/camunda/security-testing-findings/issues/265:
    // verify() must authenticate before it ever applies the verification expression, so an
    // unauthenticated caller can't reach the FEEL engine (or its response) via the verify path.
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
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecretKey",
                        "hmacHeader",
                        "X-HMAC-Sig",
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "not-the-correct-signature"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var mockExpression = replaceVerificationExpressionWithMock(testObject);

    var exception = catchException(() -> testObject.verify(payload));

    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
    // Explicit proof, not just an inference from the exception: the FEEL evaluator backing the
    // verification expression was never invoked.
    Mockito.verifyNoInteractions(mockExpression);
  }

  @Test
  void verify_MissingApiKey_RaisesExceptionBeforeEvaluatingExpression() throws Exception {
    // Same regression as above, for the authorization check rather than HMAC.
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
                        "shouldValidateHmac",
                        disabled.name(),
                        "auth",
                        Map.of(
                            "type", "APIKEY",
                            "apiKey", "myApiKey",
                            "apiKeyLocator", "=request.headers.Authorization"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers()).thenReturn(Map.of(HEADER_CONTENT_TYPE, "application/json"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var mockExpression = replaceVerificationExpressionWithMock(testObject);

    var exception = catchException(() -> testObject.verify(payload));

    assertThat(exception).isInstanceOf(WebhookConnectorException.class);
    assertThat(((WebhookConnectorException) exception).getStatusCode()).isEqualTo(401);
    // Explicit proof, not just an inference from the exception: the FEEL evaluator backing the
    // verification expression was never invoked.
    Mockito.verifyNoInteractions(mockExpression);
  }

  @Test
  void verify_AuthenticatedCaller_StillEvaluatesExpression() {
    // Counterpart to the two tests above: authentication must not swallow the legitimate
    // verification-challenge use case once the caller is authenticated.
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
                        "shouldValidateHmac",
                        enabled.name(),
                        "hmacSecret",
                        "mySecretKey",
                        "hmacHeader",
                        "X-HMAC-Sig",
                        "hmacAlgorithm",
                        HMACAlgoCustomerChoice.sha_256.name(),
                        "auth",
                        Map.of("type", "NONE"),
                        "verificationExpression",
                        verificationExpression)))
            .build();

    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                // HMAC-SHA256("{\"challenge\": \"12345\"}", "mySecretKey")
                "cfb128a772a03ce78ee13d88bfcddde870662d15043650c15fb8f24363806ac4"));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"challenge\": \"12345\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.verify(payload);

    assertThat(result.body()).isInstanceOf(Map.class);
    assertThat((Map) result.body()).containsEntry("challenge", "12345");
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
    assertThat(result.request().body()).isInstanceOf(String.class);
  }
}
