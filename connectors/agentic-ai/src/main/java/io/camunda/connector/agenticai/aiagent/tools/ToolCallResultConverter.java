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
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentToContentConverter;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.document.Document;
import java.util.List;
import java.util.Map;

public class ToolCallResultConverter {

  private final ObjectMapper resultObjectMapper;

  public ToolCallResultConverter(
      ObjectMapper objectMapper, CamundaDocumentToContentConverter documentConverter) {
    this.resultObjectMapper =
        objectMapper
            .copy()
            .registerModule(
                new SimpleModule()
                    .addSerializer(
                        Document.class, new CamundaDocumentSerializer(documentConverter)));
  }

  public List<ToolExecutionResultMessage> toolCallResultsAsToolExecutionResultMessages(
      List<Map<String, Object>> toolCallResults) {
    return toolCallResults.stream().map(this::toolCallResultAsToolExecutionResultMessage).toList();
  }

  public ToolExecutionResultMessage toolCallResultAsToolExecutionResultMessage(
      Map<String, Object> toolCallResult) {
    final var id = nullableToString(toolCallResult.get("id"));
    final var name = nullableToString(toolCallResult.get("name"));

    return toolExecutionResultMessage(
        id, name, contentAsString(name, toolCallResult.get("content")));
  }

  private String contentAsString(String toolName, Object result) {
    if (result instanceof String stringResult) {
      return stringResult;
    }

    try {
      return resultObjectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call %s to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  private String nullableToString(Object o) {
    return o == null ? "" : o.toString();
  }
}
