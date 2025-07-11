/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.generator.java.annotation.DataExample;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentResponse.AgentResponseJacksonProxyBuilder.class)
public record AgentResponse(
    AgentContext context,
    List<ToolCallProcessVariable> toolCalls,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText,
    @Nullable Object responseJson)
    implements AgentResponseBuilder.With {

  public static AgentResponseBuilder builder() {
    return AgentResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentResponseJacksonProxyBuilder extends AgentResponseBuilder {}

  @DataExample(id = "text", feel = "=responseText")
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
            .content(singleTextContent("This is a sample response text from the AI agent."))
            .metadata(
                Map.of(
                    "framework",
                    Map.ofEntries(
                        Map.entry(
                            "tokenUsage",
                            Map.of(
                                "inputTokenCount", 5,
                                "outputTokenCount", 6,
                                "totalTokenCount", 11)),
                        Map.entry("finishReason", "STOP"))))
            .build();

    return AgentResponse.builder()
        .context(exampleResultWithTextResponse().context())
        .responseMessage(assistantMessage)
        .build();
  }

  @DataExample(id = "withToolCalls", feel = "=toolCalls")
  public static AgentResponse exampleResultWithToolCalls() {
    return AgentResponse.builder()
        .context(
            exampleResultWithTextResponse().context().withState(AgentState.WAITING_FOR_TOOL_INPUT))
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
}
