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
package io.camunda.connector.http.base.blocklist.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RegexBlockTest {

  private Block httpRegexBlock;
  private Block specificDomainRegexBlock;

  @BeforeEach
  public void setup() {
    httpRegexBlock = RegexBlock.create("http.*", "HTTP_BLOCK");
    specificDomainRegexBlock = RegexBlock.create("http://example\\.com/.*", "SPECIFIC_DOMAIN");
  }

  @Test
  public void testHttpBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class, () -> httpRegexBlock.validate("http://example.com"));
    assertThat(exception.getMessage()).contains("Block Name: HTTP_BLOCK");
  }

  @Test
  public void testSpecificDomainBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> specificDomainRegexBlock.validate("http://example.com/path"));
    assertThat(exception.getMessage()).contains("Block Name: SPECIFIC_DOMAIN");
  }

  @Test
  public void testSpecificDomainBlockDoesNotThrowException() {
    assertDoesNotThrow(() -> specificDomainRegexBlock.validate("http://another.com"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://metadata.google.internal/computeMetadata/v1/",
        "http://metadata.google.internal/computeMetadata/v2/"
      })
  public void testComputeMetadataBlockThrowsException(String urlString) {
    Block computeMetadataBlock = RegexBlock.create(".*computeMetadata.*", "COMPUTE_METADATA_BLOCK");
    ConnectorInputException exception =
        assertThrows(ConnectorInputException.class, () -> computeMetadataBlock.validate(urlString));
    assertTrue(exception.getMessage().contains("Block Name: COMPUTE_METADATA_BLOCK"));
  }

  @Test
  public void testInvalidRegexThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RegexBlock.create("[invalidRegex", "TEST_INVALID_REGEX"));
  }

  @Test
  public void testNullBlockNameAllowed() {
    UrlBlock nullNameBlock = new UrlBlock("ignore", null);
    assertDoesNotThrow(() -> nullNameBlock.validate("http://example.com:8080"));
  }
}
