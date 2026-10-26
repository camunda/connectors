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
package io.camunda.connector.e2e.agenticai.aiagent.common;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the runtime tool {@value EchoRuntimeTool#NAME} for the tests that import it. */
@TestConfiguration
public class RuntimeToolTestConfiguration {

  @Bean
  public RuntimeTool echoRuntimeTool() {
    return new EchoRuntimeTool();
  }

  /** Returns the given text prefixed with {@value #RESULT_PREFIX}. */
  public static class EchoRuntimeTool implements RuntimeTool {

    public static final String NAME = "acme__echo";
    public static final String RESULT_PREFIX = "echo: ";

    private record Arguments(String text) {}

    @Override
    public ToolDefinition definition() {
      return ToolDefinition.builder()
          .name(NAME)
          .description("Returns the given text")
          .inputSchema(
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("text", Map.of("type", "string")),
                  "required",
                  List.of("text")))
          .build();
    }

    @Override
    public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
      return RuntimeToolResult.success(RESULT_PREFIX + call.argumentsAs(Arguments.class).text());
    }
  }
}
