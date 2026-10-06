/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.model.message.MessageUtil.singleTextContent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.camunda.connector.generator.java.annotation.DataExample;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentResponse.AgentResponseJacksonProxyBuilder.class)
public record AgentResponse(
    AgentContext context,
    List<ToolCallProcessVariable> toolCalls,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText,
    @Nullable Object responseJson,
    // the returned documents by key, in call order; a LinkedHashMap because the record builder
    // copies a Map into an immutable map of unspecified iteration order
    @RecordBuilder.Initializer(source = AgentResponse.class, value = "noResponseDocuments")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        LinkedHashMap<String, ReturnedDocument> responseDocuments)
    implements AgentResponseBuilder.With {

  static LinkedHashMap<String, ReturnedDocument> noResponseDocuments() {
    return new LinkedHashMap<>();
  }

  public static AgentResponseBuilder builder() {
    return AgentResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentResponseJacksonProxyBuilder extends AgentResponseBuilder {}

  @DataExample(id = DataExample.DEFAULT_ID, feel = "={ responseText: responseText }")
  public static AgentResponse exampleResultWithTextResponse() {
    final var agentContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .metrics(
                AgentMetrics.builder()
                    .modelCalls(3)
                    .tokenUsage(
                        AgentMetrics.TokenUsage.builder()
                            .inputTokenCount(10)
                            .outputTokenCount(20)
                            .build())
                    .build())
            .toolDefinitions(
                List.of(
                    ToolDefinition.builder()
                        .name("sampleTool")
                        .description("A sample tool for demonstration purposes.")
                        .inputSchema(
                            Map.ofEntries(
                                Map.entry("type", "object"),
                                Map.entry(
                                    "properties",
                                    Map.of(
                                        "input1", Map.of("type", "string"),
                                        "input2", Map.of("type", "number"))),
                                Map.entry("required", List.of("input1", "input2"))))
                        .build()))
            .build();

    return AgentResponse.builder()
        .context(agentContext)
        .responseText("This is a sample response text from the AI agent.")
        .build();
  }

  @DataExample(id = "json", feel = "=responseJson.firstname")
  public static AgentResponse exampleResultWithJsonResponse() {
    return AgentResponse.builder()
        .context(exampleResultWithTextResponse().context())
        .responseJson(Map.of("userId", 5, "firstname", "John", "lastname", "Doe"))
        .build();
  }

  @DataExample(id = "assistantMessage", feel = "=responseMessage.content[type = \"text\"][1].text")
  public static AgentResponse exampleResultWithAssistantMessageResponse() {
    final var assistantMessage =
        AssistantMessage.builder()
            .id(MessageId.of("01a00023-829e-723b-8cae-8bd1bb11c25b"))
            .content(singleTextContent("This is a sample response text from the AI agent."))
            .metadata(
                Map.of(
                    "framework",
                    Map.ofEntries(
                        Map.entry("id", "chatcmpl-123"),
                        Map.entry("finishReason", "STOP"),
                        Map.entry(
                            "tokenUsage",
                            Map.of(
                                "inputTokenCount", 5,
                                "outputTokenCount", 6,
                                "totalTokenCount", 11)))))
            .build();

    return AgentResponse.builder()
        .context(exampleResultWithTextResponse().context())
        .responseMessage(assistantMessage)
        .build();
  }

  @DataExample(id = "withToolCalls", feel = "=toolCalls")
  public static AgentResponse exampleResultWithToolCalls() {
    return AgentResponse.builder()
        .context(exampleResultWithTextResponse().context())
        .toolCalls(
            List.of(
                ToolCallProcessVariable.from(
                    ToolCall.builder()
                        .id("123456")
                        .name("sampleTool")
                        .arguments(Map.of("input1", "value1", "input2", 42))
                        .build())))
        .build();
  }

  @DataExample(id = "withResponseDocuments", feel = "=responseDocuments")
  public static AgentResponse exampleResultWithResponseDocuments() {
    return AgentResponse.builder()
        .context(exampleResultWithTextResponse().context())
        .responseText("The report is attached.")
        .responseDocuments(
            new LinkedHashMap<>(
                Map.of(
                    "report",
                    new ReturnedDocument(
                        Map.ofEntries(
                            Map.entry("camunda.document.type", "camunda"),
                            Map.entry("storeId", "in-memory"),
                            Map.entry("documentId", "d2a6b9c4-5f1e-4a6e-9a43-6a5a1d3b8c10"),
                            Map.entry("contentHash", "9f86d081884c7d659a2feaa0c55ad015"),
                            Map.entry(
                                "metadata",
                                Map.of(
                                    "contentType", "application/pdf", "fileName", "report.pdf"))),
                        "The quarterly report"))))
        .build();
  }
}
