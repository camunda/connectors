/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DocumentXmlTagTest {

  @Nested
  class ToXml {

    @Test
    void generatesFullTagWithAllAttributes() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("25ece9fa-aeea-423d-98ed-67c1f08b137b");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn("report.pdf");

      assertThat(DocumentXmlTag.from(doc, "search", "call_abc").toXml())
          .isEqualTo(
              "<document tool-name=\"search\" tool-call-id=\"call_abc\" document-short-id=\"25ece9fa\" filename=\"report.pdf\" />");
    }

    @Test
    void generatesTagWithoutToolAndCallId() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("f7b3a1d0-1234-5678-9abc-def012345678");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn(null);

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<document document-short-id=\"f7b3a1d0\" />");
    }

    @Test
    void generatesMinimalTagForMockedDocument() {
      var doc = mock(Document.class);
      assertThat(DocumentXmlTag.from(doc).toXml()).isEqualTo("<document />");
    }

    @Test
    void handlesDocumentIdWithoutDash() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("simpledocid");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<document document-short-id=\"simpledocid\" />");
    }

    @Test
    void escapesSpecialCharactersInFilename() {
      var doc = mock(Document.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn("file\"with<special>&chars'.pdf");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<document filename=\"file&quot;with&lt;special&gt;&amp;chars&apos;.pdf\" />");
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("abc12345-0000-0000-0000-000000000000");

      assertThat(DocumentXmlTag.from(doc, "tool<with\"quotes>", "call_1").toXml())
          .isEqualTo(
              "<document tool-name=\"tool&lt;with&quot;quotes&gt;\" tool-call-id=\"call_1\" document-short-id=\"abc12345\" />");
    }
  }
}
