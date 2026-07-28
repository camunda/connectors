/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.sns.message.SnsMessage;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real (unmocked) AWS SDK v1 {@link SnsMessageManager} that {@link
 * SnsWebhookExecutable} relies on for validating inbound SNS webhook payloads.
 *
 * <p>{@link SnsWebhookExecutableTest} covers the connector's business logic (allow-listing, routing
 * by message type) but mocks {@link SnsMessageManager} itself, so none of its tests — including the
 * happy-path ones — invoke real signature/cert-URL verification. The tests below close that gap:
 * they go through the real, unmocked {@link SnsClientSupplier} -&gt; {@link SnsMessageManager}
 * end-to-end, deterministically and fully offline, covering:
 *
 * <ul>
 *   <li>a spoofed {@code SigningCertURL} is rejected before any network call ({@link
 *       #spoofedSigningCertUrl_isRejectedBeforeAnyNetworkCall()});
 *   <li>a genuinely RSA-signed message is accepted by the real cryptographic signature check
 *       ({@link #validSignature_isAccepted()}) — a positive control proving the harness below is
 *       sound;
 *   <li>a message whose body was tampered with after signing is rejected by that same check ({@link
 *       #tamperedMessage_isRejected()});
 *   <li>a message with no {@code Signature} field at all is rejected ({@link
 *       #missingSignature_isRejected()}).
 * </ul>
 *
 * <p>The cryptographic-signature tests work by pre-seeding {@code SnsMessageManager}'s internal
 * certificate cache (a {@code com.amazonaws.internal.FIFOCache<PublicKey>}) via reflection with a
 * self-generated JDK-native RSA public key, keyed by the message's {@code SigningCertURL}. That
 * makes the manager's private {@code fetchPublicKey} cache-hit and skip the network certificate
 * download, while the real {@code SignatureChecker} crypto verification still runs against the
 * (self-signed) key. This needs no new test dependency (pure JDK, no BouncyCastle), no {@code
 * keytool}, and no reflection into any package-private constructor — only {@code setAccessible} on
 * two private fields (in the package-private {@code SignatureVerifier} class): {@code
 * SnsMessageManager.signatureVerifier} and its {@code SignatureVerifier.certificateCache}. The
 * cache's own {@code add} method is public; it is still invoked reflectively here (rather than via
 * a direct import/cast) purely to avoid this test module taking on a compile-time reference to the
 * {@code aws-java-sdk-core} artifact that {@code FIFOCache} lives in — {@code
 * dependency:analyze-only} would otherwise flag it as a non-test-scoped test-only dependency.
 *
 * <p><b>What this class does NOT cover</b>: the actual PEM download/parse, X.509 hostname
 * verification, and certificate-expiry checks inside {@code SignatureVerifier#downloadCert} /
 * {@code #validateCertificate} are bypassed by seeding the cache directly — those code paths still
 * have no test coverage here. What IS covered end-to-end at the connector level — including the
 * specific regression this gap could otherwise hide, e.g. {@code parseMessage} throwing and that
 * exception being silently swallowed instead of propagated — is in {@link
 * SnsWebhookExecutableTest#triggerWebhook_SignatureVerificationFails_PropagatesException()}, which
 * asserts the real production code path never falls back to an unverified payload when the (mocked)
 * manager rejects a message.
 */
class SnsWebhookSignatureVerificationTest {

  private static final String SIGNING_CERT_URL =
      "https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-56e67fcb41f6fec09b0196692625d385.pem";

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
        // Common substring of both possible SigningCertUrlVerifier messages ("SigningCertUrl
        // does not match expected endpoint..." and "SigningCertURL was not using HTTPS:"), so the
        // assertion doesn't depend on exactly which of the two checks rejects this fixture.
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

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  private static Map<String, String> notificationFields(String message) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("Type", "Notification");
    fields.put("MessageId", "2e062e6b-a527-5e68-b69b-72a8e42add60");
    fields.put("TopicArn", "arn:aws:sns:eu-central-1:111222333444:SNSWebhook");
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

  /**
   * Reproduces the AWS-documented canonical "string to sign" for a {@code Notification}: the
   * interesting fields (as SNS itself defines them), sorted alphabetically by key, each rendered as
   * {@code "Key\nValue\n"}. Only fields actually present are included, matching {@code
   * SignatureChecker}'s behavior.
   */
  private static String canonicalStringToSign(Map<String, String> fields) {
    String[] keysInSortedOrder = {
      "Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type"
    };
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

  /**
   * Pre-seeds {@code SnsMessageManager}'s certificate cache so {@code fetchPublicKey} cache-hits on
   * {@link #SIGNING_CERT_URL} instead of downloading a certificate over the network, letting the
   * real cryptographic signature check run offline against a self-generated key.
   *
   * <p>Everything here goes through reflection, including the cache's own public {@code add}
   * method: the cache's declared type ({@code com.amazonaws.internal.FIFOCache}) lives in {@code
   * aws-java-sdk-core}, which this module does not otherwise depend on directly, and importing it
   * just for a cast would make {@code dependency:analyze-only} report it under "Non-test scoped
   * test only dependencies found".
   */
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
