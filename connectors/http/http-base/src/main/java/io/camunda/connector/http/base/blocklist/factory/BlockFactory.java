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
package io.camunda.connector.http.base.blocklist.factory;

import io.camunda.connector.http.base.blocklist.block.Block;
import io.camunda.connector.http.base.blocklist.block.PortBlock;
import io.camunda.connector.http.base.blocklist.block.RegexBlock;
import io.camunda.connector.http.base.blocklist.block.UrlBlock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockFactory {
  private static final Logger logger = LoggerFactory.getLogger(BlockFactory.class);

  private static final String URL_TYPE = "URL";
  private static final String PORT_TYPE = "PORT";
  private static final String REGEX_TYPE = "REGEX";

  private BlockFactory() {}

  public static Block createBlock(String blockType, String blockRule, String blockName) {
    Objects.requireNonNull(blockType, "value must not be null");
    Objects.requireNonNull(blockRule, "value must not be null");

    return switch (blockType) {
      case URL_TYPE -> new UrlBlock(blockRule, blockName);
      case PORT_TYPE -> PortBlock.create(blockRule, blockName);
      case REGEX_TYPE -> RegexBlock.create(blockRule, blockName);
      default -> {
        logger.warn("Unknown block type: {}", blockType);
        throw new IllegalArgumentException("Unknown block type: " + blockType);
      }
    };
  }
}
