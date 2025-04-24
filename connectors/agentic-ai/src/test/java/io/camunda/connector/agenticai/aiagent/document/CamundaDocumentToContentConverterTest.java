/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentToContentConverter.CamundaDocumentConvertingException;
import io.camunda.document.Document;
import java.util.Base64;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class CamundaDocumentToContentConverterTest {

  private final CamundaDocumentToContentConverter converter =
      new CamundaDocumentToContentConverter();

  private static final String TEXT_FILE_CONTENT = "Lorem ipsum dolor sit amet.";
  private static final String TEXT_FILE_CONTENT_B64 =
      Base64.getEncoder().encodeToString(TEXT_FILE_CONTENT.getBytes());

  private static final String DUMMY_B64_VALUE = "dGVzdA==";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Document document;

  @Test
  void convertsToTextContent() {
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
  @ValueSource(
      strings = {
        // text/* types
        "text/csv",
        "text/my-custom-format",
        MediaType.TEXT_XML_VALUE,

        // defined application/* types
        MediaType.APPLICATION_XML_VALUE,
        MediaType.APPLICATION_JSON_VALUE,
        MediaType.APPLICATION_YAML_VALUE,

        // compatible types
        MediaType.APPLICATION_JSON_UTF8_VALUE
      })
  void convertsToTextFileContent(String mediaType) {
    when(document.metadata().getContentType()).thenReturn(mediaType);
    when(document.asBase64()).thenReturn(TEXT_FILE_CONTENT_B64);

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(TextFileContent.class))
        .satisfies(
            textFileContent -> {
              assertThat(textFileContent.textFile().mimeType()).isEqualTo(mediaType);
              assertThat(textFileContent.textFile().base64Data()).isEqualTo(TEXT_FILE_CONTENT_B64);
            });

    verify(document, never()).asByteArray();
    verify(document, never()).asInputStream();
  }

  @Test
  void convertsToPdfFileContent() {
    when(document.metadata().getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);
    when(document.asBase64()).thenReturn(DUMMY_B64_VALUE);

    var content = converter.convert(document);

    assertThat(content)
        .asInstanceOf(InstanceOfAssertFactories.type(PdfFileContent.class))
        .satisfies(
            pdfFileContent -> {
              assertThat(pdfFileContent.pdfFile().base64Data()).isEqualTo(DUMMY_B64_VALUE);
            });

    verify(document, never()).asByteArray();
    verify(document, never()).asInputStream();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        MediaType.IMAGE_JPEG_VALUE,
        MediaType.IMAGE_PNG_VALUE,
        MediaType.IMAGE_GIF_VALUE,
        "image/webp"
      })
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
        .isInstanceOf(CamundaDocumentConvertingException.class)
        .hasMessage("Content type is unset for document with reference '<REF>'");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void throwsExceptionWhenContentTypeIsMissing(String contentType) {
    when(document.metadata().getContentType()).thenReturn(contentType);
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(CamundaDocumentConvertingException.class)
        .hasMessage("Content type is unset for document with reference '<REF>'");
  }

  @Test
  void throwsExceptionOnInvalidContentType() {
    when(document.metadata().getContentType()).thenReturn("foo_bar");
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(CamundaDocumentConvertingException.class)
        .hasMessage(
            "Failed to parse content type 'foo_bar' for document with reference '<REF>': Invalid mime type \"foo_bar\": does not contain '/'");
  }

  @Test
  void throwsExceptionOnUnsupportedContentType() {
    when(document.metadata().getContentType()).thenReturn("application/zip");
    when(document.reference().toString()).thenReturn("<REF>");

    assertThatThrownBy(() -> converter.convert(document))
        .isInstanceOf(CamundaDocumentConvertingException.class)
        .hasMessage(
            "Unsupported content type 'application/zip' for document with reference '<REF>'");
  }
}
