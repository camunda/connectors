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

import io.camunda.connector.agenticai.model.message.DocumentXmlTag.CamundaDocumentXmlTag;
import io.camunda.connector.agenticai.model.message.DocumentXmlTag.ExternalDocumentXmlTag;
import io.camunda.connector.agenticai.model.message.DocumentXmlTag.GenericDocumentXmlTag;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DocumentXmlTagTest {

  @Nested
  class CamundaDocumentReferenceTag {

    @Test
    void generatesFullTagWithAllAttributes() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("25ece9fa-aeea-423d-98ed-67c1f08b137b");
      when(ref.getStoreId()).thenReturn("in-memory");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");

      var tag = DocumentXmlTag.from(doc, "call_abc", "search");
      assertThat(tag).isInstanceOf(CamundaDocumentXmlTag.class);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc toolName=\"search\" toolCallId=\"call_abc\" documentId=\"25ece9fa-aeea-423d-98ed-67c1f08b137b\" storeId=\"in-memory\" contentType=\"application/pdf\" fileName=\"report.pdf\" />");
    }

    @Test
    void omitsBlankAttributes() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("f7b3a1d0-1234-5678-9abc-def012345678");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<doc documentId=\"f7b3a1d0-1234-5678-9abc-def012345678\" />");
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      var doc = mock(Document.class);
      var ref = mock(CamundaDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.getDocumentId()).thenReturn("abc12345-0000-0000-0000-000000000000");

      assertThat(DocumentXmlTag.from(doc, "call_1", "tool<with\"quotes>").toXml())
          .isEqualTo(
              "<doc toolName=\"tool&lt;with&quot;quotes&gt;\" toolCallId=\"call_1\" documentId=\"abc12345-0000-0000-0000-000000000000\" />");
    }
  }

  @Nested
  class ExternalDocumentReferenceTag {

    @Test
    void generatesFullTagWithAllAttributes() {
      var doc = mock(Document.class);
      var ref = mock(ExternalDocumentReference.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.url()).thenReturn("https://example.com/report.pdf");
      when(ref.name()).thenReturn("Quarterly Report");
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");

      var tag = DocumentXmlTag.from(doc, "call_abc", "search");
      assertThat(tag).isInstanceOf(ExternalDocumentXmlTag.class);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc toolName=\"search\" toolCallId=\"call_abc\" url=\"https://example.com/report.pdf\" name=\"Quarterly Report\" contentType=\"application/pdf\" fileName=\"report.pdf\" />");
    }

    @Test
    void omitsBlankNameAndToolContext() {
      var doc = mock(Document.class);
      var ref = mock(ExternalDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.url()).thenReturn("https://example.com/report.pdf");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<doc url=\"https://example.com/report.pdf\" />");
    }

    @Test
    void escapesSpecialCharactersInUrl() {
      var doc = mock(Document.class);
      var ref = mock(ExternalDocumentReference.class);
      when(doc.reference()).thenReturn(ref);
      when(ref.url()).thenReturn("https://example.com/path?q=a&b=\"c\"");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<doc url=\"https://example.com/path?q=a&amp;b=&quot;c&quot;\" />");
    }
  }

  @Nested
  class GenericDocumentReferenceTag {

    @Test
    void emitsFileNameAndToolContextForInlineReference() {
      var doc = mock(Document.class);
      var ref = mock(InlineDocumentReference.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.reference()).thenReturn(ref);
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getContentType()).thenReturn("text/plain");
      when(metadata.getFileName()).thenReturn("inline.txt");

      var tag = DocumentXmlTag.from(doc, "call_1", "search");
      assertThat(tag).isInstanceOf(GenericDocumentXmlTag.class);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc toolName=\"search\" toolCallId=\"call_1\" contentType=\"text/plain\" fileName=\"inline.txt\" />");
    }

    @Test
    void emitsMinimalTagForUnrecognizedReferenceWithoutMetadata() {
      var doc = mock(Document.class);
      var ref = mock(InlineDocumentReference.class);
      when(doc.reference()).thenReturn(ref);

      assertThat(DocumentXmlTag.from(doc).toXml()).isEqualTo("<doc />");
    }

    @Test
    void emitsMinimalTagForNullReference() {
      var doc = mock(Document.class);
      assertThat(DocumentXmlTag.from(doc).toXml()).isEqualTo("<doc />");
    }

    @Test
    void escapesSpecialCharactersInFileName() {
      var doc = mock(Document.class);
      var metadata = mock(DocumentMetadata.class);
      when(doc.metadata()).thenReturn(metadata);
      when(metadata.getFileName()).thenReturn("file\"with<special>&chars'.pdf");

      assertThat(DocumentXmlTag.from(doc).toXml())
          .isEqualTo("<doc fileName=\"file&quot;with&lt;special&gt;&amp;chars&apos;.pdf\" />");
    }
  }
}
