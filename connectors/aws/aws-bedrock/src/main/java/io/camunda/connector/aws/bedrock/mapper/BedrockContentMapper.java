/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.aws.bedrock.model.BedrockContent;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;

public class BedrockContentMapper {

  private final DocumentMapper documentMapper;

  public BedrockContentMapper(DocumentMapper documentMapper) {
    this.documentMapper = documentMapper;
  }

  public BedrockContent messageToBedrockContent(String message) {
    return new BedrockContent(message);
  }

  public List<BedrockContent> documentsToBedrockContent(List<Document> documents) {
    if (documents == null) {
      return List.of();
    }
    return documents.stream().map(this::documentToBedrockContent).toList();
  }

  public BedrockContent documentToBedrockContent(Document document) {
    return new BedrockContent(document);
  }

  /*
   * The current implementation supports ContentBlock containing only text.
   * */
  public List<BedrockContent> mapToBedrockContent(List<ContentBlock> contentBlocks) {
    return contentBlocks.stream().map(ContentBlock::text).map(BedrockContent::new).toList();
  }

  public List<ContentBlock> mapToContentBlocks(List<BedrockContent> contentBlocks) {
    return contentBlocks.stream()
        .map(
            content -> {
              String text = content.getText();
              if (text != null) {
                return mapToContentBlock(text);
              }
              var document = content.getDocument();
              var docBlock = documentMapper.mapToFileBlock(document);
              return mapToContentBlock(docBlock);
            })
        .toList();
  }

  private ContentBlock mapToContentBlock(Object content) {
    return switch (content) {
      case String s -> ContentBlock.fromText(s);
      case ImageBlock imageBlock -> ContentBlock.fromImage(imageBlock);
      default -> ContentBlock.fromDocument((DocumentBlock) content);
    };
  }

  public DocumentMapper getDocumentMapper() {
    return documentMapper;
  }
}
