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
package io.camunda.connector.util.pem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PemUtilTest {

  // 2 full 64-character lines and a short last line
  private static final String BODY_LINE_1 =
      "MIIDdzCCAl+gAwIBAgIEAgAAuTANBgkqhkiG9w0BAQUFADBaMQswCQYDVQQGEwJJ";
  private static final String BODY_LINE_2 =
      "RTESMBAGA1UEChMJQmFsdGltb3JlMRMwEQYDVQQLEwpDeWJlclRydXN0MSIwIAYD";
  private static final String BODY_LINE_3 = "VQQDExlCYWx0aW1vcmUgQ3liZXJUcnVzdCBSb290";

  private static final String CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\n"
          + BODY_LINE_1
          + "\n"
          + BODY_LINE_2
          + "\n"
          + BODY_LINE_3
          + "\n-----END CERTIFICATE-----";

  private static final String FLATTENED =
      "-----BEGIN CERTIFICATE-----"
          + BODY_LINE_1
          + BODY_LINE_2
          + BODY_LINE_3
          + "-----END CERTIFICATE-----";

  @Test
  void shouldRestoreLineBreaksOfFlattenedPem() {
    assertThat(PemUtil.normalize(FLATTENED)).isEqualTo(CERTIFICATE);
  }

  @Test
  void shouldRestoreLineBreaksWhenBodyIsSeparatedBySpaces() {
    String spaced =
        "-----BEGIN CERTIFICATE----- "
            + BODY_LINE_1
            + " "
            + BODY_LINE_2
            + " "
            + BODY_LINE_3
            + " -----END CERTIFICATE-----";

    assertThat(PemUtil.normalize(spaced)).isEqualTo(CERTIFICATE);
  }

  @Test
  void shouldTreatLiteralBackslashNAsLineBreak() {
    String escaped = CERTIFICATE.replace("\n", "\\n");

    assertThat(PemUtil.normalize(escaped)).isEqualTo(CERTIFICATE);
  }

  @Test
  void shouldLeaveWellFormedPemUnchanged() {
    assertThat(PemUtil.normalize(CERTIFICATE)).isEqualTo(CERTIFICATE);
  }

  @Test
  void shouldBeIdempotent() {
    String once = PemUtil.normalize(FLATTENED);

    assertThat(PemUtil.normalize(once)).isEqualTo(once);
  }

  @Test
  void shouldTrimSurroundingWhitespace() {
    assertThat(PemUtil.normalize("  \n" + CERTIFICATE + "\n  ")).isEqualTo(CERTIFICATE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY", "PUBLIC KEY"})
  void shouldSupportAnyPemLabel(String label) {
    String flattened =
        "-----BEGIN " + label + "-----" + BODY_LINE_1 + BODY_LINE_2 + "-----END " + label + "-----";

    assertThat(PemUtil.normalize(flattened))
        .isEqualTo(
            "-----BEGIN "
                + label
                + "-----\n"
                + BODY_LINE_1
                + "\n"
                + BODY_LINE_2
                + "\n-----END "
                + label
                + "-----");
  }

  @Test
  void shouldKeepEveryCertificateOfAChain() {
    String chain = FLATTENED + FLATTENED;

    assertThat(PemUtil.normalize(chain)).isEqualTo(CERTIFICATE + "\n" + CERTIFICATE);
  }

  @Test
  void shouldNotTouchBlocksWithRfc1421Headers() {
    String encrypted =
        "-----BEGIN RSA PRIVATE KEY-----\n"
            + "Proc-Type: 4,ENCRYPTED\n"
            + "DEK-Info: AES-128-CBC,0123456789ABCDEF0123456789ABCDEF\n\n"
            + BODY_LINE_1
            + "\n-----END RSA PRIVATE KEY-----";

    assertThat(PemUtil.normalize(encrypted)).isEqualTo(encrypted);
  }

  @Test
  void shouldReturnInputWithoutPemBlockTrimmed() {
    assertThat(PemUtil.normalize("  not a pem  ")).isEqualTo("not a pem");
  }

  @Test
  void shouldReturnNullForNull() {
    assertThat(PemUtil.normalize(null)).isNull();
  }
}
