/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class TestMessagesFixture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final List<AdHocToolElement> AD_HOC_TOOL_ELEMENTS =
      List.of(
          AdHocToolElement.builder()
              .elementId("getWeather")
              .elementName("Get Weather")
              .documentation("Returns the current weather")
              .parameters(
                  List.of(
                      AdHocToolElementParameter.builder().name("location").type("string").build()))
              .build(),
          AdHocToolElement.builder()
              .elementId("getDateTime")
              .elementName("Get DateTime")
              .documentation("Returns the current time")
              .build());

  public static final List<ToolDefinition> TOOL_DEFINITIONS =
      List.of(
          ToolDefinition.builder()
              .name("getWeather")
              .description("Returns the current weather")
              .inputSchema(
                  Map.ofEntries(
                      Map.entry("type", "object"),
                      Map.entry("properties", Map.of("location", Map.of("type", "string"))),
                      Map.entry("required", List.of("location"))))
              .build(),
          ToolDefinition.builder()
              .name("getDateTime")
              .description("Returns the current time")
              .build());

  public static final List<ToolCall> TOOL_CALLS =
      List.of(
          ToolCall.builder()
              .id("abcdef")
              .name("getWeather")
              .arguments(Map.of("location", "MUC"))
              .build(),
          ToolCall.builder().id("fedcba").name("getDateTime").build());

  public static final List<ToolCallProcessVariable> TOOL_CALLS_PROCESS_VARIABLES =
      TOOL_CALLS.stream().map(ToolCallProcessVariable::from).toList();

  public static final List<ToolCallResult> TOOL_CALL_RESULTS =
      List.of(
          ToolCallResult.builder().id("abcdef").name("getWeather").content("Sunny, 22°C").build(),
          ToolCallResult.builder()
              .id("fedcba")
              .name("getDateTime")
              .content(
                  Map.of("date", "2025-04-14", "time", "15:56:50", "iso", "2025-04-14T15:56:50"))
              .build());

  public static final List<ToolCallResult> EVENT_TOOL_CALL_RESULTS =
      List.of(
          ToolCallResult.builder().content("Event data").build(),
          ToolCallResult.builder().content(Map.of("another", "event data")).build());

  public static List<Message> testMessages() {
    return List.of(
        systemMessage("You are a helpful assistant. Be nice."),
        userMessage(
                List.of(
                    textContent("What is the weather in Munich?"),
                    textContent("Is it typical for this time of the year?")))
            .withName("user1"),
        assistantMessage(
            "To give an answer, I need to first look up the weather in Munich. Considering available tools, I should call the getWeather tool. In addition I will call the getDateTime tool to know the current date and time.",
            TOOL_CALLS),
        toolCallResultMessage(TOOL_CALL_RESULTS),
        assistantMessage(
                "The weather in Munich is sunny with a temperature of 22°C. This is typical for April.")
            .withMetadata(Map.of("some", "value")),
        userMessage("Thank you!").withName("user1"));
  }

  public static List<Message> testMessagesFromFile() throws IOException {
    return OBJECT_MAPPER.readValue(
        TestMessagesFixture.class.getClassLoader().getResourceAsStream("test-messages.json"),
        new TypeReference<>() {});
  }

  public static SystemMessage systemMessage(String text) {
    return SystemMessage.builder().content(singleTextContent(text)).build();
  }

  public static UserMessage userMessage(String text) {
    return UserMessage.builder().content(singleTextContent(text)).build();
  }

  public static UserMessage userMessage(List<Content> content) {
    return UserMessage.builder().content(content).build();
  }

  public static AssistantMessage assistantMessage(String text) {
    return AssistantMessage.builder().content(singleTextContent(text)).build();
  }

  public static AssistantMessage assistantMessage(String text, List<ToolCall> toolCalls) {
    return AssistantMessage.builder().content(singleTextContent(text)).toolCalls(toolCalls).build();
  }

  public static AssistantMessage assistantMessage(List<Content> content) {
    return AssistantMessage.builder().content(content).build();
  }

  public static ToolCallResultMessage toolCallResultMessage(List<ToolCallResult> results) {
    return ToolCallResultMessage.builder().results(results).build();
  }
}
