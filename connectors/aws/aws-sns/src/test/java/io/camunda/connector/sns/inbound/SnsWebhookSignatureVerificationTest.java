/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.util.SignatureChecker;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real (unmocked) AWS SDK v1 signature-verification code that {@link
 * SnsMessageManager} relies on for validating inbound SNS webhook payloads, to prove that a
 * tampered or incomplete signature is actually rejected rather than silently accepted.
 *
 * <p>{@link SnsWebhookExecutableTest} covers the connector's business logic (allow-listing, routing
 * by message type) with a mocked {@link SnsMessageManager}, so none of its tests - including the
 * "happy path" ones - ever invoke real signature verification. This class closes that gap.
 *
 * <p>Two layers of the real verification chain are covered:
 *
 * <ul>
 *   <li>{@link SignatureChecker} (via a self-generated RSA key pair) - the cryptographic check that
 *       {@code SnsMessageManager} -&gt; {@code SignatureVerifier} delegates to for the actual
 *       {@code Signature} field. This is deliberately tested in isolation from AWS's real signing
 *       certificate: {@code SnsMessageManager} downloads that certificate from the exact {@code
 *       SigningCertURL} embedded in the message, and AWS rotates those URLs over time (verified
 *       empirically: the SigningCertURL baked into the pre-recorded fixtures in {@link
 *       SnsWebhookExecutableTest} already 404s), so pinning a test to a real, live cert would be
 *       flaky by construction and would eventually fail for reasons unrelated to the code under
 *       test. Generating our own key pair keeps the test exercising the same unmocked production
 *       class deterministically and offline.
 *   <li>{@link SnsMessageManager} itself (via the real, unmocked {@link SnsClientSupplier}) for the
 *       one negative case that *is* fully verifiable offline end-to-end: a spoofed {@code
 *       SigningCertURL} is rejected before any network call is even attempted, because the
 *       HTTPS/SNS-origin check runs first.
 * </ul>
 */
class SnsWebhookSignatureVerificationTest {

  private static KeyPair keyPair;

  @BeforeAll
  static void generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();
  }

  @Test
  void validSignature_isAccepted() throws Exception {
    Map<String, String> message = validlySignedNotification();

    boolean result = new SignatureChecker().verifySignature(message, keyPair.getPublic());

    // Sanity check on the test harness itself: if this ever turned false, the tampered-signature
    // test below would be meaningless (a signature checker that rejects everything "passes" it
    // for the wrong reason).
    assertThat(result).isTrue();
  }

  @Test
  void tamperedSignature_isRejected() throws Exception {
    Map<String, String> message = new LinkedHashMap<>(validlySignedNotification());
    message.put("Signature", tamper(message.get("Signature")));

    boolean result = new SignatureChecker().verifySignature(message, keyPair.getPublic());

    assertThat(result).isFalse();
  }

  @Test
  void missingSignature_isRejected() throws Exception {
    Map<String, String> message = new LinkedHashMap<>(validlySignedNotification());
    message.remove("Signature");

    assertThatThrownBy(() -> new SignatureChecker().verifySignature(message, keyPair.getPublic()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("null values");
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
        .hasMessageContaining("SigningCertUrl");
  }

  /**
   * Builds a "Notification" message signed with {@link #keyPair}'s private key, following AWS's
   * documented canonicalization for that message type: the {@code Message}, {@code MessageId},
   * {@code Subject}, {@code Timestamp}, {@code TopicArn} and {@code Type} fields present, each
   * rendered as "key\nvalue\n" and concatenated in ascending key order.
   */
  private Map<String, String> validlySignedNotification() throws Exception {
    Map<String, String> message = new LinkedHashMap<>();
    message.put("Type", "Notification");
    message.put("MessageId", "2e062e6b-a527-5e68-b69b-72a8e42add60");
    message.put("TopicArn", "arn:aws:sns:eu-central-1:111222333444:SNSWebhook");
    message.put("Subject", "Subject - test");
    message.put("Message", "Hello, world");
    message.put("Timestamp", "2023-04-26T15:10:05.479Z");
    message.put("SignatureVersion", "1");
    message.put("Signature", sign(message));
    return message;
  }

  private String sign(Map<String, String> fields) throws Exception {
    Signature signer = Signature.getInstance("SHA1withRSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(canonicalNotificationString(fields).getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signer.sign());
  }

  private String canonicalNotificationString(Map<String, String> fields) {
    TreeMap<String, String> signable = new TreeMap<>();
    for (String key :
        new String[] {"Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type"}) {
      if (fields.containsKey(key)) {
        signable.put(key, fields.get(key));
      }
    }
    StringBuilder sb = new StringBuilder();
    signable.forEach((k, v) -> sb.append(k).append('\n').append(v).append('\n'));
    return sb.toString();
  }

  /** Flips one byte in the middle of the decoded signature, re-encoding to valid Base64. */
  private String tamper(String base64Signature) {
    byte[] bytes = Base64.getDecoder().decode(base64Signature);
    bytes[bytes.length / 2] ^= 0xFF;
    return Base64.getEncoder().encodeToString(bytes);
  }
}
