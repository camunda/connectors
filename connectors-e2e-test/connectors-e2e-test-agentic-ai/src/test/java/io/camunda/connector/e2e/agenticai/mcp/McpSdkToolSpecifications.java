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
package io.camunda.connector.e2e.agenticai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

public class McpSdkToolSpecifications {

  private McpSdkToolSpecifications() {}

  public static final McpSchema.ListToolsResult MCP_TOOL_SPECIFICATIONS =
      new McpSchema.ListToolsResult(
          List.of(
              McpSchema.Tool.builder()
                  .name("toolA")
                  .description("The first tool")
                  .inputSchema(
                      new McpSchema.JsonSchema(
                          "object",
                          Map.of(
                              "paramA1",
                                  Map.of("type", "string", "description", "The first parameter"),
                              "paramA2",
                                  Map.of("type", "number", "description", "The second parameter")),
                          null,
                          null,
                          null,
                          null))
                  .build(),
              McpSchema.Tool.builder()
                  .name("toolB")
                  .description("The second tool")
                  .inputSchema(
                      new McpSchema.JsonSchema(
                          "object",
                          Map.of(
                              "paramB1",
                              Map.of("type", "string", "description", "The first parameter"),
                              "paramB2",
                              Map.of("type", "string", "enum", List.of("A", "B", "C"))),
                          null,
                          null,
                          null,
                          null))
                  .build(),
              McpSchema.Tool.builder()
                  .name("toolC")
                  .description("The third tool")
                  .inputSchema(
                      new McpSchema.JsonSchema(
                          "object",
                          Map.of(
                              "paramC1",
                              Map.of("type", "string", "description", "The first parameter")),
                          null,
                          null,
                          null,
                          null))
                  .build()),
          null);
}
