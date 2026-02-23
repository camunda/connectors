/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import io.camunda.connector.api.document.Document;
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
class DocumentToContentConverterTest {

  private final DocumentToContentConverter converter = new DocumentToContentConverterImpl();

  private static final String TEXT_FILE_CONTENT = "Lorem ipsum dolor sit amet.";
  private static final String DUMMY_B64_VALUE = "dGVzdA==";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Document document;

  @Test
  void convertsPlainTextToTextContent() {
    when(document.metadata().getContentType()).thenReturn("text/plain");
    when(document.asByteArray()).thenReturn(TEXT_FILE_CONTENT.getBytes());

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(TextContent.class))
        .satisfies(textContent -> assertThat(textContent.text()).isEqualTo(TEXT_FILE_CONTENT));

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
    "application/json",
    "application/yaml",

    // compatible types
    "application/json;charset=UTF-8"
  })
  void convertsOtherTextTypesToTextContent(String mediaType) {
    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asByteArray()).thenReturn(TEXT_FILE_CONTENT.getBytes());

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(TextContent.class))
        .satisfies(textContent -> assertThat(textContent.text()).isEqualTo(TEXT_FILE_CONTENT));

    verify(document, never()).asBase64();
    verify(document, never()).asInputStream();
  }

  @Test
  void convertsToPdfFileContent() {
    when(document.metadata().getContentType()).thenReturn("application/pdf");
    when(document.asBase64()).thenReturn(DUMMY_B64_VALUE);

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(PdfFileContent.class))
        .satisfies(
            pdfFileContent ->
                assertThat(pdfFileContent.pdfFile().base64Data()).isEqualTo(DUMMY_B64_VALUE));

    verify(document, never()).asByteArray();
    verify(document, never()).asInputStream();
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp"})
  void convertsToImageFileContent(String mediaType) {
    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asBase64()).thenReturn(DUMMY_B64_VALUE);

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(ImageContent.class))
        .satisfies(
            imageContent -> {
              assertThat(imageContent.detailLevel()).isEqualTo(ImageContent.DetailLevel.AUTO);
              assertThat(imageContent.image().mimeType()).isEqualTo(mediaType);
              assertThat(imageContent.image().base64Data()).isEqualTo(DUMMY_B64_VALUE);
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
}
