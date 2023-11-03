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

import com.google.api.client.http.GenericUrl;
import io.camunda.connector.http.base.blocklist.block.Block;
import io.camunda.connector.http.base.blocklist.factory.BlockFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpBlocklistManager implements HttpBlockListManager {
  private static final Logger logger = LoggerFactory.getLogger(DefaultHttpBlocklistManager.class);

  private static final String BLOCK_PREFIX = "CAMUNDA_CONNECTOR_HTTP_BLOCK_";

  private final List<Block> blockList;

  public DefaultHttpBlocklistManager() {
    this(System.getenv());
  }

  //  Constructor that accepts a custom environment map, primarily for testing
  public DefaultHttpBlocklistManager(Map<String, String> environment) {
    this.blockList = loadBlocklistFromEnv(environment);
  }

  private List<Block> loadBlocklistFromEnv(final Map<String, String> environment) {
    return environment.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(BLOCK_PREFIX))
        .map(this::createBlockFromEnvEntry)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Block createBlockFromEnvEntry(Map.Entry<String, String> entry) {
    var remaining = entry.getKey().substring(BLOCK_PREFIX.length());
    // Splits 'remaining' into blockType and optional blockName
    // Example: "TYPE_NAME" splits into parts[0] = "TYPE", parts[1] = "NAME"
    var parts = remaining.split("_", 2);
    var blockType = parts[0]; // The block type.
    var blockName = parts.length > 1 ? parts[1] : ""; // The block name, if specified.
    try {
      return BlockFactory.createBlock(blockType, entry.getValue(), blockName);
    } catch (Exception e) {
      logger.warn("Failed to create block of type {}: {}", blockType, e.getMessage());
    }
    return null;
  }

  public void validateUrlAgainstBlocklist(GenericUrl url) {
    for (Block block : blockList) {
      block.validate(url);
    }
  }

  public List<Block> getBlockList() {
    return blockList;
  }
}
