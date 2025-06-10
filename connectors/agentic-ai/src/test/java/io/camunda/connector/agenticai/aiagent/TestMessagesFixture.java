/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class TestMessagesFixture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final List<ToolCallResult> TOOL_CALL_RESULTS =
      List.of(
          ToolCallResult.builder().id("abcdef").name("getWeather").content("Sunny, 22°C").build(),
          ToolCallResult.builder()
              .id("fedcba")
              .name("getDateTime")
              .content(
                  Map.of("date", "2025-04-14", "time", "15:56:50", "iso", "2025-04-14T15:56:50"))
              .build());

  public static List<Message> testMessages() {
    return List.of(
        SystemMessage.systemMessage("You are a helpful assistant. Be nice."),
        UserMessage.userMessage(
                List.of(
                    textContent("What is the weather in Munich?"),
                    textContent("Is it typical for this time of the year?")))
            .withName("user1"),
        AssistantMessage.assistantMessage(
            "To give an answer, I need to first look up the weather in Munich. Considering available tools, I should call the getWeather tool. In addition I will call the getDateTime tool to know the current date and time.",
            List.of(
                ToolCall.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .arguments(Map.of("location", "MUC"))
                    .build(),
                ToolCall.builder().id("fedcba").name("getDateTime").build())),
        ToolCallResultMessage.toolCallResultMessage(TOOL_CALL_RESULTS),
        AssistantMessage.assistantMessage(
                "The weather in Munich is sunny with a temperature of 22°C. This is typical for April.")
            .withMetadata(Map.of("some", "value")),
        UserMessage.userMessage("Thank you!").withName("user1"));
  }

  public static List<Message> testMessagesFromFile() throws IOException {
    return OBJECT_MAPPER.readValue(
        TestMessagesFixture.class.getClassLoader().getResourceAsStream("test-messages.json"),
        new TypeReference<>() {});
  }
}
