/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class HMACSignatureValidatorTest {

  private static final String GH_SHA1_HEADER = "x-hub-signature";
  private static final String GH_SHA1_VALUE = "sha1=de81c837cc792e7d21d7bf9feb74cd19d714baca";
  private static final String GH_SHA256_HEADER = "x-hub-signature-256";
  private static final String GH_SHA256_LONG_VALUE =
      "sha256=dd22cfb7ae96875d81bd1a695a0244f2b4c32c0938be0b445f520b0b3e0f43fd";
  private static final String GH_SHA256_SHORT_VALUE =
      "dd22cfb7ae96875d81bd1a695a0244f2b4c32c0938be0b445f520b0b3e0f43fd";
  private static final String GH_SECRET_KEY = "mySecretKey";

  @ParameterizedTest
  @MethodSource("provideHMACTestData")
  public void hmacSignatureVerificationParametrizedTest(final HMACTestEntry testEntry)
      throws IOException, NoSuchAlgorithmException, InvalidKeyException {
    HMACSignatureValidator validator =
        new HMACSignatureValidator(
            readString(new File(testEntry.filepathWithBody).toPath(), UTF_8).getBytes(UTF_8),
            testEntry.originalRequestHeaders,
            testEntry.headerWithHmac,
            testEntry.decodedSecretKey,
            testEntry.algo);
    Assertions.assertThat(validator.isRequestValid()).isTrue();
  }

  private static Stream<HMACTestEntry> provideHMACTestData() {
    return Stream.of(
        new HMACTestEntry(
            "src/test/resources/hmac/gh-webhook-request.json",
            Map.of(GH_SHA256_HEADER, GH_SHA256_LONG_VALUE),
            GH_SHA256_HEADER,
            GH_SECRET_KEY,
            HMACAlgoCustomerChoice.sha_256),
        new HMACTestEntry(
            "src/test/resources/hmac/gh-webhook-request.json",
            Map.of(GH_SHA1_HEADER, GH_SHA1_VALUE),
            GH_SHA1_HEADER,
            GH_SECRET_KEY,
            HMACAlgoCustomerChoice.sha_1),
        new HMACTestEntry(
            "src/test/resources/hmac/gh-webhook-request.json",
            Map.of(GH_SHA256_HEADER, GH_SHA256_SHORT_VALUE),
            GH_SHA256_HEADER,
            GH_SECRET_KEY,
            HMACAlgoCustomerChoice.sha_256));
  }

  private static class HMACTestEntry {
    final String filepathWithBody;
    final Map<String, String> originalRequestHeaders;
    final String headerWithHmac;
    final String decodedSecretKey;
    final HMACAlgoCustomerChoice algo;

    public HMACTestEntry(
        String filepathWithBody,
        Map<String, String> originalRequestHeaders,
        String headerWithHmac,
        String decodedSecretKey,
        HMACAlgoCustomerChoice algo) {
      this.filepathWithBody = filepathWithBody;
      this.originalRequestHeaders = originalRequestHeaders;
      this.headerWithHmac = headerWithHmac;
      this.decodedSecretKey = decodedSecretKey;
      this.algo = algo;
    }
  }
}
