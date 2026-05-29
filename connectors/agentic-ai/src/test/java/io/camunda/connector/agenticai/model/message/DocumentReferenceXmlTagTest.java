/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag.CamundaDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag.ExternalDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag.GenericDocumentReferenceXmlTag;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentReferenceXmlTagTest {

  private static final String DOCUMENT_ID = "25ece9fa-aeea-423d-98ed-67c1f08b137b";

  @Mock private Document doc;
  @Mock private DocumentMetadata metadata;

  @Nested
  class CamundaDocumentReferenceTag {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CamundaDocumentReference ref;

    @BeforeEach
    void setUp() {
      when(doc.reference()).thenReturn(ref);
      when(doc.metadata()).thenReturn(metadata);
    }

    @Test
    void generatesFullTagWithAllAttributes() {
      when(ref.getDocumentId()).thenReturn(DOCUMENT_ID);
      when(ref.getStoreId()).thenReturn("in-memory");
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");

      var tag = DocumentReferenceXmlTag.from(doc, "call_abc", "search");
      assertThat(tag).isInstanceOf(CamundaDocumentReferenceXmlTag.class);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc toolName=\"search\" toolCallId=\"call_abc\" documentId=\"%s\" storeId=\"in-memory\" contentType=\"application/pdf\" fileName=\"report.pdf\" />"
                  .formatted(DOCUMENT_ID));
    }

    @Test
    void omitsBlankAttributes() {
      when(ref.getDocumentId()).thenReturn(DOCUMENT_ID);

      assertThat(DocumentReferenceXmlTag.from(doc).toXml())
          .isEqualTo("<doc documentId=\"%s\" />".formatted(DOCUMENT_ID));
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      assertThat(DocumentReferenceXmlTag.from(doc, "call_1", "tool<with\"quotes>").toXml())
          .isEqualTo("<doc toolName=\"tool&lt;with&quot;quotes&gt;\" toolCallId=\"call_1\" />");
    }
  }

  @Nested
  class ExternalDocumentReferenceTag {

    @Mock private ExternalDocumentReference ref;

    @BeforeEach
    void setUp() {
      when(doc.reference()).thenReturn(ref);
    }

    @Test
    void generatesFullTagWithAllAttributes() {
      when(ref.url()).thenReturn("https://example.com/report.pdf");
      when(ref.name()).thenReturn("Quarterly Report");

      var tag = DocumentReferenceXmlTag.from(doc, "call_abc", "search");
      assertThat(tag).isInstanceOf(ExternalDocumentReferenceXmlTag.class);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc toolName=\"search\" toolCallId=\"call_abc\" url=\"https://example.com/report.pdf\" name=\"Quarterly Report\" />");
    }

    @Test
    void omitsBlankNameAndToolContext() {
      when(ref.url()).thenReturn("https://example.com/report.pdf");

      assertThat(DocumentReferenceXmlTag.from(doc).toXml())
          .isEqualTo("<doc url=\"https://example.com/report.pdf\" />");
    }

    @Test
    void escapesSpecialCharactersInUrl() {
      when(ref.url()).thenReturn("https://example.com/path?q=a&b=\"c\"");

      assertThat(DocumentReferenceXmlTag.from(doc).toXml())
          .isEqualTo("<doc url=\"https://example.com/path?q=a&amp;b=&quot;c&quot;\" />");
    }
  }

  @Nested
  class GenericDocumentReferenceTag {

    /** Custom reference subtype that isn't recognized by the tag's dispatch switch. */
    private record CustomDocumentReference(String id) implements DocumentReference {}

    @Test
    void emitsToolContextForUnrecognizedReference() {
      when(doc.reference()).thenReturn(new CustomDocumentReference("custom-1"));

      var tag = DocumentReferenceXmlTag.from(doc, "call_1", "search");
      assertThat(tag).isInstanceOf(GenericDocumentReferenceXmlTag.class);
      assertThat(tag.toXml()).isEqualTo("<doc toolName=\"search\" toolCallId=\"call_1\" />");
    }

    @Test
    void emitsMinimalTagForUnrecognizedReferenceWithoutToolContext() {
      when(doc.reference()).thenReturn(new CustomDocumentReference("custom-1"));

      assertThat(DocumentReferenceXmlTag.from(doc).toXml()).isEqualTo("<doc />");
    }

    @Test
    void emitsMinimalTagForNullReference() {
      assertThat(DocumentReferenceXmlTag.from(doc).toXml()).isEqualTo("<doc />");
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      assertThat(DocumentReferenceXmlTag.from(doc, "call_1", "tool<with\"quotes>&'").toXml())
          .isEqualTo(
              "<doc toolName=\"tool&lt;with&quot;quotes&gt;&amp;&apos;\" toolCallId=\"call_1\" />");
    }
  }
}
