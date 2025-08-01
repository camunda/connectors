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
package io.camunda.connector.http.client.blocklist.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UrlBlockTest {

  private UrlBlock specificUrlBlock;
  private UrlBlock commonUrlBlock;
  private UrlBlock domainUrlBlock;
  private UrlBlock stringBlock;

  @BeforeEach
  public void setup() {
    specificUrlBlock = new UrlBlock("http://example.com/specificPath", "SPECIFIC_URL");
    commonUrlBlock = new UrlBlock("http://example.com", "COMMON_URL");
    domainUrlBlock = new UrlBlock("blocked.com", "DOMAIN_BLOCK");
    stringBlock = new UrlBlock("forbiddenString", "STRING_BLOCK");
  }

  @Test
  public void testSpecificUrlBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> specificUrlBlock.validate("http://example.com/specificPath"));
    assertThat(exception.getMessage()).contains("Block Name: SPECIFIC_URL");
  }

  @Test
  public void testCommonUrlBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> commonUrlBlock.validate("http://example.com/somePath"));
    assertThat(exception.getMessage()).contains("Block Name: COMMON_URL");
  }

  @Test
  public void testDomainBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> domainUrlBlock.validate("http://blocked.com/somePath"));
    assertThat(exception.getMessage()).contains("Block Name: DOMAIN_BLOCK");
  }

  @Test
  public void testStringBlockThrowsException() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> stringBlock.validate("http://example.com/forbiddenString/somePath"));
    assertThat(exception.getMessage()).contains("Block Name: STRING_BLOCK");
  }

  @Test
  public void testSpecificUrlBlockDoesNotThrowException() {
    assertDoesNotThrow(() -> specificUrlBlock.validate("http://another.com/specificPath"));
  }

  @Test
  public void testNullBlockNameAllowed() {
    UrlBlock nullNameBlock = new UrlBlock("ignore", null);
    assertDoesNotThrow(() -> nullNameBlock.validate("http://example.com/nullBlock"));
  }
}
