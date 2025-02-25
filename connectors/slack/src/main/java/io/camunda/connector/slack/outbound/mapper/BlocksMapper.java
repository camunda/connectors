/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.mapper;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.api.model.File;
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.FileBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.util.json.GsonFactory;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlocksMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlocksMapper.class);
  public static final String REMOTE_FILE_SOURCE = "remote";

  private BlocksMapper() {}

  public static List<LayoutBlock> mapBlocks(List<File> files, String text, JsonNode blockContent) {
    List<LayoutBlock> blocks = mapBlocks(text, blockContent);
    blocks.addAll(prepareFileBlocks(files));
    return blocks;
  }

  public static List<LayoutBlock> mapBlocks(String text, JsonNode blockContent) {
    List<LayoutBlock> blocks = new ArrayList<>();
    if (blockContent != null) {
      blocks = parseBlockContent(blockContent);
    } else {
      blocks.add(prepareBlockFromMessage(text));
    }

    return blocks;
  }

  private static LayoutBlock prepareBlockFromMessage(String text) {
    return ContextBlock.builder()
        .elements(List.of(PlainTextObject.builder().text(text).build()))
        .build();
  }

  private static List<LayoutBlock> prepareFileBlocks(List<File> files) {
    return files.stream()
        .map(file -> FileBlock.builder().fileId(file.getId()).source(REMOTE_FILE_SOURCE).build())
        .collect(toList());
  }

  private static List<LayoutBlock> parseBlockContent(JsonNode blockContent) {
    if (!blockContent.isArray()) {
      String msg = "Block section must be an array";
      LOGGER.warn(msg);
      throw new ConnectorException(msg);
    }

    ArrayNode arrayNode = (ArrayNode) blockContent;
    List<LayoutBlock> blocks = new ArrayList<>();
    for (JsonNode node : arrayNode) {
      blocks.add(GsonFactory.createSnakeCase().fromJson(node.toString(), LayoutBlock.class));
    }
    return blocks;
  }
}
