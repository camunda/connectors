/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slack.api.model.File;
import com.slack.api.model.block.FileBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import io.camunda.connector.slack.outbound.caller.FileUploader;
import io.camunda.document.CamundaDocument;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockBuilderTest {

  private FileUploader fileUploader;
  private BlockBuilder blockBuilder;

  @BeforeEach
  void setup() {
    fileUploader = mock(FileUploader.class);
    blockBuilder = BlockBuilder.create(fileUploader);
  }

  @Test
  void shouldAddTextBlock() {
    String text = "Hello world";

    List<LayoutBlock> blocks = blockBuilder.text(text).getLayoutBlocks();

    assertEquals(1, blocks.size());
    assertInstanceOf(SectionBlock.class, blocks.getFirst());

    SectionBlock section = (SectionBlock) blocks.getFirst();
    MarkdownTextObject textObj = (MarkdownTextObject) section.getText();
    assertEquals(text, textObj.getText());
  }

  @Test
  void shouldAddDocumentFileBlocks() {
    // Given
    File file = new File();
    file.setId("file123");

    List<File> uploadedFiles = List.of(file);
    when(fileUploader.uploadDocuments(any())).thenReturn(uploadedFiles);

    CamundaDocumentStore store = InMemoryDocumentStore.INSTANCE;
    DocumentReference.CamundaDocumentReference documentReference =
        store.createDocument(DocumentCreationRequest.from(new byte[] {1, 2, 3}).build());

    Document document =
        new CamundaDocument(documentReference.getMetadata(), documentReference, store);
    List<LayoutBlock> layoutBlocks = blockBuilder.documents(List.of(document)).getLayoutBlocks();

    // Then
    assertEquals(1, layoutBlocks.size());
    assertEquals("file123", ((FileBlock) layoutBlocks.getFirst()).getFileId());
  }

  @Test
  void shouldParseJsonBlocks() throws Exception {
    // Given
    ObjectMapper mapper = new ObjectMapper();
    String json =
        """
      [
        {
          "type": "section",
          "text": {
            "type": "mrkdwn",
            "text": "Block from JSON"
          }
        }
      ]
      """;
    ArrayNode arrayNode = (ArrayNode) mapper.readTree(json);

    // When
    blockBuilder.blockContent(arrayNode);

    // Then
    List<LayoutBlock> blocks = blockBuilder.getLayoutBlocks();
    assertEquals(1, blocks.size());
    assertInstanceOf(SectionBlock.class, blocks.getFirst());
  }

  @Test
  void shouldThrowOnInvalidBlockContent() {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.createObjectNode();
    assertThrows(RuntimeException.class, () -> blockBuilder.blockContent(node));
  }
}
