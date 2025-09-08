/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.client.convert;

import static io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverterImpl.ERROR_CODE_FAILED_TO_SERIALIZE_DATA_PART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.a2a.spec.DataPart;
import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mockito;

class PartsToContentConverterTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  private final PartsToContentConverterImpl converter =
      new PartsToContentConverterImpl(objectMapper);

  @ParameterizedTest
  @NullAndEmptySource
  void convertNullOrEmptyPartsReturnsEmptyList(List<Part<?>> parts) {
    assertThat(converter.convert(parts)).isEmpty();
  }

  @Test
  void convertSingleTextPartReturnsSingleTextContent() {
    TextPart textPart = new TextPart("Hello world", Map.of("contentType", "text/plain"));
    final var result = converter.convert(List.of(textPart));
    assertThat(result).containsExactly(new TextContent("Hello world"));
  }

  @Test
  void convertMultipleTextPartsConcatenatesIntoSingleTextContent() {
    var result =
        converter.convert(
            List.of(
                new TextPart("Hello", Map.of()),
                new TextPart("", Map.of()),
                new TextPart("world", Map.of())));

    assertThat(result).containsExactly(new TextContent("Hello\nworld"));
  }

  @Test
  void convertMultipleTextPartsConcatenatesIntoSingleTextContentIgnoresEmptyTexts() {
    var result =
        converter.convert(
            List.of(
                new TextPart("", Map.of()),
                new TextPart("Hello", Map.of()),
                new TextPart("", Map.of()),
                new TextPart("world", Map.of()),
                new TextPart("", Map.of())));

    assertThat(result).containsExactly(new TextContent("Hello\nworld"));
  }

  @Test
  void convertSingleDataPartWithMetadata() {
    DataPart dataPart =
        new DataPart(
            Map.of("key", "value"), Map.of("contentType", "application/json", "source", "system"));

    var result = converter.convert(List.of(dataPart));
    assertThat(result)
        .containsExactly(
            new TextContent(
"""

---
JSON data:
{"key":"value"}
Metadata:
{"contentType":"application/json","source":"system"}
---
"""));
  }

  @Test
  void convertSingleDataPartWithoutMetadata() {
    DataPart dataPart = new DataPart(Map.of("a", 1), Map.of());

    var result = converter.convert(List.of(dataPart));
    assertThat(result)
        .containsExactly(
            new TextContent(
"""

---
JSON data:
{"a":1}
---
"""));
  }

  @Test
  void convertSingleEmptyDataPartWithoutMetadata() {
    DataPart dataPart = new DataPart(Map.of(), Map.of());

    var result = converter.convert(List.of(dataPart));
    assertThat(result)
        .containsExactly(
            new TextContent(
"""

---
JSON data:
{}
---
"""));
  }

  @Test
  void convertMixedTextAndDataParts() {
    TextPart before = new TextPart("Before", Map.of());
    DataPart dataPart =
        new DataPart(Map.of("key", "value"), Map.of("contentType", "application/json"));
    TextPart after = new TextPart("After", Map.of());

    var result = converter.convert(List.of(before, dataPart, after));
    assertThat(result)
        .containsExactly(
            new TextContent(
"""
Before

---
JSON data:
{"key":"value"}
Metadata:
{"contentType":"application/json"}
---

After"""));
  }

  @Test
  void serializationFailureThrowsConnectorExceptionWithErrorCode() throws Exception {
    ObjectMapper failingObjectMapper = Mockito.mock(ObjectMapper.class);
    Mockito.when(failingObjectMapper.writeValueAsString(Mockito.any()))
        .thenThrow(new JsonProcessingException("boom") {});
    PartsToContentConverterImpl failingConverter =
        new PartsToContentConverterImpl(failingObjectMapper);

    DataPart dataPart = new DataPart(Map.of("key", "value"), Map.of());

    assertThatThrownBy(() -> failingConverter.convert(List.of(dataPart)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Could not convert data part to string")
        .satisfies(
            ex ->
                assertThat(((ConnectorException) ex).getErrorCode())
                    .isEqualTo(ERROR_CODE_FAILED_TO_SERIALIZE_DATA_PART));
  }

  @Test
  void unsupportedPartTypeThrowsRuntimeException() {
    FileWithBytes fileWithBytes = new FileWithBytes("application/pdf", "file.pdf", "BASE64DATA==");
    FilePart filePart = new FilePart(fileWithBytes);

    assertThatThrownBy(() -> converter.convert(List.of(filePart)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Only text and data parts are supported in the response yet.");
  }
}
