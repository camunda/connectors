/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class TestMessagesFixture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static List<ChatMessage> testMessages() {
    return List.of(
        new UserMessage(
            TextContent.from("What is the weather in Munich?"),
            TextContent.from("Is it typical for this time of the year?")),
        new AiMessage(
            "To give an answer, I need to first look up the weather in Munich. Considering available tools, I should call the getWeather tool. In addition I will call the getDateTime tool to know the current date and time.",
            List.of(
                ToolExecutionRequest.builder()
                    .id("abcdef")
                    .name("getWeather")
                    .arguments("{\"location\": \"MUC\"}")
                    .build(),
                ToolExecutionRequest.builder().id("fedcba").name("getDateTime").build())),
        new ToolExecutionResultMessage("abcdef", "getWeather", "Sunny, 22°C"),
        new ToolExecutionResultMessage("fedcba", "getDateTime", "2025-04-14T15:56:50"),
        new AiMessage(
            "The weather in Munich is sunny with a temperature of 22°C. This is typical for April."),
        new UserMessage("Thank you!"));
  }

  public static List<Map<String, Object>> serializedTestMessages() throws IOException {
    return OBJECT_MAPPER.readValue(
        TestMessagesFixture.class.getClassLoader().getResourceAsStream("test-messages.json"),
        new TypeReference<>() {});
  }
}
