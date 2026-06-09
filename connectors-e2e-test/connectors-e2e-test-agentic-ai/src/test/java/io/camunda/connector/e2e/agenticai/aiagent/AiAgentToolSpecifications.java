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

import java.util.List;

/**
 * Framework-agnostic tool specification constants for AI agent e2e tests. Uses {@link ExpectedTool}
 * records (name + description) instead of framework-specific types so these lists remain valid as a
 * regression baseline when switching AI frameworks.
 */
public interface AiAgentToolSpecifications {

  /** Lightweight representation of an expected tool visible to the LLM. */
  record ExpectedTool(String name, String description) {}

  // ── Standard tools ──────────────────────────────────────────────────────────

  ExpectedTool GET_DATE_AND_TIME_TOOL =
      new ExpectedTool(
          "GetDateAndTime", "Returns the current date and time including the timezone.");

  ExpectedTool SUPERFLUX_PRODUCT_TOOL =
      new ExpectedTool(
          "SuperfluxProduct",
          "Calculates the superflux product (a very complicated method only this tool can do) given two input numbers");

  ExpectedTool SEARCH_THE_WEB_TOOL =
      new ExpectedTool("Search_The_Web", "Do a web search to find the needed information.");

  ExpectedTool DOWNLOAD_A_FILE_TOOL =
      new ExpectedTool("Download_A_File", "Download a file from the provided URL");

  ExpectedTool EXTERNAL_FILE_REFERENCE_TOOL =
      new ExpectedTool(
          "External_File_Reference",
          "Returns a reference to an externally hosted file (URL + display name) without downloading it.");

  ExpectedTool A_COMPLEX_TOOL = new ExpectedTool("A_Complex_Tool", "A very complex tool");

  ExpectedTool AN_EVENT_TOOL = new ExpectedTool("An_Event", "An event!");

  List<ExpectedTool> EXPECTED_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          A_COMPLEX_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          EXTERNAL_FILE_REFERENCE_TOOL,
          AN_EVENT_TOOL);

  // ── MCP tools ───────────────────────────────────────────────────────────────

  List<ExpectedTool> EXPECTED_MCP_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          new ExpectedTool("MCP_A_MCP_Client___toolA", "The first tool"),
          new ExpectedTool("MCP_A_MCP_Client___toolC", "The third tool"),
          new ExpectedTool("MCP_A_HTTP_Remote_MCP_Client___toolA", "The first tool"),
          new ExpectedTool("MCP_A_HTTP_Remote_MCP_Client___toolC", "The third tool"),
          new ExpectedTool("MCP_A_SSE_Remote_MCP_Client___toolA", "The first tool"),
          new ExpectedTool("MCP_A_SSE_Remote_MCP_Client___toolC", "The third tool"),
          new ExpectedTool("MCP_Filesystem_MCP_Flow___toolA", "The first tool"),
          new ExpectedTool("MCP_Filesystem_MCP_Flow___toolB", "The second tool"),
          new ExpectedTool("MCP_Filesystem_MCP_Flow___toolC", "The third tool"));

  // ── A2A tools ───────────────────────────────────────────────────────────────

  List<ExpectedTool> EXPECTED_A2A_TOOL_SPECIFICATIONS =
      List.of(
          GET_DATE_AND_TIME_TOOL,
          SUPERFLUX_PRODUCT_TOOL,
          SEARCH_THE_WEB_TOOL,
          DOWNLOAD_A_FILE_TOOL,
          new ExpectedTool(
              "A2A_Travel_Agent",
              """
              This tool allows interaction with the remote A2A agent represented by the following agent card:
              {"name":"Travel agent","description":"Helps with travel bookings","skills":[{"id":"hotel-booking","name":"Hotel Booking","description":"Book a hotel room","tags":["booking","hotel"],"examples":["Book a single room","Book a double room"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}"""),
          new ExpectedTool(
              "A2A_Weather_Agent",
              """
              This tool allows interaction with the remote A2A agent represented by the following agent card:
              {"name":"Weather agent","description":"Helps with weather information","skills":[{"id":"weather-forecast","name":"Weather Forecast","description":"Get weather forecast information","tags":["forecast","weather"],"examples":["What's the weather like today?","Will it rain tomorrow?"],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}"""),
          new ExpectedTool(
              "A2A_Exchange_Rate_Agent",
              """
              This tool allows interaction with the remote A2A agent represented by the following agent card:
              {"name":"Exchange rate agent","description":"Helps with exchanging currency rates","skills":[{"id":"currency-exchange","name":"Currency Exchange","description":"Get currency exchange rates","tags":["currency","exchange"],"examples":["What's the exchange rate from USD to EUR?","Convert 100 GBP to JPY."],"inputModes":["text"],"outputModes":["text"]}],"kind":"agentCard"}"""));
}
