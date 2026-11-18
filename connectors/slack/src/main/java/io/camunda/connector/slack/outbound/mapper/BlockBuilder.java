/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.util.json.GsonFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class BlockBuilder {
  private final List<LayoutBlock> layoutBlockList;

  private BlockBuilder(List<LayoutBlock> layoutBlockList) {
    this.layoutBlockList = layoutBlockList;
  }

  public static BlockBuilder create() {
    return new BlockBuilder(new ArrayList<>());
  }

  public BlockBuilder text(String text) {
    if (text != null && !text.isEmpty()) {
      this.layoutBlockList.add(
          SectionBlock.builder().text(MarkdownTextObject.builder().text(text).build()).build());
    }
    return this;
  }

  public BlockBuilder blockContent(JsonNode blockContent) {
    if (blockContent != null && !blockContent.isNull()) {
      if (blockContent instanceof ArrayNode arrayNode) {
        StreamSupport.stream(arrayNode.spliterator(), false)
            .map(jsonNode ->
                GsonFactory.createSnakeCase().fromJson(jsonNode.toString(), LayoutBlock.class))
            .forEach(this.layoutBlockList::add);
      } else throw new ConnectorInputException("Block section must be an array");
    }
    return this;
  }

  public List<LayoutBlock> getLayoutBlocks() {
    return this.layoutBlockList;
  }
}
