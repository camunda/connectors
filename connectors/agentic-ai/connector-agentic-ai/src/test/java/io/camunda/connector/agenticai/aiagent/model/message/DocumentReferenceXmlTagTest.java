/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link DocumentReferenceXmlTag}.
 *
 * <p>The tag now uses a single {@code id} attribute derived via {@link DocumentHandle#idFor} (not
 * {@code storeId}/{@code documentId}/{@code url}). Tool attribution ({@code toolName}/{@code
 * toolCallId}) is present only at Site 2 (the content-bearing user message), not at Site 1 (the
 * tool-call result text). Prompt/event documents use {@link DocumentReferenceXmlTag#from(Document)}
 * — no tool attribution.
 */
@ExtendWith(MockitoExtension.class)
class DocumentReferenceXmlTagTest {

  private static final String DOCUMENT_ID = "25ece9fa-aeea-423d-98ed-67c1f08b137b";

  @Mock private Document doc;
  @Mock private DocumentMetadata metadata;

  @Nested
  class CamundaDocumentTag {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CamundaDocumentReference ref;

    @BeforeEach
    void setUp() {
      when(doc.reference()).thenReturn(ref);
      when(doc.metadata()).thenReturn(metadata);
      when(ref.getDocumentId()).thenReturn(DOCUMENT_ID);
    }

    @Test
    void generatesTagWithIdFileNameContentType() {
      when(metadata.getContentType()).thenReturn("application/pdf");
      when(metadata.getFileName()).thenReturn("report.pdf");

      var tag = DocumentReferenceXmlTag.from(doc, "call_abc", "search");
      assertThat(tag.id()).isEqualTo(DOCUMENT_ID);
      assertThat(tag.toXml())
          .isEqualTo(
              "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call_abc\" />"
                  .formatted(DOCUMENT_ID));
    }

    @Test
    void omitsBlankOptionalAttributes() {
      // metadata present but fileName/contentType are blank
      when(metadata.getFileName()).thenReturn(null);
      when(metadata.getContentType()).thenReturn(null);

      assertThat(DocumentReferenceXmlTag.from(doc).toXml())
          .isEqualTo("<doc id=\"%s\" />".formatted(DOCUMENT_ID));
    }

    @Test
    void omitsToolAttributesWhenNotProvided() {
      when(metadata.getFileName()).thenReturn("file.txt");
      when(metadata.getContentType()).thenReturn("text/plain");

      assertThat(DocumentReferenceXmlTag.from(doc).toXml())
          .isEqualTo(
              "<doc id=\"%s\" fileName=\"file.txt\" contentType=\"text/plain\" />"
                  .formatted(DOCUMENT_ID));
    }

    @Test
    void escapesSpecialCharactersInToolName() {
      when(metadata.getFileName()).thenReturn(null);
      when(metadata.getContentType()).thenReturn(null);

      assertThat(DocumentReferenceXmlTag.from(doc, "call_1", "tool<with\"quotes>").toXml())
          .isEqualTo(
              "<doc id=\"%s\" toolName=\"tool&lt;with&quot;quotes&gt;\" toolCallId=\"call_1\" />"
                  .formatted(DOCUMENT_ID));
    }
  }

  @Nested
  class ExternalDocumentTag {

    @Mock private ExternalDocumentReference ref;

    @BeforeEach
    void setUp() {
      when(doc.reference()).thenReturn(ref);
    }

    @Test
    void generatesTagWithDerivedIdNoRawUrl() {
      when(ref.url()).thenReturn("https://example.com/report.pdf");
      // no metadata: external docs typically have no metadata
      when(doc.metadata()).thenReturn(null);

      var tag = DocumentReferenceXmlTag.from(doc, "call_abc", "search");
      // id is derived from the URL via DocumentHandle (ext-<sha256 prefix>)
      assertThat(tag.id()).startsWith("ext-");
      assertThat(tag.toXml())
          .contains("id=\"ext-")
          .contains("toolName=\"search\"")
          .contains("toolCallId=\"call_abc\"")
          // raw URL must NOT appear in the tag
          .doesNotContain("url=")
          .doesNotContain("https://example.com");
    }

    @Test
    void promptDocumentHasNoToolAttributes() {
      when(ref.url()).thenReturn("https://example.com/doc.pdf");
      when(doc.metadata()).thenReturn(null);

      var tag = DocumentReferenceXmlTag.from(doc);
      assertThat(tag.toXml())
          .contains("id=\"ext-")
          .doesNotContain("toolName=")
          .doesNotContain("toolCallId=")
          .doesNotContain("url=");
    }
  }

  @Nested
  class NullDocumentMetadata {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CamundaDocumentReference ref;

    @Test
    void toleratesNullMetadata() {
      when(doc.reference()).thenReturn(ref);
      when(doc.metadata()).thenReturn(null);
      when(ref.getDocumentId()).thenReturn(DOCUMENT_ID);

      var tag = DocumentReferenceXmlTag.from(doc);
      assertThat(tag.fileName()).isNull();
      assertThat(tag.contentType()).isNull();
      assertThat(tag.toXml()).isEqualTo("<doc id=\"%s\" />".formatted(DOCUMENT_ID));
    }
  }
}
