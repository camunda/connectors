package io.camunda.connector.inbound.security.signature;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

class HMACSignatureValidatorTest {

    private static final String GH_SHA1_HEADER = "X-Hub-Signature";
    private static final String GH_SHA1_VALUE = "sha1=de81c837cc792e7d21d7bf9feb74cd19d714baca";
    private static final String GH_SHA256_HEADER = "X-Hub-Signature-256";
    private static final String GH_SHA256_LONG_VALUE = "sha256=dd22cfb7ae96875d81bd1a695a0244f2b4c32c0938be0b445f520b0b3e0f43fd";
    private static final String GH_SHA256_SHORT_VALUE = "dd22cfb7ae96875d81bd1a695a0244f2b4c32c0938be0b445f520b0b3e0f43fd";
    private static final String GH_SECRET_KEY = "mySecretKey";


    @ParameterizedTest
    @MethodSource("provideHMACTestData")
    public void hmacSignatureVerificationParametrizedTest(final HMACTestEntry testEntry)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        HMACSignatureValidator validator = new HMACSignatureValidator(
                readString(new File(testEntry.filepathWithBody).toPath(), UTF_8).getBytes(UTF_8),
                testEntry.originalRequestHeaders,
                testEntry.headerWithHmac,
                testEntry.decodedSecretKey,
                testEntry.algo
        );
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
                        HMACAlgoCustomerChoice.sha_256)
        );
    }

    private static class HMACTestEntry {
        final String filepathWithBody;
        final Map<String, String> originalRequestHeaders;
        final String headerWithHmac;
        final String decodedSecretKey;
        final HMACAlgoCustomerChoice algo;

        public HMACTestEntry(String filepathWithBody,
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