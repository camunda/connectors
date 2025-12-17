/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.intrinsic.functions;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
class CreateGithubAppInstallationTokenFunctionTest {

  private static final String APP_ID = "123456";
  private static final String INSTALLATION_ID = "789012";
  private static final String MOCK_TOKEN = "ghs_test_installation_token_abc123";

  // Test RSA private key in proper PEM format (generated for testing purposes only)
  private static String TEST_PRIVATE_KEY_FORMATTED;
  private static String TEST_PRIVATE_KEY_SINGLE_LINE;
  private static String TEST_PRIVATE_KEY_WITH_ESCAPED_NEWLINES;
  private static String TEST_PRIVATE_KEY_WITH_SPACES;
  private static String TEST_PRIVATE_KEY_WITH_IRREGULAR_SPACING;

  private CreateGithubAppInstallationTokenFunction function;

  @BeforeAll
  static void generateTestKey() throws Exception {
    // Generate a real RSA key pair for testing
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    // Encode to base64
    String base64Key = Base64.getEncoder().encodeToString(privateKey.getEncoded());

    // Create properly formatted PEM
    StringBuilder formatted = new StringBuilder();
    formatted.append("-----BEGIN RSA PRIVATE KEY-----\n");
    for (int i = 0; i < base64Key.length(); i += 64) {
      formatted.append(base64Key, i, Math.min(base64Key.length(), i + 64)).append("\n");
    }
    formatted.append("-----END RSA PRIVATE KEY-----");
    TEST_PRIVATE_KEY_FORMATTED = formatted.toString();

    // Create single-line version (no newlines)
    TEST_PRIVATE_KEY_SINGLE_LINE =
        "-----BEGIN RSA PRIVATE KEY-----" + base64Key + "-----END RSA PRIVATE KEY-----";

    // Create version with escaped newlines (as from env vars)
    TEST_PRIVATE_KEY_WITH_ESCAPED_NEWLINES = TEST_PRIVATE_KEY_FORMATTED.replace("\n", "\\n");

    // Create version with spaces instead of newlines
    TEST_PRIVATE_KEY_WITH_SPACES =
        "-----BEGIN RSA PRIVATE KEY----- " + base64Key + " -----END RSA PRIVATE KEY-----";

    // Create version with irregular spacing
    TEST_PRIVATE_KEY_WITH_IRREGULAR_SPACING =
        "  -----BEGIN RSA PRIVATE KEY-----   " + base64Key + "   -----END RSA PRIVATE KEY-----  ";
  }

  @BeforeEach
  void setUp() {
    function = new CreateGithubAppInstallationTokenFunction();
  }

  @Nested
  @DisplayName("Private Key Parsing Tests")
  class PrivateKeyParsingTests {

    @Test
    @DisplayName("Should parse properly formatted PEM key")
    void shouldParseProperlyFormattedKey() {
      String result = function.normalizePrivateKey(TEST_PRIVATE_KEY_FORMATTED);

      assertThat(result).contains("-----BEGIN RSA PRIVATE KEY-----");
      assertThat(result).contains("-----END RSA PRIVATE KEY-----");
      assertThat(result).contains("\n");
    }

    @Test
    @DisplayName("Should parse single-line key without newlines")
    void shouldParseSingleLineKey() {
      String result = function.normalizePrivateKey(TEST_PRIVATE_KEY_SINGLE_LINE);

      assertThat(result).startsWith("-----BEGIN RSA PRIVATE KEY-----\n");
      assertThat(result).endsWith("\n-----END RSA PRIVATE KEY-----");
      // Verify 64-character line formatting
      String[] lines = result.split("\n");
      for (int i = 1; i < lines.length - 1; i++) {
        assertThat(lines[i].length()).isLessThanOrEqualTo(64);
      }
    }

    @Test
    @DisplayName("Should parse key with escaped newlines from environment variables")
    void shouldParseKeyWithEscapedNewlines() {
      String result = function.normalizePrivateKey(TEST_PRIVATE_KEY_WITH_ESCAPED_NEWLINES);

      assertThat(result).contains("\n");
      assertThat(result).doesNotContain("\\n");
      assertThat(result).startsWith("-----BEGIN RSA PRIVATE KEY-----\n");
    }

    @Test
    @DisplayName("Should parse key with spaces instead of newlines")
    void shouldParseKeyWithSpaces() {
      String result = function.normalizePrivateKey(TEST_PRIVATE_KEY_WITH_SPACES);

      assertThat(result).startsWith("-----BEGIN RSA PRIVATE KEY-----\n");
      assertThat(result).endsWith("\n-----END RSA PRIVATE KEY-----");
    }

    @Test
    @DisplayName("Should parse key with irregular spacing")
    void shouldParseKeyWithIrregularSpacing() {
      String result = function.normalizePrivateKey(TEST_PRIVATE_KEY_WITH_IRREGULAR_SPACING);

      assertThat(result).startsWith("-----BEGIN RSA PRIVATE KEY-----\n");
      assertThat(result).endsWith("\n-----END RSA PRIVATE KEY-----");
      // Verify no extra whitespace in content
      String[] lines = result.split("\n");
      for (String line : lines) {
        assertThat(line).doesNotStartWith(" ");
        assertThat(line).doesNotEndWith(" ");
      }
    }

    @Test
    @DisplayName("Should handle key with mixed formatting issues")
    void shouldHandleMixedFormattingIssues() {
      // Create a key with multiple formatting issues
      String messyKey =
          "  -----BEGIN RSA PRIVATE KEY-----\\n"
              + TEST_PRIVATE_KEY_FORMATTED.split("\n")[1]
              + "  \\n"
              + TEST_PRIVATE_KEY_FORMATTED.split("\n")[2]
              + "\\n-----END RSA PRIVATE KEY-----  ";

      String result = function.normalizePrivateKey(messyKey);

      assertThat(result).contains("-----BEGIN RSA PRIVATE KEY-----");
      assertThat(result).contains("-----END RSA PRIVATE KEY-----");
      assertThat(result).doesNotContain("\\n");
    }
  }

  @Nested
  @DisplayName("End-to-End Token Generation Tests")
  @WireMockTest
  class EndToEndTests {

    @Test
    @DisplayName("Should successfully get installation token with properly formatted key")
    void shouldGetTokenWithFormattedKey(WireMockRuntimeInfo wmRuntimeInfo) {
      // Setup mock server
      stubFor(
          post(urlPathMatching("/app/installations/.*/access_tokens"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"token\": \"" + MOCK_TOKEN + "\"}")));

      CreateGithubAppInstallationTokenFunction testFunction =
          new CreateGithubAppInstallationTokenFunction(wmRuntimeInfo.getHttpBaseUrl());

      String token = testFunction.execute(TEST_PRIVATE_KEY_FORMATTED, APP_ID, INSTALLATION_ID);

      assertThat(token).isEqualTo(MOCK_TOKEN);
      verify(postRequestedFor(urlPathMatching("/app/installations/.*/access_tokens")));
    }

    @Test
    @DisplayName("Should successfully get installation token with escaped newlines key")
    void shouldGetTokenWithEscapedNewlinesKey(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlPathMatching("/app/installations/.*/access_tokens"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"token\": \"" + MOCK_TOKEN + "\"}")));

      CreateGithubAppInstallationTokenFunction testFunction =
          new CreateGithubAppInstallationTokenFunction(wmRuntimeInfo.getHttpBaseUrl());

      String token =
          testFunction.execute(TEST_PRIVATE_KEY_WITH_ESCAPED_NEWLINES, APP_ID, INSTALLATION_ID);

      assertThat(token).isEqualTo(MOCK_TOKEN);
    }

    @Test
    @DisplayName("Should successfully get installation token with single-line key")
    void shouldGetTokenWithSingleLineKey(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlPathMatching("/app/installations/.*/access_tokens"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"token\": \"" + MOCK_TOKEN + "\"}")));

      CreateGithubAppInstallationTokenFunction testFunction =
          new CreateGithubAppInstallationTokenFunction(wmRuntimeInfo.getHttpBaseUrl());

      String token = testFunction.execute(TEST_PRIVATE_KEY_SINGLE_LINE, APP_ID, INSTALLATION_ID);

      assertThat(token).isEqualTo(MOCK_TOKEN);
    }

    @Test
    @DisplayName("Should throw exception when GitHub API returns error")
    void shouldThrowExceptionOnApiError(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlPathMatching("/app/installations/.*/access_tokens"))
              .willReturn(
                  aResponse()
                      .withStatus(401)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"message\": \"Bad credentials\"}")));

      CreateGithubAppInstallationTokenFunction testFunction =
          new CreateGithubAppInstallationTokenFunction(wmRuntimeInfo.getHttpBaseUrl());

      assertThatThrownBy(
              () -> testFunction.execute(TEST_PRIVATE_KEY_FORMATTED, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to generate GitHub App installation token");
    }

    @Test
    @DisplayName("Should throw exception when response does not contain token")
    void shouldThrowExceptionWhenNoToken(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          post(urlPathMatching("/app/installations/.*/access_tokens"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"expires_at\": \"2024-01-01T00:00:00Z\"}")));

      CreateGithubAppInstallationTokenFunction testFunction =
          new CreateGithubAppInstallationTokenFunction(wmRuntimeInfo.getHttpBaseUrl());

      assertThatThrownBy(
              () -> testFunction.execute(TEST_PRIVATE_KEY_FORMATTED, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to generate GitHub App installation token")
          .cause()
          .hasMessageContaining("did not contain a token");
    }
  }

  @Nested
  @DisplayName("Invalid Key Tests")
  class InvalidKeyTests {

    @Test
    @DisplayName("Should throw exception for invalid PEM format")
    void shouldThrowExceptionForInvalidPem() {
      String invalidKey = "not a valid key";

      assertThatThrownBy(() -> function.execute(invalidKey, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for missing BEGIN marker")
    void shouldThrowExceptionForMissingBeginMarker() {
      String invalidKey = "MIIEvQIBADANBg...-----END RSA PRIVATE KEY-----";

      assertThatThrownBy(() -> function.execute(invalidKey, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for missing END marker")
    void shouldThrowExceptionForMissingEndMarker() {
      String invalidKey = "-----BEGIN RSA PRIVATE KEY-----MIIEvQIBADANBg...";

      assertThatThrownBy(() -> function.execute(invalidKey, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for empty key")
    void shouldThrowExceptionForEmptyKey() {
      assertThatThrownBy(() -> function.execute("", APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void shouldThrowExceptionForNullKey() {
      assertThatThrownBy(() -> function.execute(null, APP_ID, INSTALLATION_ID))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
