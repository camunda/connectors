/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.message.SnsMessage;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises the real (unmocked) AWS SDK v1 {@link SnsMessageManager} that {@link
 * SnsWebhookExecutable} relies on to verify inbound SNS webhook signatures. {@link
 * SnsWebhookExecutableTest} mocks {@link SnsMessageManager}, so it never runs real
 * signature/cert-URL verification; these tests do, covering: a spoofed {@code SigningCertURL}, a
 * validly-signed message, a tampered message, and a missing signature.
 *
 * <p>The webhook entry-point tests also cover topic authorization using the signature-verified
 * message rather than unsigned HTTP headers.
 *
 * <p>The certificate-download step is skipped by seeding {@code SnsMessageManager}'s internal
 * certificate cache via reflection with a self-generated RSA key, so the crypto check runs fully
 * offline. The cache's own {@code add} call is also invoked via reflection rather than a direct
 * import, to avoid a compile-time dependency on {@code aws-java-sdk-core} (where the cache type
 * lives) that {@code dependency:analyze-only} would flag as declared-but-unused.
 *
 * <p>Not covered: certificate download/parsing, hostname verification, and expiry checks — those
 * are bypassed by seeding the cache directly instead of exercising them.
 */
class SnsWebhookSignatureVerificationTest {

  private static final String TOPIC_ARN = "arn:aws:sns:eu-central-1:111222333444:SNSWebhook";
  private static final String OTHER_TOPIC_ARN = "arn:aws:sns:eu-central-1:555666777888:OtherTopic";
  private static final String SIGNING_CERT_URL =
      "https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-56e67fcb41f6fec09b0196692625d385.pem";

  @Test
  void triggerWebhook_signedUnlistedTopicWithAllowListedHeader_isRejected() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    fields.put("TopicArn", OTHER_TOPIC_ARN);
    SnsWebhookExecutable executable = createExecutable(keyPair, "specific", TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), TOPIC_ARN);

    assertThatThrownBy(() -> executable.triggerWebhook(payload))
        .hasMessageContaining("Request didn't match allow list")
        .hasMessageContaining(OTHER_TOPIC_ARN);
  }

  @Test
  void triggerWebhook_signedUnlistedSubscriptionDoesNotInvokeConfirmationUrl() throws Exception {
    AtomicInteger callbackCount = new AtomicInteger();
    HttpServer callbackServer = startCallbackServer(callbackCount);
    try {
      KeyPair keyPair = generateRsaKeyPair();
      String subscribeUrl =
          "http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/confirm";
      Map<String, String> fields = subscriptionConfirmationFields(OTHER_TOPIC_ARN, subscribeUrl);
      SnsWebhookExecutable executable = createExecutable(keyPair, "specific", TOPIC_ARN);
      WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), TOPIC_ARN);

      assertThatThrownBy(() -> executable.triggerWebhook(payload))
          .hasMessageContaining("Request didn't match allow list")
          .hasMessageContaining(OTHER_TOPIC_ARN);
      assertThat(callbackCount.get()).isZero();
    } finally {
      callbackServer.stop(0);
    }
  }

  @Test
  void triggerWebhook_signedAllowListedSubscriptionInvokesConfirmationUrl() throws Exception {
    AtomicInteger callbackCount = new AtomicInteger();
    HttpServer callbackServer = startCallbackServer(callbackCount);
    try {
      KeyPair keyPair = generateRsaKeyPair();
      String subscribeUrl =
          "http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/confirm";
      Map<String, String> fields = subscriptionConfirmationFields(TOPIC_ARN, subscribeUrl);
      SnsWebhookExecutable executable = createExecutable(keyPair, "specific", TOPIC_ARN);
      WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), TOPIC_ARN);

      assertThat(executable.triggerWebhook(payload).connectorData())
          .containsEntry("snsEventType", "Subscription");
      assertThat(callbackCount.get()).isEqualTo(1);
    } finally {
      callbackServer.stop(0);
    }
  }

  @Test
  void triggerWebhook_matchingUnlistedTopic_isRejected() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    fields.put("TopicArn", OTHER_TOPIC_ARN);
    SnsWebhookExecutable executable = createExecutable(keyPair, "specific", TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), OTHER_TOPIC_ARN);

    assertThatThrownBy(() -> executable.triggerWebhook(payload))
        .hasMessageContaining("Request didn't match allow list")
        .hasMessageContaining(OTHER_TOPIC_ARN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"any", "specific"})
  void triggerWebhook_differentVerifiedAndHeaderTopics_isAccepted(String allowedFor)
      throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    fields.put("TopicArn", OTHER_TOPIC_ARN);
    SnsWebhookExecutable executable =
        createExecutable(keyPair, allowedFor, TOPIC_ARN + "," + OTHER_TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), TOPIC_ARN);

    assertThat(executable.triggerWebhook(payload).connectorData())
        .containsEntry("snsEventType", "Notification");
  }

  @ParameterizedTest
  @ValueSource(strings = {"any", "specific"})
  void triggerWebhook_matchingAllowListedTopic_isAccepted(String allowedFor) throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    SnsWebhookExecutable executable = createExecutable(keyPair, allowedFor, TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), TOPIC_ARN);

    assertThat(executable.triggerWebhook(payload).connectorData())
        .containsEntry("snsEventType", "Notification");
  }

  @Test
  void triggerWebhook_anyTopicAllowsUnlistedTopic() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    fields.put("TopicArn", OTHER_TOPIC_ARN);
    SnsWebhookExecutable executable = createExecutable(keyPair, "any", TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, sign(fields, keyPair), OTHER_TOPIC_ARN);

    assertThat(executable.triggerWebhook(payload).connectorData())
        .containsEntry("snsEventType", "Notification");
  }

  @Test
  void triggerWebhook_tamperedTopicArn_isRejected() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    fields.put("TopicArn", OTHER_TOPIC_ARN);
    String signature = sign(fields, keyPair);
    fields.put("TopicArn", TOPIC_ARN);
    SnsWebhookExecutable executable = createExecutable(keyPair, "specific", TOPIC_ARN);
    WebhookProcessingPayload payload = payloadWith(fields, signature, TOPIC_ARN);

    assertThatThrownBy(() -> executable.triggerWebhook(payload))
        .hasMessageContaining("Signature in SNS message was invalid");
  }

  @Test
  void spoofedSigningCertUrl_isRejectedBeforeAnyNetworkCall() {
    // Real, unmocked supplier and manager - exercises SigningCertUrlVerifier for real.
    SnsMessageManager manager = new SnsClientSupplier().messageManager("eu-central-1");
    String payloadWithSpoofedCertUrl =
        """
        {
          "Type": "Notification",
          "MessageId": "2e062e6b-a527-5e68-b69b-72a8e42add60",
          "TopicArn": "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
          "Message": "Hello, world",
          "Timestamp": "2023-04-26T15:10:05.479Z",
          "SignatureVersion": "1",
          "Signature": "does-not-matter-cert-url-check-runs-first",
          "SigningCertURL": "https://evil.example.com/fake-cert.pem"
        }
        """;

    assertThatThrownBy(
            () ->
                manager.parseMessage(
                    new ByteArrayInputStream(
                        payloadWithSpoofedCertUrl.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(RuntimeException.class)
        // Matches either SigningCertUrlVerifier rejection message (wrong host or non-HTTPS).
        .hasMessageContaining("SigningCert");
  }

  @Test
  void validSignature_isAccepted() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");
    String signature = sign(fields, keyPair);

    SnsMessageManager manager = new SnsClientSupplier().messageManager("eu-central-1");
    seedCertificateCache(manager, keyPair.getPublic());

    SnsMessage message =
        manager.parseMessage(
            new ByteArrayInputStream(toJson(fields, signature).getBytes(StandardCharsets.UTF_8)));

    assertThat(message).isInstanceOf(SnsNotification.class);
    assertThat(((SnsNotification) message).getMessage()).isEqualTo("Hello, world");
  }

  @Test
  void tamperedMessage_isRejected() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> originalFields = notificationFields("Hello, world");
    String signatureOverOriginal = sign(originalFields, keyPair);

    Map<String, String> tamperedFields = new LinkedHashMap<>(originalFields);
    tamperedFields.put("Message", "Tampered message!");

    SnsMessageManager manager = new SnsClientSupplier().messageManager("eu-central-1");
    seedCertificateCache(manager, keyPair.getPublic());

    assertThatThrownBy(
            () ->
                manager.parseMessage(
                    new ByteArrayInputStream(
                        toJson(tamperedFields, signatureOverOriginal)
                            .getBytes(StandardCharsets.UTF_8))))
        .hasMessageContaining("Signature in SNS message was invalid");
  }

  @Test
  void missingSignature_isRejected() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Map<String, String> fields = notificationFields("Hello, world");

    SnsMessageManager manager = new SnsClientSupplier().messageManager("eu-central-1");
    seedCertificateCache(manager, keyPair.getPublic());

    assertThatThrownBy(
            () ->
                manager.parseMessage(
                    new ByteArrayInputStream(
                        toJson(fields, null).getBytes(StandardCharsets.UTF_8))))
        .hasMessageContaining("Message cannot have null values");
  }

  private static SnsWebhookExecutable createExecutable(
      KeyPair keyPair, String allowedFor, String topicsAllowList) throws Exception {
    SnsMessageManager manager = new SnsClientSupplier().messageManager("eu-central-1");
    seedCertificateCache(manager, keyPair.getPublic());
    SnsClientSupplier supplier = mock(SnsClientSupplier.class);
    when(supplier.messageManager("eu-central-1")).thenReturn(manager);
    var mapper = ObjectMapperSupplier.getMapperInstance();
    SnsWebhookExecutable executable = new SnsWebhookExecutable(mapper, supplier);
    executable.activate(
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "snstest",
                        "securitySubscriptionAllowedFor", allowedFor,
                        "topicsAllowList", topicsAllowList)))
            .objectMapper(mapper)
            .validation(new DefaultValidationProvider())
            .build());
    return executable;
  }

  private static WebhookProcessingPayload payloadWith(
      Map<String, String> fields, String signature, String topicArnHeader) {
    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.headers())
        .thenReturn(Map.of(SnsWebhookExecutable.TOPIC_ARN_HEADER, topicArnHeader));
    when(payload.rawBody()).thenReturn(toJson(fields, signature).getBytes(StandardCharsets.UTF_8));
    when(payload.params()).thenReturn(Map.of());
    return payload;
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  private static Map<String, String> notificationFields(String message) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("Type", "Notification");
    fields.put("MessageId", "2e062e6b-a527-5e68-b69b-72a8e42add60");
    fields.put("TopicArn", TOPIC_ARN);
    fields.put("Subject", "test subject");
    fields.put("Message", message);
    fields.put("Timestamp", "2023-04-26T15:10:05.479Z");
    // Not part of the signed field set (see canonicalStringToSign below) but required by
    // SnsNotification's constructor, which builds a java.net.URL from it.
    fields.put(
        "UnsubscribeURL",
        "https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:111222333444:SNSWebhook:abc");
    return fields;
  }

  private static Map<String, String> subscriptionConfirmationFields(
      String topicArn, String subscribeUrl) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("Type", "SubscriptionConfirmation");
    fields.put("MessageId", "b9b4574f-b4ab-4c03-ac14-a3145896747f");
    fields.put("Token", "test-token");
    fields.put("TopicArn", topicArn);
    fields.put("Message", "Confirm this subscription");
    fields.put("SubscribeURL", subscribeUrl);
    fields.put("Timestamp", "2023-04-26T15:04:47.883Z");
    return fields;
  }

  /**
   * AWS's canonical "string to sign": present message-type fields, sorted by key, as
   * "Key\nValue\n".
   */
  private static String canonicalStringToSign(Map<String, String> fields) {
    String[] keysInSortedOrder =
        "SubscriptionConfirmation".equals(fields.get("Type"))
            ? new String[] {
              "Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type"
            }
            : new String[] {"Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type"};
    StringBuilder builder = new StringBuilder();
    for (String key : keysInSortedOrder) {
      String value = fields.get(key);
      if (value != null) {
        builder.append(key).append('\n').append(value).append('\n');
      }
    }
    return builder.toString();
  }

  private static String sign(Map<String, String> fields, KeyPair keyPair) throws Exception {
    Signature signer = Signature.getInstance("SHA1withRSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(canonicalStringToSign(fields).getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signer.sign());
  }

  private static HttpServer startCallbackServer(AtomicInteger callbackCount) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/confirm",
        exchange -> {
          callbackCount.incrementAndGet();
          byte[] response =
              """
              <ConfirmSubscriptionResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ConfirmSubscriptionResult>
                  <SubscriptionArn>%s:11111111-2222-3333-4444-555555555555</SubscriptionArn>
                </ConfirmSubscriptionResult>
                <ResponseMetadata><RequestId>req-1</RequestId></ResponseMetadata>
              </ConfirmSubscriptionResponse>
              """
                  .formatted(TOPIC_ARN)
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/xml");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static String toJson(Map<String, String> fields, String signature) {
    StringBuilder json = new StringBuilder("{\n");
    fields.forEach(
        (key, value) ->
            json.append("  \"")
                .append(key)
                .append("\": \"")
                .append(value.replace("\n", "\\n"))
                .append("\",\n"));
    json.append("  \"SignatureVersion\": \"1\",\n");
    if (signature != null) {
      json.append("  \"Signature\": \"").append(signature).append("\",\n");
    }
    json.append("  \"SigningCertURL\": \"").append(SIGNING_CERT_URL).append("\"\n}");
    return json.toString();
  }

  /** Seeds the certificate cache via reflection so verification runs offline; see class javadoc. */
  private static void seedCertificateCache(SnsMessageManager manager, PublicKey publicKey)
      throws Exception {
    Field signatureVerifierField = SnsMessageManager.class.getDeclaredField("signatureVerifier");
    signatureVerifierField.setAccessible(true);
    Object signatureVerifier = signatureVerifierField.get(manager);

    Field certificateCacheField = signatureVerifier.getClass().getDeclaredField("certificateCache");
    certificateCacheField.setAccessible(true);
    Object certificateCache = certificateCacheField.get(signatureVerifier);

    Method addMethod = certificateCache.getClass().getMethod("add", String.class, Object.class);
    addMethod.invoke(certificateCache, SIGNING_CERT_URL, publicKey);
  }
}
