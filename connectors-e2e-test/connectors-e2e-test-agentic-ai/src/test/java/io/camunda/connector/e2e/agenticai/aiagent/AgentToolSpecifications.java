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
package io.camunda.connector.e2e.agenticai.aiagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

/**
 * Tool specification constants for AI agent e2e tests. Uses {@link ToolDefinition} records (name +
 * description + input schema) so these lists serve as a full regression baseline for what the
 * connector sends to the LLM.
 */
public interface AgentToolSpecifications {

  // ── Standard tools ──────────────────────────────────────────────────────────

  String GET_DATE_AND_TIME_TOOL_NAME = "GetDateAndTime";
  String SUPERFLUX_PRODUCT_TOOL_NAME = "SuperfluxProduct";
  String SEARCH_THE_WEB_TOOL_NAME = "Search_The_Web";
  String DOWNLOAD_A_FILE_TOOL_NAME = "Download_A_File";

  List<ToolDefinition> EXPECTED_TOOL_SPECIFICATIONS = parseExpectedToolDefinitions();

  Map<String, ToolDefinition> EXPECTED_TOOL_SPECIFICATIONS_BY_NAME =
      parseExpectedToolDefinitions().stream()
          .collect(Collectors.toMap(ToolDefinition::name, Function.identity()));

  private static List<ToolDefinition> parseExpectedToolDefinitions() {
    try {
      var objectMapper = new ObjectMapper();
      JsonNode result =
          objectMapper
              .readTree(new ClassPathResource("expected-schema-result.json").getInputStream())
              .get("toolDefinitions");

      return objectMapper.treeToValue(result, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // ── MCP tools ───────────────────────────────────────────────────────────────
  // MCP tool schemas originate from the MCP server and are passed through without modification.
  // They do not include a "required" key unless the MCP server specifies one.

  private static ToolDefinition mcpTool(
      String prefixedName, String description, Map<String, Object> properties) {
    return ToolDefinition.builder()
        .name(prefixedName)
        .description(description)
        .inputSchema(
            Map.of("type", "object", "properties", properties, "required", Collections.emptyList()))
        .build();
  }

  private static Map<String, Object> mcpToolAProperties() {
    return Map.of(
        "paramA1", Map.of("type", "string", "description", "The first parameter"),
        "paramA2", Map.of("type", "number", "description", "The second parameter"));
  }

  private static Map<String, Object> mcpToolBProperties() {
    return Map.of(
        "paramB1", Map.of("type", "string", "description", "The first parameter"),
        "paramB2", Map.of("type", "string", "enum", List.of("A", "B", "C")));
  }

  private static Map<String, Object> mcpToolCProperties() {
    return Map.of("paramC1", Map.of("type", "string", "description", "The first parameter"));
  }

  List<ToolDefinition> EXPECTED_MCP_TOOL_SPECIFICATIONS =
      List.of(
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(GET_DATE_AND_TIME_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(SUPERFLUX_PRODUCT_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(SEARCH_THE_WEB_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(DOWNLOAD_A_FILE_TOOL_NAME),
          mcpTool("MCP_A_MCP_Client___toolA", "The first tool", mcpToolAProperties()),
          mcpTool("MCP_A_MCP_Client___toolC", "The third tool", mcpToolCProperties()),
          mcpTool("MCP_A_HTTP_Remote_MCP_Client___toolA", "The first tool", mcpToolAProperties()),
          mcpTool("MCP_A_HTTP_Remote_MCP_Client___toolC", "The third tool", mcpToolCProperties()),
          mcpTool("MCP_A_SSE_Remote_MCP_Client___toolA", "The first tool", mcpToolAProperties()),
          mcpTool("MCP_A_SSE_Remote_MCP_Client___toolC", "The third tool", mcpToolCProperties()),
          mcpTool("MCP_Filesystem_MCP_Flow___toolA", "The first tool", mcpToolAProperties()),
          mcpTool("MCP_Filesystem_MCP_Flow___toolB", "The second tool", mcpToolBProperties()),
          mcpTool("MCP_Filesystem_MCP_Flow___toolC", "The third tool", mcpToolCProperties()));

  // ── A2A tools ───────────────────────────────────────────────────────────────
  // A2A tool input schema is loaded from a2a/tool-input-schema.json in the connector resources.

  Map<String, Object> A2A_INPUT_SCHEMA =
      Map.of(
          "type", "object",
          "properties",
              Map.of(
                  "text",
                  Map.of(
                      "type", "string",
                      "description", "The request or the follow-up message to send to the agent."),
                  "taskId",
                  Map.of(
                      "type", "string",
                      "description",
                          "The ID of the task this message is part of. ONLY include if continuing an existing task (e.g., input-required); otherwise OMIT."),
                  "contextId",
                  Map.of(
                      "type", "string",
                      "description",
                          "The context ID for this message, used to group related interactions. OMIT on first message; include on subsequent messages."),
                  "referenceTaskIds",
                  Map.of(
                      "type",
                      "array",
                      "description",
                      "A list of other task IDs that this message references for additional context.",
                      "items",
                      Collections.emptyMap())),
          "required", List.of("text"));

  List<ToolDefinition> EXPECTED_A2A_TOOL_SPECIFICATIONS =
      List.of(
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(GET_DATE_AND_TIME_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(SUPERFLUX_PRODUCT_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(SEARCH_THE_WEB_TOOL_NAME),
          EXPECTED_TOOL_SPECIFICATIONS_BY_NAME.get(DOWNLOAD_A_FILE_TOOL_NAME),
          ToolDefinition.builder()
              .name("A2A_Travel_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Travel agent","description":"Helps with travel bookings","skills":[{"id":"hotel-booking","name":"Hotel Booking","description":"Book a hotel room","tags":["booking","hotel"],"examples":["Book a single room","Book a double room"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .inputSchema(A2A_INPUT_SCHEMA)
              .build(),
          ToolDefinition.builder()
              .name("A2A_Weather_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Weather agent","description":"Helps with weather information","skills":[{"id":"weather-forecast","name":"Weather Forecast","description":"Get weather forecast information","tags":["forecast","weather"],"examples":["What's the weather like today?","Will it rain tomorrow?"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .inputSchema(A2A_INPUT_SCHEMA)
              .build(),
          ToolDefinition.builder()
              .name("A2A_Exchange_Rate_Agent")
              .description(
                  """
                  This tool allows interaction with the remote A2A agent represented by the following agent card:
                  {"name":"Exchange rate agent","description":"Helps with exchanging currency rates","skills":[{"id":"currency-exchange","name":"Currency Exchange","description":"Get currency exchange rates","tags":["currency","exchange"],"examples":["What's the exchange rate from USD to EUR?","Convert 100 GBP to JPY."],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}""")
              .inputSchema(A2A_INPUT_SCHEMA)
              .build());
}
