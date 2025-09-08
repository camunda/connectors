/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

public class PartsToContentConverterImpl implements PartsToContentConverter {

  public static final String ERROR_CODE_FAILED_TO_SERIALIZE_DATA_PART =
      "FAILED_TO_SERIALIZE_DATA_PART_AS_JSON";

  private final ObjectMapper objectMapper;

  public PartsToContentConverterImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Content> convert(List<Part<?>> parts) {
    if (CollectionUtils.isEmpty(parts)) {
      return List.of();
    }
    StringBuilder textBuilder = new StringBuilder();
    for (Part<?> part : parts) {
      switch (part) {
        case TextPart textPart -> appendText(textBuilder, textPart.getText());
        case DataPart dataPart -> appendText(textBuilder, convertDataPart(dataPart));
        default ->
            // TODO: yaml and xml files can be converted to text content
            throw new RuntimeException(
                "Only text and data parts are supported in the response yet.");
      }
    }
    return StringUtils.isBlank(textBuilder)
        ? List.of()
        : List.of(new TextContent(textBuilder.deleteCharAt(textBuilder.length() - 1).toString()));
  }

  private String convertDataPart(DataPart dataPart) {
    StringBuilder textBuilder = new StringBuilder();
    textBuilder.append("\n---\n");
    textBuilder.append("JSON data:\n");
    textBuilder.append(
        serializeAsJSONString(dataPart.getData(), "Could not convert data part to string: %s"));

    if (MapUtils.isNotEmpty(dataPart.getMetadata())) {
      textBuilder.append("\nMetadata:\n");
      textBuilder.append(
          serializeAsJSONString(
              dataPart.getMetadata(), "Could not convert data part metadata to string: %s"));
    }
    textBuilder.append("\n---\n");
    return textBuilder.toString();
  }

  private String serializeAsJSONString(Map<String, Object> dataPart, String errorMessage) {
    try {
      return objectMapper.writeValueAsString(dataPart);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_TO_SERIALIZE_DATA_PART,
          errorMessage.formatted(humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  private static void appendText(StringBuilder textBuilder, String text) {
    if (StringUtils.isNotBlank(text)) {
      textBuilder.append(text).append("\n");
    }
  }
}
