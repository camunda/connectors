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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.api.client.http.GenericUrl;
import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortBlockTest {

  private Block multiPortBlock;
  private Block singlePortBlock;

  @BeforeEach
  public void setup() {
    multiPortBlock = PortBlock.create("8080,8081,8082", "TEST_MULTI_PORT");
    singlePortBlock = PortBlock.create("8080", "TEST_PORT");
  }

  @Test
  public void testMultiBlockedPortThrowsException() {
    GenericUrl url = new GenericUrl("http://example.com:8080");
    ConnectorInputException exception =
        assertThrows(ConnectorInputException.class, () -> multiPortBlock.validate(url));
    assert (exception.getMessage()).contains("Block Name: TEST_MULTI_PORT");
  }

  @Test
  public void testSingleBlockedPortThrowsException() {
    GenericUrl url = new GenericUrl("http://example.com:8080");
    ConnectorInputException exception =
        assertThrows(ConnectorInputException.class, () -> singlePortBlock.validate(url));
    assert (exception.getMessage()).contains("Block Name: TEST_PORT");
  }

  @Test
  public void testUnblockedPortDoesNotThrowException() {
    GenericUrl url = new GenericUrl("http://example.com:8083");
    assertDoesNotThrow(() -> multiPortBlock.validate(url));
    assertDoesNotThrow(() -> singlePortBlock.validate(url));
  }

  @Test
  public void testNoPortDoesNotThrowException() {
    GenericUrl url = new GenericUrl("http://example.com");
    assertDoesNotThrow(() -> multiPortBlock.validate(url));
    assertDoesNotThrow(() -> singlePortBlock.validate(url));
  }

  @Test
  public void testInvalidPortRangeThrowsException() {
    assertThrows(
        IllegalArgumentException.class, () -> PortBlock.create("-1,65536", "INVALID_RANGE"));
  }

  @Test
  public void testInvalidPortFormatThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> PortBlock.create("XYZ", "INVALID_FORMAT"));
  }

  @Test
  public void testBoundaryPorts() {
    Block boundaryBlock = PortBlock.create("0,65535", "BOUNDARY_PORT");
    GenericUrl low = new GenericUrl("http://example.com:0");
    GenericUrl high = new GenericUrl("http://example.com:65535");

    assertThrows(ConnectorInputException.class, () -> boundaryBlock.validate(low));
    assertThrows(ConnectorInputException.class, () -> boundaryBlock.validate(high));
  }

  @Test
  public void testNullBlockNameAllowed() {
    UrlBlock nullNameBlock = new UrlBlock("2020", null);
    GenericUrl url = new GenericUrl("http://example.com:8080");
    assertDoesNotThrow(() -> nullNameBlock.validate(url));
  }
}
