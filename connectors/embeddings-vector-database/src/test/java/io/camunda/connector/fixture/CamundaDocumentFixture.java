/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.mockito.Mockito;

public class CamundaDocumentFixture {

  private static final String DOCS_PATH = "src/test/resources/testfiles/";
  private static final String TXT_FILENAME = "rag.txt";
  private static final String PDF_FILENAME = "test.pdf";

  public static Document inMemoryTxtDocument() {
    return inMemoryDocument(DOCS_PATH + TXT_FILENAME, TXT_FILENAME);
  }

  public static final Document inMemoryPdfDocument() {
    return inMemoryDocument(DOCS_PATH + PDF_FILENAME, PDF_FILENAME);
  }

  private static Document inMemoryDocument(final String filePath, final String fileName) {
    try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
      final var text = IOUtils.toString(fileInputStream, StandardCharsets.UTF_8);
      return new Document() {
        @Override
        public DocumentMetadata metadata() {
          final var metadata = Mockito.mock(DocumentMetadata.class);
          Mockito.when(metadata.getFileName()).thenReturn(fileName);
          return metadata;
        }

        @Override
        public String asBase64() {
          return new Base64().encodeToString(text.getBytes());
        }

        @Override
        public InputStream asInputStream() {
          return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public byte[] asByteArray() {
          return text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public DocumentReference reference() {
          return Mockito.mock(DocumentReference.class);
        }

        @Override
        public String generateLink(DocumentLinkParameters parameters) {
          return "https://doc.link.com";
        }
      };
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
