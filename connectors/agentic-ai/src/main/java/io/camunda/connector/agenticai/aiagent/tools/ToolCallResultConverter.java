/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentContentModule;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentContentSerializer;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentToContentConverter;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;

/**
 * Converts the result of a tool call to a {@link ToolExecutionResultMessage}.
 *
 * <p>If the result is not a string, it will be serialized to a JSON string, using the {@link
 * CamundaDocumentContentSerializer} to serialize document contents.
 */
public class ToolCallResultConverter {

  private static final String PROPERTY_ID = "id";
  private static final String PROPERTY_NAME = "name";
  private static final String PROPERTY_CONTENT = "content";

  private final ObjectMapper resultObjectMapper;

  public ToolCallResultConverter(
      ObjectMapper objectMapper, CamundaDocumentToContentConverter documentConverter) {
    this.resultObjectMapper =
        objectMapper.copy().registerModule(new CamundaDocumentContentModule(documentConverter));
  }

  public ToolExecutionResultMessage asToolExecutionResultMessage(
      Map<String, Object> toolCallResult) {
    final var id = nullableToString(toolCallResult.get(PROPERTY_ID));
    final var name = nullableToString(toolCallResult.get(PROPERTY_NAME));

    return toolExecutionResultMessage(
        id, name, contentAsString(name, toolCallResult.get(PROPERTY_CONTENT)));
  }

  private String contentAsString(String toolName, Object result) {
    if (result instanceof String stringResult) {
      return stringResult;
    }

    try {
      return resultObjectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call '%s' to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  private String nullableToString(Object o) {
    return o == null ? "" : o.toString();
  }
}
