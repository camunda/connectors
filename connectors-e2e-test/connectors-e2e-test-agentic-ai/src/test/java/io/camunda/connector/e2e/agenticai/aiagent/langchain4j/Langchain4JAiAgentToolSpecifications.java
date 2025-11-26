/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;

public interface Langchain4JAiAgentToolSpecifications {

  ToolSpecification GET_DATE_AND_TIME_TOOL =
      ToolSpecification.builder()
          .name("GetDateAndTime")
          .description("Returns the current date and time including the timezone.")
          .parameters(JsonObjectSchema.builder().build())
          .build();

  ToolSpecification SUPERFLUX_PRODUCT_TOOL =
      ToolSpecification.builder()
          .name("SuperfluxProduct")
          .description(
              "Calculates the superflux product (a very complicated method only this tool can do) given two input numbers")
          .parameters(
              JsonObjectSchema.builder()
                  .addNumberProperty("a", "The first number to be superflux calculated.")
                  .addNumberProperty("b", "The second number to be superflux calculated.")
                  .required("a", "b")
                  .build())
          .build();

  ToolSpecification SEARCH_THE_WEB_TOOL =
      ToolSpecification.builder()
          .name("Search_The_Web")
          .description("Do a web search to find the needed information.")
          .parameters(
              JsonObjectSchema.builder()
                  .addStringProperty("searchQuery", "The search query to use")
                  .required("searchQuery")
                  .build())
          .build();

  ToolSpecification DOWNLOAD_A_FILE_TOOL =
      ToolSpecification.builder()
          .name("Download_A_File")
          .description("Download a file from the provided URL")
          .parameters(
              JsonObjectSchema.builder()
                  .addStringProperty("url", "The URL to download the file from")
                  .required("url")
                  .build())
          .build();

  ToolSpecification A_COMPLEX_TOOL =
      ToolSpecification.builder()
          .name("A_Complex_Tool")
          .description("A very complex tool")
          .parameters(
              JsonObjectSchema.builder()
                  .addStringProperty("aSimpleValue", "A simple value")
                  .addProperty(
                      "anEnumValue",
                      JsonEnumSchema.builder()
                          .description("An enum value")
                          .enumValues("A", "B", "C")
                          .build())
                  .addProperty(
                      "anArrayValue",
                      JsonArraySchema.builder()
                          .description("An array value")
                          .items(JsonEnumSchema.builder().enumValues("foo", "bar", "baz").build())
                          .build())
                  .addStringProperty("urlPath", "The URL path to use")
                  .addStringProperty("firstValue")
                  .addIntegerProperty("secondValue", "The second value")
                  .addStringProperty("thirdValue", "The third value to add")
                  .addProperty(
                      "fourthValue",
                      JsonArraySchema.builder()
                          .description("The fourth value to add")
                          .items(JsonEnumSchema.builder().enumValues("foo", "bar", "baz").build())
                          .build())
                  .required(
                      "aSimpleValue",
                      "anEnumValue",
                      "anArrayValue",
                      "urlPath",
                      "firstValue",
                      "secondValue",
                      "thirdValue")
                  .build())
          .build();

  ToolSpecification AN_EVENT_TOOL =
      ToolSpecification.builder()
          .name("An_Event")
          .description("An event!")
          .parameters(JsonObjectSchema.builder().build())
          .build();

  List<ToolSpecification> EXPECTED_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          A_COMPLEX_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          AN_EVENT_TOOL);

  List<ToolSpecification> EXPECTED_MCP_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          ToolSpecification.builder()
              .name("MCP_A_MCP_Client___toolA")
              .description("The first tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramA1", "The first parameter")
                      .addNumberProperty("paramA2", "The second parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_A_MCP_Client___toolC")
              .description("The third tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramC1", "The first parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_A_HTTP_Remote_MCP_Client___toolA")
              .description("The first tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramA1", "The first parameter")
                      .addNumberProperty("paramA2", "The second parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_A_HTTP_Remote_MCP_Client___toolC")
              .description("The third tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramC1", "The first parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_A_SSE_Remote_MCP_Client___toolA")
              .description("The first tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramA1", "The first parameter")
                      .addNumberProperty("paramA2", "The second parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_A_SSE_Remote_MCP_Client___toolC")
              .description("The third tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramC1", "The first parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_Filesystem_MCP_Flow___toolA")
              .description("The first tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramA1", "The first parameter")
                      .addNumberProperty("paramA2", "The second parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_Filesystem_MCP_Flow___toolB")
              .description("The second tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramB1", "The first parameter")
                      .addEnumProperty("paramB2", List.of("A", "B", "C"))
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("MCP_Filesystem_MCP_Flow___toolC")
              .description("The third tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramC1", "The first parameter")
                      .build())
              .build());

  JsonObjectSchema A2A_JSON_OBJECT_SCHEMA =
      JsonObjectSchema.builder()
          .addStringProperty("text", "The request or the follow-up message to send to the agent.")
          .addStringProperty(
              "taskId",
              "The ID of the task this message is part of. ONLY include if continuing an existing task (e.g., input-required); otherwise OMIT.")
          .addStringProperty(
              "contextId",
              "The context ID for this message, used to group related interactions. OMIT on first message; include on subsequent messages.")
          .addProperty(
              "referenceTaskIds",
              JsonArraySchema.builder()
                  .description(
                      "A list of other task IDs that this message references for additional context.")
                  .build())
          .required("text")
          .build();

  List<ToolSpecification> EXPECTED_A2A_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          ToolSpecification.builder()
              .name("A2A_Travel_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Travel agent","description":"Helps with travel bookings","skills":[{"id":"hotel-booking","name":"Hotel Booking","description":"Book a hotel room","tags":["booking","hotel"],"examples":["Book a single room","Book a double room"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .parameters(A2A_JSON_OBJECT_SCHEMA)
              .build(),
          ToolSpecification.builder()
              .name("A2A_Weather_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Weather agent","description":"Helps with weather information","skills":[{"id":"weather-forecast","name":"Weather Forecast","description":"Get weather forecast information","tags":["forecast","weather"],"examples":["What's the weather like today?","Will it rain tomorrow?"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .parameters(A2A_JSON_OBJECT_SCHEMA)
              .build(),
          ToolSpecification.builder()
              .name("A2A_Exchange_Rate_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Exchange rate agent","description":"Helps with exchanging currency rates","skills":[{"id":"currency-exchange","name":"Currency Exchange","description":"Get currency exchange rates","tags":["currency","exchange"],"examples":["What's the exchange rate from USD to EUR?","Convert 100 GBP to JPY."],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .parameters(A2A_JSON_OBJECT_SCHEMA)
              .build());

  // individual tools provided by a specific MCP client - expected resolved specifications are
  // prefixed with the MCP client name
  List<ToolSpecification> MCP_TOOL_SPECIFICATIONS =
      List.of(
          ToolSpecification.builder()
              .name("toolA")
              .description("The first tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramA1", "The first parameter")
                      .addNumberProperty("paramA2", "The second parameter")
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("toolB")
              .description("The second tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramB1", "The first parameter")
                      .addEnumProperty("paramB2", List.of("A", "B", "C"))
                      .build())
              .build(),
          ToolSpecification.builder()
              .name("toolC")
              .description("The third tool")
              .parameters(
                  JsonObjectSchema.builder()
                      .addStringProperty("paramC1", "The first parameter")
                      .build())
              .build());
}
