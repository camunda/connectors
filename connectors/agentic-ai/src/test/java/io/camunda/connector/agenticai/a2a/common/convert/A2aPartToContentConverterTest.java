/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.common.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.a2a.spec.DataPart;
import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class A2aPartToContentConverterTest {

  private final A2aPartToContentConverterImpl converter = new A2aPartToContentConverterImpl();

  @ParameterizedTest
  @NullAndEmptySource
  void convertNullOrEmptyPartsReturnsEmptyList(List<Part<?>> parts) {
    assertThat(converter.convert(parts)).isEmpty();
  }

  @Test
  void convertTextPartToTextContent() {
    TextPart textPart = new TextPart("Hello, world!");

    var result = converter.convert(textPart);

    assertThat(result).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result).text()).isEqualTo("Hello, world!");
    assertThat(result.metadata()).isNull();
  }

  @Test
  void convertTextPartWithMetadataPreservesMetadata() {
    Map<String, Object> metadata = Map.of("key", "value", "num", 42);
    TextPart textPart = new TextPart("Hello, world!", metadata);

    var result = converter.convert(List.of(textPart));

    assertThat(result)
        .satisfiesExactly(
            content -> {
              assertThat(content).isInstanceOf(TextContent.class);
              assertThat(content.metadata()).isEqualTo(metadata);
            });
  }

  @Test
  void convertDataPartToObjectContent() {
    Map<String, Object> data = Map.of("field1", "value1", "field2", 123);
    DataPart dataPart = new DataPart(data);

    var result = converter.convert(dataPart);

    assertThat(result).isInstanceOf(ObjectContent.class);
    assertThat(((ObjectContent) result).content()).isEqualTo(data);
    assertThat(result.metadata()).isNull();
  }

  @Test
  void convertDataPartWithMetadataPreservesMetadata() {
    Map<String, Object> data = Map.of("field1", "value1");
    Map<String, Object> metadata = Map.of("source", "test");
    DataPart dataPart = new DataPart(data, metadata);

    var result = converter.convert(List.of(dataPart));

    assertThat(result)
        .satisfiesExactly(
            content -> {
              assertThat(content).isInstanceOf(ObjectContent.class);
              assertThat(content.metadata()).isEqualTo(metadata);
            });
  }

  @Test
  void convertMultiplePartsOfDifferentTypes() {
    TextPart textPart1 = new TextPart("First text");
    Map<String, Object> data = Map.of("key", "value");
    DataPart dataPart = new DataPart(data);
    TextPart textPart2 = new TextPart("Second text");

    var result = converter.convert(List.of(textPart1, dataPart, textPart2));

    assertThat(result)
        .satisfiesExactly(
            first -> {
              assertThat(first).isInstanceOf(TextContent.class);
              assertThat(((TextContent) first).text()).isEqualTo("First text");
            },
            second -> {
              assertThat(second).isInstanceOf(ObjectContent.class);
              assertThat(((ObjectContent) second).content()).isEqualTo(data);
            },
            third -> {
              assertThat(third).isInstanceOf(TextContent.class);
              assertThat(((TextContent) third).text()).isEqualTo("Second text");
            });
  }

  @Test
  void unsupportedPartTypeThrowsRuntimeException() {
    FileWithBytes fileWithBytes = new FileWithBytes("application/pdf", "file.pdf", "BASE64DATA==");
    FilePart filePart = new FilePart(fileWithBytes);

    assertThatThrownBy(() -> converter.convert(filePart))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Only text and data parts are supported in the response yet.");
  }

  @Test
  void unsupportedPartTypeThrowsRuntimeExceptionWhenMultipleParts() {
    TextPart textPart1 = new TextPart("First text");
    FileWithBytes fileWithBytes = new FileWithBytes("application/pdf", "file.pdf", "BASE64DATA==");
    FilePart filePart = new FilePart(fileWithBytes);
    TextPart textPart2 = new TextPart("Second text");

    assertThatThrownBy(() -> converter.convert(List.of(textPart1, filePart, textPart2)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Only text and data parts are supported in the response yet.");
  }
}
