/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.DataPart;
import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentConversionException;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class A2aDocumentToPartConverterTest {

  private final A2aDocumentToPartConverter converter =
      new A2aDocumentToPartConverterImpl(new ObjectMapper());

  private static final String TEXT_FILE_CONTENT = "Lorem ipsum dolor sit amet.";
  private static final String DUMMY_B64_VALUE = "dGVzdA==";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Document document;

  @Test
  void convertsPlainTextToTextPart() {
    when(document.metadata().getContentType()).thenReturn("text/plain");
    when(document.asByteArray()).thenReturn(TEXT_FILE_CONTENT.getBytes());

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(TextPart.class))
        .satisfies(textPart -> assertThat(textPart.getText()).isEqualTo(TEXT_FILE_CONTENT));

    verify(document, never()).asBase64();
    verify(document, never()).asInputStream();
  }

  @ParameterizedTest
  @CsvSource({
    // text/* types
    "text/csv",
    "text/my-custom-format",
    "text/xml",

    // defined application/* types
    "application/xml",
    "application/yaml",
  })
  void convertsOtherTextTypesToTextContent(String mediaType) {
    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asByteArray()).thenReturn(TEXT_FILE_CONTENT.getBytes());

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(TextPart.class))
        .satisfies(textPart -> assertThat(textPart.getText()).isEqualTo(TEXT_FILE_CONTENT));

    verify(document, never()).asBase64();
    verify(document, never()).asInputStream();
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/json", "application/json;charset=UTF-8"})
  void convertsJsonTypesToDataContent(String mediaType) {
    var jsonContent =
        """
        {
          "key1": "value1",
          "key2": 42,
          "key3": { "nestedKey": "nestedValue" }
        }
        """;

    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asByteArray()).thenReturn(jsonContent.getBytes());

    var content = converter.convert(document);

    var expectedData =
        Map.of("key1", "value1", "key2", 42, "key3", Map.of("nestedKey", "nestedValue"));
    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(DataPart.class))
        .satisfies(dataPart -> assertThat(dataPart.getData()).isEqualTo(expectedData));

    verify(document, never()).asBase64();
    verify(document, never()).asInputStream();
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/pdf", "image/jpeg", "image/png", "image/gif", "image/webp"})
  void convertsToPdfOrImageFileContent(String mediaType) {
    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asBase64()).thenReturn(DUMMY_B64_VALUE);

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(FilePart.class))
        .satisfies(
            filePart -> {
              assertThat(filePart.getFile().mimeType()).isEqualTo(mediaType);
              assertThat(filePart.getFile()).isInstanceOf(FileWithBytes.class);
              assertThat(((FileWithBytes) filePart.getFile()).bytes()).isEqualTo(DUMMY_B64_VALUE);
            });

    verify(document, never()).asByteArray();
    verify(document, never()).asInputStream();
  }

  @Test
  void throwsExceptionWhenMetadataIsMissing() {
    when(document.metadata()).thenReturn(null);
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(DocumentConversionException.class)
        .hasMessage("Content type is unset for document with reference '<REF>'");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void throwsExceptionWhenContentTypeIsMissing(String contentType) {
    when(document.metadata().getContentType()).thenReturn(contentType);
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(DocumentConversionException.class)
        .hasMessage("Content type is unset for document with reference '<REF>'");
  }

  @Test
  void throwsExceptionOnInvalidContentType() {
    when(document.metadata().getContentType()).thenReturn("foo_bar");
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(DocumentConversionException.class)
        .hasMessage("Unsupported content type 'foo_bar' for document with reference '<REF>'");
  }

  @Test
  void throwsExceptionOnUnsupportedContentType() {
    when(document.metadata().getContentType()).thenReturn("application/zip");
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(DocumentConversionException.class)
        .hasMessage(
            "Unsupported content type 'application/zip' for document with reference '<REF>'");
  }

  @Test
  void convertsMultipleDocumentsOfDifferentTypes() {
    Document textDoc = mock(Document.class, Answers.RETURNS_DEEP_STUBS);
    Document jsonDoc = mock(Document.class, Answers.RETURNS_DEEP_STUBS);
    Document imageDoc = mock(Document.class, Answers.RETURNS_DEEP_STUBS);

    when(textDoc.metadata().getContentType()).thenReturn("text/plain");
    when(textDoc.asByteArray()).thenReturn("Hello World".getBytes());

    var jsonContent =
        """
        {"name": "test", "value": 123}
        """;
    when(jsonDoc.metadata().getContentType()).thenReturn("application/json");
    when(jsonDoc.asByteArray()).thenReturn(jsonContent.getBytes());

    when(imageDoc.metadata().getContentType()).thenReturn("image/png");
    when(imageDoc.asBase64()).thenReturn(DUMMY_B64_VALUE);

    var parts = converter.convert(List.of(textDoc, jsonDoc, imageDoc));

    assertThat(parts)
        .satisfiesExactly(
            first -> {
              assertThat(first).isInstanceOf(TextPart.class);
              assertThat(((TextPart) first).getText()).isEqualTo("Hello World");
            },
            second -> {
              assertThat(second).isInstanceOf(DataPart.class);
              assertThat(((DataPart) second).getData())
                  .isEqualTo(Map.of("name", "test", "value", 123));
            },
            third -> {
              assertThat(third).isInstanceOf(FilePart.class);
              FilePart filePart = (FilePart) third;
              assertThat(filePart.getFile().mimeType()).isEqualTo("image/png");
              assertThat(((FileWithBytes) filePart.getFile()).bytes()).isEqualTo(DUMMY_B64_VALUE);
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  void convertsEmptyOrNullDocumentListToEmptyList(List<Document> documents) {
    var parts = converter.convert(documents);
    assertThat(parts).isEmpty();
  }
}
