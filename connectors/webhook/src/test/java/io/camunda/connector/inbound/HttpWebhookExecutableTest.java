/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.test.inbound.InboundConnectorPropertiesBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.assertj.core.api.Assertions;
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
  void triggerWebhook_JsonBody_HappyCase() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    Assertions.assertThat((Map) result.body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_FormDataBody_HappyCase() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("key1=value1&key2=value2".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    Assertions.assertThat((Map) result.body()).containsEntry("key1", "value1");
    Assertions.assertThat((Map) result.body()).containsEntry("key2", "value2");
  }

  @Test
  void triggerWebhook_UnknownJsonLikeBody_HappyCase() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.any.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.GEO_JSON.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);
    var result = testObject.triggerWebhook(payload);

    Assertions.assertThat((Map) result.body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_BinaryData_RaisesException() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
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
  void triggerWebhook_HttpMethodNotAllowed_RaisesException() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "get")
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
    WebhookProcessingPayload payload = Mockito.mock(WebhookProcessingPayload.class);
    Mockito.when(payload.method()).thenReturn(HttpMethods.post.name());
    Mockito.when(payload.headers())
        .thenReturn(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
    Mockito.when(payload.rawBody())
        .thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    testObject.activate(ctx);

    assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_HmacSignatureMatches_HappyCase() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .property("inbound.shouldValidateHmac", enabled.name())
            .property("inbound.hmacSecret", "mySecretKey")
            .property("inbound.hmacHeader", "X-HMAC-Sig")
            .property("inbound.hmacAlgorithm", HMACAlgoCustomerChoice.sha_256.name())
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
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

    Assertions.assertThat((Map) result.body()).containsEntry("key", "value");
  }

  @Test
  void triggerWebhook_HmacSignatureDidntMatch_RaisesException() throws Exception {
    InboundConnectorProperties properties =
        InboundConnectorPropertiesBuilder.create()
            .property("inbound.context", "webhookContext")
            .property("inbound.method", "any")
            .property("inbound.shouldValidateHmac", enabled.name())
            .property("inbound.hmacSecret", "mySecretKey")
            .property("inbound.hmacHeader", "X-HMAC-Sig")
            .property("inbound.hmacAlgorithm", HMACAlgoCustomerChoice.sha_256.name())
            .build();
    InboundConnectorContext ctx = Mockito.mock(InboundConnectorContext.class);
    Mockito.when(ctx.getProperties()).thenReturn(properties);
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

    assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }
}
