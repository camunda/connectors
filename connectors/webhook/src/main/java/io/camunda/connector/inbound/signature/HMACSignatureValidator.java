/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HMACSignatureValidator {

  private static final Logger LOG = LoggerFactory.getLogger(HMACSignatureValidator.class);

  private final byte[] requestBody;
  private final Map<String, String> headers;
  private final String hmacHeader;
  private final String hmacSecretKey;
  private final HMACAlgoCustomerChoice hmacAlgo;

  public HMACSignatureValidator(
      final byte[] requestBody,
      final Map<String, String> headers,
      final String hmacHeader,
      final String hmacSecretKey,
      final HMACAlgoCustomerChoice hmacAlgo) {
    this.requestBody = requestBody;
    this.headers = headers;
    this.hmacHeader = hmacHeader;
    this.hmacSecretKey = hmacSecretKey;
    this.hmacAlgo = hmacAlgo;
    if (requestBody == null) {
      throw new ConnectorException("Request body must not be null");
    }
    if (headers == null) {
      throw new ConnectorException("Headers must not be null");
    }
    if (hmacHeader == null) {
      throw new ConnectorException("HMAC header must not be null");
    }
    if (hmacSecretKey == null) {
      throw new ConnectorException("HMAC secret key must not be null");
    }
    if (hmacAlgo == null) {
      throw new ConnectorException("HMAC algorithm key must not be null");
    }
  }

  public boolean isRequestValid()
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    var caseInsensitiveHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveHeaders.putAll(headers);

    if (!caseInsensitiveHeaders.containsKey(hmacHeader)) {
      throw new ConnectorException("Expected HMAC header " + hmacHeader + ", but was not present");
    }
    final String providedHmac = caseInsensitiveHeaders.get(hmacHeader);
    LOG.debug("Given HMAC from webhook call: {}", providedHmac);

    if (providedHmac == null || providedHmac.length() == 0) {
      return false;
    }

    // Some webhooks produce longer version, like sha256=aabbcc...; hmac-sha1=aabbcc...; etc
    var providedHmacWithoutTag = providedHmac;
    var split = providedHmacWithoutTag.split("=");
    if (split.length == 2) {
      providedHmacWithoutTag = split[1];
    }

    Mac sha256_HMAC = Mac.getInstance(hmacAlgo.getAlgoReference());
    SecretKeySpec secret_key =
        new SecretKeySpec(
            hmacSecretKey.getBytes(StandardCharsets.UTF_8), hmacAlgo.getAlgoReference());
    sha256_HMAC.init(secret_key);
    byte[] expectedHmac = sha256_HMAC.doFinal(requestBody);

    // Some webhooks produce short HMAC message, e.g. aabbcc...
    String expectedHmacString = Hex.encodeHexString(expectedHmac);

    // The Twilio produce base64 version
    String expectedBase64HmacString = DatatypeConverter.printBase64Binary(expectedHmac);
    LOG.debug("Computed HMAC from webhook body: {}", expectedHmacString);
    return providedHmac.equals(expectedHmacString)
        || providedHmacWithoutTag.equals(expectedHmacString)
        || providedHmac.equals(expectedBase64HmacString);
  }
}
