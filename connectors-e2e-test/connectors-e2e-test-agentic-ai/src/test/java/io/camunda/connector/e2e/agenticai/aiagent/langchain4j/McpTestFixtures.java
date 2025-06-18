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
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;

public interface McpTestFixtures {
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
