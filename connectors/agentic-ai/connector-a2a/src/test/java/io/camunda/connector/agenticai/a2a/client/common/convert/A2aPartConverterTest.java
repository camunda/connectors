/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.client.common.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.a2a.spec.DataPart;
import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aDataPart;
import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aTextPart;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class A2aPartConverterTest {

  private final A2aPartConverterImpl converter = new A2aPartConverterImpl();

  @ParameterizedTest
  @NullAndEmptySource
  void convertNullOrEmptyPartsReturnsEmptyList(List<Part<?>> parts) {
    assertThat(converter.convert(parts)).isEmpty();
  }

  @Test
  void convertTextPartToA2aTextPart() {
    TextPart textPart = new TextPart("Hello, world!");

    var result = converter.convert(textPart);

    assertThat(result).isInstanceOf(A2aTextPart.class);
    assertThat(((A2aTextPart) result).text()).isEqualTo("Hello, world!");
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
              assertThat(content).isInstanceOf(A2aTextPart.class);
              assertThat(content.metadata()).isEqualTo(metadata);
            });
  }

  @Test
  void convertDataPartToA2aDataPart() {
    Map<String, Object> data = Map.of("field1", "value1", "field2", 123);
    DataPart dataPart = new DataPart(data);

    var result = converter.convert(dataPart);

    assertThat(result).isInstanceOf(A2aDataPart.class);
    assertThat(((A2aDataPart) result).content()).isEqualTo(data);
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
              assertThat(content).isInstanceOf(A2aDataPart.class);
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
              assertThat(first).isInstanceOf(A2aTextPart.class);
              assertThat(((A2aTextPart) first).text()).isEqualTo("First text");
            },
            second -> {
              assertThat(second).isInstanceOf(A2aDataPart.class);
              assertThat(((A2aDataPart) second).content()).isEqualTo(data);
            },
            third -> {
              assertThat(third).isInstanceOf(A2aTextPart.class);
              assertThat(((A2aTextPart) third).text()).isEqualTo("Second text");
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
