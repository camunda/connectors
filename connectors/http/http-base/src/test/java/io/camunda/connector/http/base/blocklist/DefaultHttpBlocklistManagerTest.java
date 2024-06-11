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
package io.camunda.connector.http.base.blocklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.base.blocklist.block.Block;
import io.camunda.connector.http.base.blocklist.block.PortBlock;
import io.camunda.connector.http.base.blocklist.block.RegexBlock;
import io.camunda.connector.http.base.blocklist.block.UrlBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class DefaultHttpBlocklistManagerTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://blocked.url/metadata",
        "https://blocked.url/other",
        "https://some.url:8081",
        "http://regex.some.url"
      })
  public void testUrlBlocked(String blockedUrl) {
    // given
    var mockEnv =
        Map.of(
            "CAMUNDA_CONNECTOR_HTTP_BLOCK_URL_TEST_META", "http://blocked.url/metadata",
            "CAMUNDA_CONNECTOR_HTTP_BLOCK_URL_DUPLICATE_TEST", "https://blocked.url/other",
            "CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_TEST_PORT", "8080,8081,8082",
            "CAMUNDA_CONNECTOR_HTTP_BLOCK_REGEX_TEST_REGEX", "^http://regex.*");

    HttpBlockListManager manager = new DefaultHttpBlocklistManager(mockEnv);
    // when and then
    assertThatThrownBy(() -> manager.validateUrlAgainstBlocklist(blockedUrl))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  public void testInvalidPortInEnvironment() {
    // given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_TEST_INVALID_PORT", "not_a_port");
    // when
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // then
    assertThat(blockList).isEmpty();
  }

  @Test
  public void testInvalidRegexInEnvironment() {
    // given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_REGEX_TEST_INVALID_REGEX", "[invalidRegex");
    // when
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // then
    assertThat(blockList).isEmpty();
  }

  @Test
  public void testInvalidPrefix() {
    // Given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put("INVALID_PREFIX_URL_TEST", "https://blocked.url/metadata");
    // When
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // Then
    assertThat(blockList).isEmpty();
  }

  @Test
  public void testInvalidType() {
    // Given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put(
        "CAMUNDA_CONNECTOR_HTTP_BLOCK_INVALID_TYPE", "https://blocked.url/metadata");
    // When
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // Then
    assertThat(blockList).isEmpty();
  }

  @Test
  public void testMultipleBlocksOfSameType() {
    // given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_MULTIPLE_BLOCKS", "8080,8081");
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_ANOTHER_BLOCK", "8082");
    // when
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // then
    List<Block> portBlocks = filterBlocksByType(blockList, PortBlock.class);
    assertThat(portBlocks).hasSize(2);
  }

  @Test
  public void testCorrectParsingOfBlocklist() {
    // given
    Map<String, String> mockEnvironment = new HashMap<>();
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_URL_TEST_URL", "http://blocked.url/metadata");
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_TEST_PORT", "8080,8081");
    mockEnvironment.put("CAMUNDA_CONNECTOR_HTTP_BLOCK_REGEX_TEST_REGEX", "^http://regex.*");
    // when
    DefaultHttpBlocklistManager blocklistManager = new DefaultHttpBlocklistManager(mockEnvironment);
    List<Block> blockList = blocklistManager.getBlockList();
    // then
    assertThat(filterBlocksByType(blockList, UrlBlock.class)).hasSize(1);
    assertThat(filterBlocksByType(blockList, PortBlock.class)).hasSize(1);
    assertThat(filterBlocksByType(blockList, RegexBlock.class)).hasSize(1);

    UrlBlock block = (UrlBlock) filterBlocksByType(blockList, UrlBlock.class).get(0);
    assertThat(block.blockName()).isEqualTo("TEST_URL");
    assertThat(block.value()).isEqualTo("http://blocked.url/metadata");
  }

  private <T extends Block> List<Block> filterBlocksByType(List<Block> blocks, Class<T> type) {
    return blocks.stream().filter(type::isInstance).collect(Collectors.toList());
  }
}
