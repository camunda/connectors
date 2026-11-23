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

import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.McpTestServerTransport;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Starts the stdio MCP server before the first test. The stdio client starts its server container
 * with its first request, and the start is slow on a loaded runner. This request starts the
 * container and initializes the cached client, so the tests find a running server. A failed start
 * fails the context with the cause, and not every stdio row with a timeout.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AgentMcpStdioWarmUp {

  @Bean
  ApplicationRunner warmUpStdioMcpClient(McpClientRegistry registry) {
    return args ->
        registry
            .getClient(McpTestServers.clientId(McpTestServerTransport.STDIO))
            .listTools(AllowDenyList.allowingEverything(), null);
  }
}
