/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import static io.camunda.connector.aws.bedrock.BaseTest.readData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.aws.bedrock.model.BedrockContent;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.document.CamundaDocument;
import io.camunda.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;

@ExtendWith(MockitoExtension.class)
class BedrockContentMapperTest {

  @Mock private DocumentMapper documentMapper;

  @InjectMocks private BedrockContentMapper bedrockContentMapper;

  @Test
  void messageToBedrockContent() {
    String msg = "Hello World!";
    var bedrockContent = bedrockContentMapper.messageToBedrockContent(msg);

    assertThat(bedrockContent.getText()).isEqualTo(msg);
    assertThat(bedrockContent.getDocument()).isNull();
  }

  @Test
  void documentsToBedrockContent() throws IOException {
    String path1 = "src/test/resources/converse/text-document.json";
    String path2 = "src/test/resources/converse/image-document.json";
    var doc1 = prepareDocument(path1);
    var doc2 = prepareDocument(path2);

    List<BedrockContent> expectedContent =
        List.of(new BedrockContent(doc1), new BedrockContent(doc2));
    List<BedrockContent> contentResult =
        bedrockContentMapper.documentsToBedrockContent(List.of(doc1, doc2));

    assertThat(contentResult).isEqualTo(expectedContent);
  }

  @Test
  void documentToBedrockContent() throws IOException {
    String path = "src/test/resources/converse/text-document.json";
    var document = prepareDocument(path);

    var bedrockContent = bedrockContentMapper.documentToBedrockContent(document);

    assertThat(bedrockContent.getText()).isNull();
    assertThat(bedrockContent.getDocument()).isEqualTo(document);
  }

  @Test
  void mapToBedrockContent() {
    String msg = "Hello World!";
    var contentBlock = ContentBlock.fromText(msg);

    List<BedrockContent> result = bedrockContentMapper.mapToBedrockContent(List.of(contentBlock));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getText()).isEqualTo(msg);
  }

  @Test
  void mapToContentBlocks() throws IOException {
    String msg = "Hello World!";
    var textBedrockContent = new BedrockContent(msg);

    String path = "src/test/resources/converse/text-document.json";
    var documentReference = mock(DocumentReference.CamundaDocumentReference.class);
    var documentStore = mock(CamundaDocumentStore.class);
    var document = prepareDocument(path, documentReference, documentStore);

    var byteInput = new ByteArrayInputStream(new byte[0]);
    when(documentStore.getDocumentContent(any())).thenReturn(byteInput);

    var docContent = new BedrockContent(document);

    when(documentMapper.mapToFileBlock(any(Document.class))).thenCallRealMethod();

    List<ContentBlock> result =
        bedrockContentMapper.mapToContentBlocks(List.of(textBedrockContent, docContent));

    DocumentBlock documentBlock = (DocumentBlock) documentMapper.mapToFileBlock(document);
    ContentBlock documentContent = ContentBlock.fromDocument(documentBlock);

    ContentBlock textContent = ContentBlock.fromText(msg);

    List<ContentBlock> expected = List.of(textContent, documentContent);

    assertThat(result).isEqualTo(expected);
  }

  private Document prepareDocument(String path) throws IOException {
    var documentReference = mock(DocumentReference.CamundaDocumentReference.class);
    var documentStore = mock(CamundaDocumentStore.class);

    return prepareDocument(path, documentReference, documentStore);
  }

  private Document prepareDocument(
      String path, DocumentReference.CamundaDocumentReference docRef, CamundaDocumentStore docStore)
      throws IOException {
    var docMetadata = readData(path, DocumentReferenceModel.CamundaDocumentMetadataModel.class);
    return new CamundaDocument(docMetadata, docRef, docStore);
  }
}
