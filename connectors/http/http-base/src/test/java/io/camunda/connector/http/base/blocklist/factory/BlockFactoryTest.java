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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.http.base.blocklist.block.Block;
import io.camunda.connector.http.base.blocklist.block.PortBlock;
import io.camunda.connector.http.base.blocklist.block.RegexBlock;
import io.camunda.connector.http.base.blocklist.block.UrlBlock;
import org.junit.jupiter.api.Test;

public class BlockFactoryTest {

  @Test
  public void testCreateUrlBlock() {
    Block block = BlockFactory.createBlock("URL", "http://example.com", "URL_BLOCK");
    assertThat(block).isInstanceOf(UrlBlock.class);
    UrlBlock urlBlock = (UrlBlock) block;
    assertThat(urlBlock.value()).isEqualTo("http://example.com");
    assertThat(urlBlock.blockName()).isEqualTo("URL_BLOCK");
  }

  @Test
  public void testCreatePortBlock() {
    Block block = BlockFactory.createBlock("PORT", "8080", "PORT_BLOCK");
    assertThat(block).isInstanceOf(PortBlock.class);
    PortBlock portBlock = (PortBlock) block;
    assertThat(portBlock.blockedPorts()).contains(8080);
    assertThat(portBlock.blockName()).isEqualTo("PORT_BLOCK");
  }

  @Test
  public void testCreateRegexBlock() {
    Block block = BlockFactory.createBlock("REGEX", "http.*", "REGEX_BLOCK");
    assertThat(block).isInstanceOf(RegexBlock.class);
    RegexBlock regexBlock = (RegexBlock) block;
    assertThat(regexBlock.pattern().toString()).isEqualTo("http.*");
    assertThat(regexBlock.blockName()).isEqualTo("REGEX_BLOCK");
  }

  @Test
  public void testCreateUnknownBlock() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BlockFactory.createBlock("UNKNOWN", "value", "UNKNOWN_BLOCK"));
  }

  @Test
  public void testCreateBlockWithNullType() {
    assertThrows(
        NullPointerException.class, () -> BlockFactory.createBlock(null, "value", "NULL_BLOCK"));
  }
}
