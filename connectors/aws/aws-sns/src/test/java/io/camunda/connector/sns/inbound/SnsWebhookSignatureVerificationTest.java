/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.sns.message.SnsMessageManager;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real (unmocked) AWS SDK v1 {@link SnsMessageManager} that {@link
 * SnsWebhookExecutable} relies on for validating inbound SNS webhook payloads.
 *
 * <p>{@link SnsWebhookExecutableTest} covers the connector's business logic (allow-listing, routing
 * by message type) but mocks {@link SnsMessageManager} itself, so none of its tests — including the
 * happy-path ones — invoke real signature/cert-URL verification. The test below closes part of that
 * gap: it goes through the real, unmocked {@link SnsClientSupplier} -&gt; {@link SnsMessageManager}
 * end-to-end and proves a spoofed {@code SigningCertURL} is rejected before any network call,
 * deterministically and offline.
 *
 * <p><b>What this class does NOT cover</b> (and no test in this module does): a live end-to-end
 * check that a tampered or missing cryptographic {@code Signature} is rejected by the real,
 * unmocked {@code SnsMessageManager}. That would require either a live AWS-issued signing
 * certificate (network-dependent and flaky — AWS rotates {@code SigningCertURL}s; the one baked
 * into {@link SnsWebhookExecutableTest}'s fixtures already 404s, verified empirically) or an
 * offline self-signed X.509 harness (would need a new test dependency such as BouncyCastle, or
 * shelling out to keytool plus reflection into a package-private AWS SDK constructor) — judged
 * disproportionate for this chore. What IS covered end-to-end at the connector level — including
 * the specific regression this gap could otherwise hide, e.g. {@code parseMessage} throwing and
 * that exception being silently swallowed instead of propagated — is in {@link
 * SnsWebhookExecutableTest#triggerWebhook_SignatureVerificationFails_PropagatesException()}, which
 * asserts the real production code path never falls back to an unverified payload when the (mocked)
 * manager rejects a message.
 */
class SnsWebhookSignatureVerificationTest {

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
}
