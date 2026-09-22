/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import static io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice.sha_256;
import static io.camunda.connector.inbound.utils.HttpWebhookUtil.HEADER_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.utils.HttpMethods;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class HMACVerifierTest {

  private static final String SECRET = "mySecretKey";
  private static final String TIMESTAMP_HEADER = "X-HMAC-Timestamp";
  private static final int TOLERANCE_SECONDS = 300;
  private static final String TOLERANCE = "PT300S";

  @Test
  void verifySignature_WhenSignatureMatches_ShouldNotThrowException() {
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", SECRET, sha_256, null, null);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenUsingLegacyFourArgConstructor_ShouldNotThrowException() {
    // Source/binary compatibility: code compiled against the pre-timestamp constructor must keep
    // working unchanged.
    HMACVerifier verifier =
        new HMACVerifier(new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", SECRET, sha_256);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenSignatureDoesNotMatch_ShouldThrowException() {
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", SECRET, sha_256, null, null);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(HEADER_CONTENT_TYPE, "application/json", "X-HMAC-Sig", "invalidSignature123"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("HMAC signature check didn't pass");
  }

  /** Builds a payload whose signature is valid for {@code timestampEpochSeconds}. */
  private WebhookProcessingPayload signedPayload(long timestampEpochSeconds, byte[] body)
      throws NoSuchAlgorithmException, InvalidKeyException {
    String timestamp = Long.toString(timestampEpochSeconds);
    byte[] bytesToSign = concat((timestamp + ":").getBytes(StandardCharsets.UTF_8), body);
    String signature = hmacHex(SECRET, bytesToSign);

    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_CONTENT_TYPE, "application/json");
    headers.put("X-HMAC-Sig", signature);
    headers.put(TIMESTAMP_HEADER, timestamp);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(body);
    return payload;
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private static String hmacHex(String secret, byte[] data)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance(sha_256.getAlgoReference());
    mac.init(
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), sha_256.getAlgoReference()));
    return Hex.encodeHexString(mac.doFinal(data));
  }

  private static Clock fixedClock(long epochSeconds) {
    return Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
  }

  @Test
  void verifySignature_WhenTimestampWithinTolerance_ShouldNotThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = 1_700_000_000L;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(now));

    WebhookProcessingPayload payload =
        signedPayload(now, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenSkewExactlyAtToleranceBoundary_ShouldNotThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long sentAt = 1_700_000_000L;
    long verifiedAt = sentAt + TOLERANCE_SECONDS; // exactly at the boundary, not past it
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(verifiedAt));

    WebhookProcessingPayload payload =
        signedPayload(sentAt, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenSkewOneSecondPastToleranceBoundary_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long sentAt = 1_700_000_000L;
    long verifiedAt = sentAt + TOLERANCE_SECONDS + 1; // one second past the boundary
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(verifiedAt));

    WebhookProcessingPayload payload =
        signedPayload(sentAt, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void verifySignature_WhenSkewCalculationWouldOverflow_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // A timestamp crafted so that (now - timestamp) overflows Long, and Math.abs() of the
    // overflowed result is itself negative (the classic Long.MIN_VALUE quirk) — naive arithmetic
    // would let this compare as "within tolerance" and bypass the staleness check entirely.
    long now = 1_700_000_000L;
    long overflowingTimestamp = Long.MIN_VALUE + now;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(now));

    WebhookProcessingPayload payload =
        signedPayload(
            overflowingTimestamp, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void verifySignature_WhenDefaultScopeWithoutTimestamp_IgnoresTimestampHeader()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // Deliberate design choice: TIMESTAMP is an additive, opt-in scope (per the issue's own
    // remediation hints, which offer a separate seen-signature-cache mitigation for senders that
    // never sign a timestamp — e.g. GitHub, Twilio, Shopify sign only the body/URL). A connector
    // configured with the default BODY-only scope must keep working unchanged and must not
    // consult a timestamp header at all, even if one happens to be present with a wildly stale
    // value the sender put there for its own purposes.
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    String signature = hmacHex(SECRET, body);

    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_CONTENT_TYPE, "application/json");
    headers.put("X-HMAC-Sig", signature);
    headers.put(TIMESTAMP_HEADER, "1"); // 1970-01-01, decades stale, and irrelevant here

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(body);

    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY}, "X-HMAC-Sig", SECRET, sha_256, null, null);

    assertThatCode(() -> verifier.verifySignature(payload)).doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenTimestampHeaderMissing_ShouldThrowException() {
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(1_700_000_000L));

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers())
        .thenReturn(
            Map.of(
                HEADER_CONTENT_TYPE,
                "application/json",
                "X-HMAC-Sig",
                "fa431d91a69beb76186b3b082c5bb87bab0702769d65761af2361cbf3a17cc09"));
    when(payload.rawBody()).thenReturn("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("missing")
        .hasMessageContaining(TIMESTAMP_HEADER);
  }

  @Test
  void verifySignature_WhenTimestampHeaderMalformed_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = 1_700_000_000L;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(now));

    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_CONTENT_TYPE, "application/json");
    headers.put(TIMESTAMP_HEADER, "not-a-timestamp");
    headers.put(
        "X-HMAC-Sig",
        hmacHex(SECRET, concat("not-a-timestamp:".getBytes(StandardCharsets.UTF_8), body)));

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(body);

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("malformed")
        .hasMessageContaining(TIMESTAMP_HEADER);
  }

  @Test
  void verifySignature_WhenTimestampHeaderHasSurroundingWhitespace_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // Regression test: parsing used to tolerate whitespace (Long.parseLong(value.trim())) while
    // the signed material always used the raw, untrimmed header — so a padded value could be
    // treated as fresh yet signed over different bytes than a sender who signs the trimmed
    // numeric string would produce. Even a self-consistent request — correctly signed over the
    // padded bytes it actually sends — must still be rejected as malformed; tolerance for the
    // format isn't the point, byte-for-byte agreement between the freshness check and the signed
    // material is.
    long now = 1_700_000_000L;
    String paddedTimestamp = " " + now + " ";
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    String signature =
        hmacHex(SECRET, concat((paddedTimestamp + ":").getBytes(StandardCharsets.UTF_8), body));

    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_CONTENT_TYPE, "application/json");
    headers.put(TIMESTAMP_HEADER, paddedTimestamp);
    headers.put("X-HMAC-Sig", signature);

    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn(HttpMethods.post.name());
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(body);

    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(now));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("malformed")
        .hasMessageContaining(TIMESTAMP_HEADER);
  }

  @Test
  void verifySignature_WhenTimestampStale_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long requestSentAt = 1_700_000_000L;
    long verifiedAt = requestSentAt + TOLERANCE_SECONDS + 1;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(verifiedAt));

    WebhookProcessingPayload payload =
        signedPayload(requestSentAt, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void verifySignature_WhenToleranceMalformedAndTimestampMatchesClock_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = 1_700_000_000L;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            "not-a-duration",
            fixedClock(now));

    WebhookProcessingPayload payload =
        signedPayload(now, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("HMAC tolerance is malformed");
  }

  @Test
  void verifySignature_WhenTimestampFutureSkewed_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long now = 1_700_000_000L;
    long claimedTimestamp = now + TOLERANCE_SECONDS + 1;
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(now));

    WebhookProcessingPayload payload =
        signedPayload(claimedTimestamp, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void verifySignature_WhenReplayedAfterToleranceWindowElapses_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long originalSendTime = 1_700_000_000L;
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);

    // The original request is captured and verified while still fresh.
    HMACVerifier verifierAtOriginalTime =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(originalSendTime));
    WebhookProcessingPayload originalRequest = signedPayload(originalSendTime, body);
    assertThatCode(() -> verifierAtOriginalTime.verifySignature(originalRequest))
        .doesNotThrowAnyException();

    // The exact same byte-identical request (same signature, same timestamp) is replayed once the
    // tolerance window has elapsed and must now be rejected.
    long replayTime = originalSendTime + TOLERANCE_SECONDS + 1;
    HMACVerifier verifierAtReplayTime =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(replayTime));

    assertThatThrownBy(() -> verifierAtReplayTime.verifySignature(originalRequest))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void verifySignature_WhenTimestampHeaderIsForgedButSignatureIsNot_ShouldThrowException()
      throws NoSuchAlgorithmException, InvalidKeyException {
    long originalTimestamp = 1_700_000_000L;
    byte[] body = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);
    WebhookProcessingPayload originalRequest = signedPayload(originalTimestamp, body);
    String originalSignature = originalRequest.headers().get("X-HMAC-Sig");

    // An attacker who captured the original request rewrites only the timestamp header to a
    // fresh value, keeping the original (now-stale) signature untouched. If the timestamp were
    // not part of the signed material, this forged timestamp would let a captured request be
    // replayed indefinitely just by relabeling it as fresh.
    long forgedTimestamp = originalTimestamp + TOLERANCE_SECONDS + 1;
    Map<String, String> forgedHeaders = new HashMap<>();
    forgedHeaders.put(HEADER_CONTENT_TYPE, "application/json");
    forgedHeaders.put("X-HMAC-Sig", originalSignature);
    forgedHeaders.put(TIMESTAMP_HEADER, Long.toString(forgedTimestamp));
    WebhookProcessingPayload forgedRequest = mock(WebhookProcessingPayload.class);
    when(forgedRequest.method()).thenReturn(HttpMethods.post.name());
    when(forgedRequest.headers()).thenReturn(forgedHeaders);
    when(forgedRequest.rawBody()).thenReturn(body);

    // Verified at the forged (fresh) time, so a pass here can only mean the tolerance check let
    // it through — a failure must come from the signature check, proving the timestamp is bound
    // into the signed material rather than checked out-of-band.
    HMACVerifier verifierAtForgedTime =
        new HMACVerifier(
            new HMACScope[] {HMACScope.BODY, HMACScope.TIMESTAMP},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(forgedTimestamp));

    assertThatThrownBy(() -> verifierAtForgedTime.verifySignature(forgedRequest))
        .isInstanceOf(WebhookSecurityException.class)
        .hasMessageContaining("HMAC signature check didn't pass");
  }

  @Test
  void
      rejectUnsupportedScopeCombination_WhenTimestampScopeStripsDownToUnsupportedUrlAlone_ShouldThrow() {
    // Regression test: [timestamp, url] strips TIMESTAMP down to [url] alone, which
    // HMACEncodingStrategyFactory has no strategy for. Before this fail-fast check, this
    // combination only failed at request-processing time — an UnsupportedOperationException
    // wrapped in a bare RuntimeException, indistinguishable from an unhandled 500, on every
    // single request. Connectors call this from activate() (gated on HMAC being enabled) so it
    // fails immediately at deploy time instead — this is not run from the constructor itself,
    // since both connectors construct HMACVerifier unconditionally regardless of that gate.
    assertThatThrownBy(
            () ->
                HMACVerifier.rejectUnsupportedScopeCombination(
                    new HMACScope[] {HMACScope.TIMESTAMP, HMACScope.URL}))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Unsupported HMAC scope combination");
  }

  @Test
  void
      rejectUnsupportedScopeCombination_WhenTimestampScopeStripsDownToUnsupportedParametersAlone_ShouldThrow() {
    // Same regression, for [timestamp, parameters] stripping down to [parameters] alone.
    assertThatThrownBy(
            () ->
                HMACVerifier.rejectUnsupportedScopeCombination(
                    new HMACScope[] {HMACScope.TIMESTAMP, HMACScope.PARAMETERS}))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Unsupported HMAC scope combination");
  }

  @Test
  void rejectUnsupportedScopeCombination_WhenTimestampCombinedWithSupportedScopes_ShouldNotThrow() {
    // Counterpart: [timestamp, url, body] strips down to [url, body], a supported combination,
    // and must keep working.
    assertThatCode(
            () ->
                HMACVerifier.rejectUnsupportedScopeCombination(
                    new HMACScope[] {HMACScope.TIMESTAMP, HMACScope.URL, HMACScope.BODY}))
        .doesNotThrowAnyException();
  }

  @Test
  void verifySignature_WhenConstructedWithUnsupportedScopeCombination_StillThrowsAtRequestTime()
      throws NoSuchAlgorithmException, InvalidKeyException {
    // The constructor itself does not validate scope support (that's
    // rejectUnsupportedScopeCombination's
    // job, called from activate() gated on HMAC being enabled) — so constructing with an
    // unsupported combination must not throw, but using it to verify a request still fails
    // (as an unhandled exception, not silently) rather than falsely accepting anything.
    HMACVerifier verifier =
        new HMACVerifier(
            new HMACScope[] {HMACScope.TIMESTAMP, HMACScope.URL},
            "X-HMAC-Sig",
            SECRET,
            sha_256,
            TIMESTAMP_HEADER,
            TOLERANCE,
            fixedClock(1_700_000_000L));

    WebhookProcessingPayload payload =
        signedPayload(1_700_000_000L, "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> verifier.verifySignature(payload))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
