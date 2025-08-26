/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.api.model.File;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.util.json.GsonFactory;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.slack.outbound.caller.FileUploader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class BlockBuilder {
  private static final String REMOTE_ACCESS = "remote";
  private final List<LayoutBlock> layoutBlockList;
  private final FileUploader fileUploader;

  private BlockBuilder(List<LayoutBlock> layoutBlockList, FileUploader fileUploader) {
    this.layoutBlockList = layoutBlockList;
    this.fileUploader = fileUploader;
  }

  public static BlockBuilder create(FileUploader fileUploader) {
    return new BlockBuilder(new ArrayList<>(), fileUploader);
  }

  public BlockBuilder documents(List<Document> documents) {
    if (documents != null && !documents.isEmpty()) {
      List<File> files = fileUploader.uploadDocuments(documents);
      files.stream()
          .map(file -> FileBlock.builder().fileId(file.getId()).source(REMOTE_ACCESS).build())
          .forEach(this.layoutBlockList::add);
    }
    return this;
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
            .map(
                jsonNode ->
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
