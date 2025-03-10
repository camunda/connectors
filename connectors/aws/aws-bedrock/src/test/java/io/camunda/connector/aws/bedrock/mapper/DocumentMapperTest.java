/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import static io.camunda.connector.aws.bedrock.BaseTest.readData;
import static io.camunda.connector.aws.bedrock.mapper.DocumentMapper.UNSUPPORTED_DOC_TYPE_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.document.CamundaDocument;
import io.camunda.document.Document;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.*;

@ExtendWith(MockitoExtension.class)
class DocumentMapperTest {

  private final DocumentMapper documentMapper = new DocumentMapper();
  @Mock private DocumentReference.CamundaDocumentReference documentReference;
  @Mock private CamundaDocumentStore documentStore;

  @Test
  void mapToFileBlockShouldCreateDocumentBlock() throws IOException {
    String path = "src/test/resources/converse/text-document.json";
    var document = prepareDocument(path);

    var byteInput = new ByteArrayInputStream(new byte[0]);
    when(documentStore.getDocumentContent(any())).thenReturn(byteInput);

    String fileName =
        ((DocumentReferenceModel.CamundaDocumentMetadataModel) document.metadata()).fileName();
    var expectedDocBlock =
        DocumentBlock.builder()
            .source(
                DocumentSource.builder()
                    .bytes(SdkBytes.fromByteArray(byteInput.readAllBytes()))
                    .build())
            .format(DocumentFormat.TXT)
            .name(fileName.split("\\.")[0])
            .build();

    var documentBlockResult = documentMapper.mapToFileBlock(document);

    assertThat(documentBlockResult).isEqualTo(expectedDocBlock);
  }

  @Test
  void mapToFileShouldCreateImageBlock() throws IOException {
    String path = "src/test/resources/converse/image-document.json";
    var document = prepareDocument(path);

    var byteInput = new ByteArrayInputStream(new byte[0]);
    when(documentStore.getDocumentContent(any())).thenReturn(byteInput);

    var imageBlockResult = documentMapper.mapToFileBlock(document);

    var expectedImageBlock =
        ImageBlock.builder()
            .source(
                ImageSource.builder()
                    .bytes(SdkBytes.fromByteArray(byteInput.readAllBytes()))
                    .build())
            .format(ImageFormat.PNG)
            .build();

    assertThat(imageBlockResult).isEqualTo(expectedImageBlock);
  }

  @Test
  void mapToFileWithWithUnknownFileTypeShouldThrowException() throws IOException {
    String path = "src/test/resources/converse/unsupported-document.json";
    var document = prepareDocument(path);

    var byteInput = new ByteArrayInputStream(new byte[0]);
    when(documentStore.getDocumentContent(any())).thenReturn(byteInput);

    var ex =
        assertThrows(IllegalArgumentException.class, () -> documentMapper.mapToFileBlock(document));

    assertThat(ex).hasMessageContaining(UNSUPPORTED_DOC_TYPE_MSG);
  }

  private Document prepareDocument(String path) throws IOException {
    var docMetadata = readData(path, DocumentReferenceModel.CamundaDocumentMetadataModel.class);
    return new CamundaDocument(docMetadata, documentReference, documentStore);
  }
}
