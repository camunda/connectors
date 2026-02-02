/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BinaryDataToContentConverterTest {

  private static final String TEXT_CONTENT = "Hello, world!";
  private static final byte[] TEXT_DATA = TEXT_CONTENT.getBytes(StandardCharsets.UTF_8);
  private static final byte[] BINARY_DATA = "binary-data".getBytes(StandardCharsets.UTF_8);

  @Test
  void convertsPlainTextToTextContent() {
    var result = BinaryDataToContentConverter.convert(TEXT_DATA, ContentType.TEXT_PLAIN);

    assertThat(result).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result).text()).isEqualTo(TEXT_CONTENT);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain",
        "text/csv",
        "text/html",
        "text/xml",
        "application/json",
        "application/xml",
        "application/yaml",
        "application/json;charset=UTF-8"
      })
  void convertsTextTypesToTextContent(String mimeType) {
    var contentType = ContentType.parse(mimeType);
    var result = BinaryDataToContentConverter.convert(TEXT_DATA, contentType);

    assertThat(result).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result).text()).isEqualTo(TEXT_CONTENT);
  }

  @Test
  void convertsPdfToPdfFileContent() {
    var result = BinaryDataToContentConverter.convert(BINARY_DATA, ContentType.APPLICATION_PDF);

    assertThat(result).isInstanceOf(PdfFileContent.class);
    assertThat(((PdfFileContent) result).pdfFile().base64Data())
        .isEqualTo(Base64.getEncoder().encodeToString(BINARY_DATA));
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp"})
  void convertsImageTypesToImageContent(String mimeType) {
    var contentType = ContentType.parse(mimeType);
    var result = BinaryDataToContentConverter.convert(BINARY_DATA, contentType);

    assertThat(result).isInstanceOf(ImageContent.class);
    var imageContent = (ImageContent) result;
    assertThat(imageContent.image().mimeType()).isEqualTo(mimeType);
    assertThat(imageContent.image().base64Data())
        .isEqualTo(Base64.getEncoder().encodeToString(BINARY_DATA));
    assertThat(imageContent.detailLevel()).isEqualTo(ImageContent.DetailLevel.AUTO);
  }

  @Test
  void returnsNullForUnsupportedContentType() {
    var result =
        BinaryDataToContentConverter.convert(BINARY_DATA, ContentType.create("application/zip"));

    assertThat(result).isNull();
  }
}
