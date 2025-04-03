/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.document.Document;
import io.camunda.document.DocumentLinkParameters;
import io.camunda.document.reference.DocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.mockito.Mockito;

public class CamundaDocumentFixture {

  public static Document inMemoryDocument() {
    final var text =
        "RAG stands for Retrieval-Augmented Generation, "
            + "an AI framework that combines information retrieval with "
            + "large language models (LLMs). RAG helps LLMs produce more "
            + "accurate, relevant, and up-to-date responses ";

    return new Document() {
      @Override
      public DocumentMetadata metadata() {
        final var metadata = Mockito.mock(DocumentMetadata.class);
        Mockito.when(metadata.getFileName()).thenReturn("rag.txt");
        return metadata;
      }

      @Override
      public String asBase64() {
        return "UkFHIHN0YW5kcyBmb3IgUmV0cmlldmFsLUF1Z21lbnRlZCBHZW5lcmF0aW9uLCBhbiBBSSBmcmFtZXdvcmsgdGhhdCBjb21iaW5lcyBpbmZvcm1hdGlvbiByZXRyaWV2YWwgd2l0aCBsYXJnZSBsYW5ndWFnZSBtb2RlbHMgKExMTXMpLiBSQUcgaGVscHMgTExNcyBwcm9kdWNlIG1vcmUgYWNjdXJhdGUsIHJlbGV2YW50LCBhbmQgdXAtdG8tZGF0ZSByZXNwb25zZXM=";
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
  }
}
