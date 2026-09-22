/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException.Reason;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategy;
import io.camunda.connector.inbound.signature.strategy.HMACEncodingStrategyFactory;
import io.camunda.connector.inbound.utils.HttpMethods;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.TreeMap;

public class HMACVerifier {

  /** Mirrors the Slack SDK's own timestamp tolerance (5 minutes). */
  public static final int DEFAULT_HMAC_TOLERANCE_SECONDS = 300;

  private final HMACScope[] hmacScopes;
  private final String hmacHeader;
  private final String hmacSecret;
  private final HMACAlgoCustomerChoice hmacAlgorithm;
  private final String hmacTimestampHeader;
  private final Integer hmacToleranceSeconds;
  private final Clock clock;

  /**
   * Legacy constructor retained for source/binary compatibility with existing connector code built
   * against the pre-timestamp four-argument constructor. Disables timestamp validation (no {@code
   * hmacTimestampHeader}), matching the pre-existing behavior exactly.
   */
  public HMACVerifier(
      HMACScope[] hmacScopes,
      String hmacHeader,
      String hmacSecret,
      HMACAlgoCustomerChoice hmacAlgorithm) {
    this(hmacScopes, hmacHeader, hmacSecret, hmacAlgorithm, null, null);
  }

  public HMACVerifier(
      HMACScope[] hmacScopes,
      String hmacHeader,
      String hmacSecret,
      HMACAlgoCustomerChoice hmacAlgorithm,
      String hmacTimestampHeader,
      Integer hmacToleranceSeconds) {
    this(
        hmacScopes,
        hmacHeader,
        hmacSecret,
        hmacAlgorithm,
        hmacTimestampHeader,
        hmacToleranceSeconds,
        Clock.systemUTC());
  }

  // Package-private: lets tests inject a fixed clock instead of depending on wall-clock time.
  HMACVerifier(
      HMACScope[] hmacScopes,
      String hmacHeader,
      String hmacSecret,
      HMACAlgoCustomerChoice hmacAlgorithm,
      String hmacTimestampHeader,
      Integer hmacToleranceSeconds,
      Clock clock) {
    this.hmacScopes = hmacScopes;
    this.hmacHeader = hmacHeader;
    this.hmacSecret = hmacSecret;
    this.hmacAlgorithm = hmacAlgorithm;
    this.hmacTimestampHeader = hmacTimestampHeader;
    this.hmacToleranceSeconds = hmacToleranceSeconds;
    this.clock = clock;
    rejectUnsupportedScopeCombination(hmacScopes);
  }

  /**
   * Fails fast (at construction time, i.e. connector activation) when the non-{@code timestamp}
   * portion of {@code hmacScopes} isn't a combination {@link HMACEncodingStrategyFactory} actually
   * supports — e.g. {@code [timestamp, url]} or {@code [timestamp, parameters]} strip down to
   * {@code [url]}/{@code [parameters]} alone, which has no matching strategy. Without this check,
   * {@link HMACEncodingStrategyFactory#getStrategy} throws {@code UnsupportedOperationException} on
   * every incoming request instead, which nothing upstream maps to a client error — an unhandled
   * 500 on every request, not a deploy-time failure.
   */
  private static void rejectUnsupportedScopeCombination(HMACScope[] hmacScopes) {
    HMACScope[] effectiveScopes = nonTimestampScopes(hmacScopes);
    try {
      // Every scope combination the factory supports is either method-independent or supports
      // both branches of its method-conditional entries, so one arbitrary non-GET method is
      // enough to determine whether the combination is supported at all.
      HMACEncodingStrategyFactory.getStrategy(effectiveScopes, HttpMethods.post.name());
    } catch (UnsupportedOperationException e) {
      throw new ConnectorInputException(
          "Unsupported HMAC scope combination "
              + Arrays.toString(hmacScopes)
              + ": "
              + e.getMessage());
    }
  }

  private static HMACScope[] nonTimestampScopes(HMACScope[] hmacScopes) {
    HMACScope[] filtered =
        Arrays.stream(hmacScopes)
            .filter(scope -> scope != HMACScope.TIMESTAMP)
            .toArray(HMACScope[]::new);
    // TIMESTAMP is an additive scope: on its own, fall back to the default BODY strategy.
    return filtered.length > 0 ? filtered : new HMACScope[] {HMACScope.BODY};
  }

  public void verifySignature(WebhookProcessingPayload payload) {
    if (hasTimestampScope()) {
      validateTimestamp(payload);
    }
    if (!webhookSignatureIsValid(payload)) {
      throw new WebhookSecurityException(
          401, Reason.INVALID_SIGNATURE, "HMAC signature check didn't pass");
    }
  }

  private boolean hasTimestampScope() {
    return Arrays.asList(hmacScopes).contains(HMACScope.TIMESTAMP);
  }

  private void validateTimestamp(WebhookProcessingPayload payload) {
    if (hmacTimestampHeader == null || hmacTimestampHeader.isBlank()) {
      throw new WebhookSecurityException(
          401,
          Reason.INVALID_SIGNATURE,
          "HMAC scope 'timestamp' is enabled but no HMAC timestamp header is configured");
    }

    String timestampValue = timestampHeaderValue(payload);
    if (timestampValue == null || timestampValue.isBlank()) {
      throw new WebhookSecurityException(
          401,
          Reason.INVALID_SIGNATURE,
          "HMAC timestamp header " + hmacTimestampHeader + " is missing");
    }

    // Deliberately not trimmed: the signed material is this exact header string (see
    // withTimestampPrefix), so tolerating surrounding whitespace here would accept a timestamp as
    // fresh while still signing bytes a well-formed sender never produced — silently rejecting a
    // legitimately fresh, correctly-signed request instead. A padded value is malformed, full stop.
    long timestampEpochSeconds;
    try {
      timestampEpochSeconds = Long.parseLong(timestampValue);
    } catch (NumberFormatException e) {
      throw new WebhookSecurityException(
          401,
          Reason.INVALID_SIGNATURE,
          "HMAC timestamp header " + hmacTimestampHeader + " is malformed: " + timestampValue);
    }

    int toleranceSeconds =
        hmacToleranceSeconds != null ? hmacToleranceSeconds : DEFAULT_HMAC_TOLERANCE_SECONDS;
    long nowEpochSeconds = clock.instant().getEpochSecond();
    boolean withinTolerance;
    try {
      long skewSeconds = Math.absExact(Math.subtractExact(nowEpochSeconds, timestampEpochSeconds));
      withinTolerance = skewSeconds <= toleranceSeconds;
    } catch (ArithmeticException e) {
      // The skew arithmetic overflowed, which only happens for a timestamp far enough outside
      // the long range to be nonsensical; treat it the same as any other out-of-tolerance value.
      withinTolerance = false;
    }
    if (!withinTolerance) {
      throw new WebhookSecurityException(
          401,
          Reason.INVALID_SIGNATURE,
          "HMAC timestamp is outside the allowed tolerance of " + toleranceSeconds + " seconds");
    }
  }

  private String timestampHeaderValue(WebhookProcessingPayload payload) {
    if (hmacTimestampHeader == null) {
      return null;
    }
    var caseInsensitiveHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveHeaders.putAll(payload.headers());
    return caseInsensitiveHeaders.get(hmacTimestampHeader);
  }

  private boolean webhookSignatureIsValid(WebhookProcessingPayload payload) {
    try {
      HMACScope[] effectiveScopes = nonTimestampScopes(hmacScopes);
      HMACEncodingStrategy strategy =
          HMACEncodingStrategyFactory.getStrategy(effectiveScopes, payload.method());
      byte[] bytesToSign = strategy.getBytesToSign(payload);
      if (hasTimestampScope()) {
        bytesToSign = withTimestampPrefix(bytesToSign, payload);
      }
      return validateHmacSignature(bytesToSign, payload);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] withTimestampPrefix(byte[] bytesToSign, WebhookProcessingPayload payload) {
    byte[] timestampPrefix = (timestampHeaderValue(payload) + ":").getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[timestampPrefix.length + bytesToSign.length];
    System.arraycopy(timestampPrefix, 0, combined, 0, timestampPrefix.length);
    System.arraycopy(bytesToSign, 0, combined, timestampPrefix.length, bytesToSign.length);
    return combined;
  }

  private boolean validateHmacSignature(byte[] signatureData, WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final HMACSignatureValidator hmacSignatureValidator =
        new HMACSignatureValidator(
            signatureData, payload.headers(), hmacHeader, hmacSecret, hmacAlgorithm);
    return hmacSignatureValidator.isRequestValid();
  }
}
