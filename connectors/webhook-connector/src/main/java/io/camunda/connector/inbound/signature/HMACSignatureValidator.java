/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: add URL signing and Base64 format
public class HMACSignatureValidator {

  private static final Logger LOG = LoggerFactory.getLogger(HMACSignatureValidator.class);

  public static final String HMAC_VALIDATION_ENABLED_PROPERTY = "inbound.shouldValidateHmac";
  public static final String HMAC_VALIDATION_ENABLED = "enabled";
  public static final String HMAC_VALIDATION_DISABLED = "disabled";
  public static final String HMAC_HEADER_PROPERTY = "inbound.hmacHeader";
  public static final String HMAC_SECRET_KEY_PROPERTY = "inbound.hmacSecret";
  public static final String HMAC_ALGO_PROPERTY = "inbound.hmacAlgorithm";
  public static final String HMAC_VALIDATION_FAILED_KEY = "HMACFailedReason";
  public static final String HMAC_VALIDATION_FAILED_REASON_DIDNT_MATCH =
      "DigitalSignatureDidntMatch";

  private final byte[] requestBody;
  private final Map<String, Object> headers;
  private final String hmacHeader;
  private final String hmacSecretKey;
  private final HMACAlgoCustomerChoice hmacAlgo;

  public HMACSignatureValidator(
      final byte[] requestBody,
      final Map<String, Object> headers,
      final String hmacHeader,
      final String hmacSecretKey,
      final HMACAlgoCustomerChoice hmacAlgo) {
    this.requestBody = requestBody;
    this.headers = headers;
    this.hmacHeader = hmacHeader;
    this.hmacSecretKey = hmacSecretKey;
    this.hmacAlgo = hmacAlgo;
    Objects.requireNonNull(requestBody, "Request body must not be null");
    Objects.requireNonNull(headers, "Headers must not be null");
    Objects.requireNonNull(hmacHeader, "HMAC header must not be null");
    Objects.requireNonNull(hmacSecretKey, "HMAC secret key must not be null");
    Objects.requireNonNull(hmacAlgo, "HMAC algorithm must not be null");
  }

  public boolean isRequestValid() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    var caseInsensitiveHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveHeaders.putAll(headers);
    
    if (!caseInsensitiveHeaders.containsKey(hmacHeader)) {
      throw new IOException("Expected HMAC header " + hmacHeader + ", but was not present");
    }
    final String providedHmac = headers.get(hmacHeader.toLowerCase()).toString();
    LOG.debug("Given HMAC from webhook call: {}", providedHmac);

    if (providedHmac == null || providedHmac.length() == 0) {
      return false;
    }

    Mac sha256_HMAC = Mac.getInstance(hmacAlgo.getAlgoReference());
    SecretKeySpec secret_key =
        new SecretKeySpec(
            hmacSecretKey.getBytes(StandardCharsets.UTF_8), hmacAlgo.getAlgoReference());
    sha256_HMAC.init(secret_key);
    byte[] expectedHmac = sha256_HMAC.doFinal(requestBody);

    // Some webhooks produce short HMAC message, e.g. aabbcc...
    String expectedShortHmacString = Hex.encodeHexString(expectedHmac);
    // The other produce longer version, like sha256=aabbcc...
    String expectedLongHmacString = hmacAlgo.getTag() + "=" + expectedShortHmacString;
    LOG.debug(
        "Computed HMAC from webhook body: {}, {}", expectedShortHmacString, expectedLongHmacString);

    return providedHmac.equals(expectedShortHmacString)
        || providedHmac.equals(expectedLongHmacString);
  }
}
