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

import static io.camunda.connector.e2e.agenticai.BpmnUtil.updateInputMappings;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.McpTestServerTransport;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentMcpIntegrationTestSupport {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AgentMcpIntegrationTestSupport.class);

  public static final String ECHOED_META = "{\"exampleMetaKey\":\"exampleMetaValue\"}";

  /** Headers that the HTTP client and the MCP transports add, besides the configured ones. */
  private static final Set<String> TRANSPORT_HEADERS =
      Set.of(
          "accept",
          "cache-control",
          "connection",
          "content-length",
          "content-type",
          "host",
          "http2-settings",
          "last-event-id",
          "mcp-protocol-version",
          "mcp-session-id",
          "upgrade",
          "user-agent");

  private static final Set<McpClientRegistry> WARMED_UP_REGISTRIES = ConcurrentHashMap.newKeySet();

  private final McpTestServers mcpTestServers;
  private final McpClientRegistry mcpClientRegistry;
  private final ObjectMapper objectMapper;

  public AgentMcpIntegrationTestSupport(
      McpTestServers mcpTestServers,
      McpClientRegistry mcpClientRegistry,
      ObjectMapper objectMapper) {
    this.mcpTestServers = mcpTestServers;
    this.mcpClientRegistry = mcpClientRegistry;
    this.objectMapper = objectMapper;
  }

  /** The server ports are known only at runtime, so the remote gateways get their URL here. */
  public BpmnModelInstance setRemoteClientUrls(BpmnModelInstance model) {
    setRemoteClientUrl(model, "A_HTTP_Remote_MCP_Client", McpTestServerTransport.HTTP);
    setRemoteClientUrl(model, "A_SSE_Remote_MCP_Client", McpTestServerTransport.SSE);
    return model;
  }

  /**
   * Starts the stdio client once per Spring context, so that the start of its server container does
   * not count against the process wait of the first test. A failure only fails the assertions that
   * need the stdio client.
   */
  public void warmUpStdioClient() {
    if (!WARMED_UP_REGISTRIES.add(mcpClientRegistry)) {
      return;
    }

    try {
      mcpClientRegistry
          .getClient(McpTestServers.clientId(McpTestServerTransport.STDIO))
          .listTools(AllowDenyList.allowingEverything(), Map.of());
    } catch (Exception e) {
      LOGGER.warn("Failed to warm up the stdio MCP client", e);
    }
  }

  /** Asserts that the headers echoed for the tool call are exactly the configured ones. */
  public void assertEchoedHeaders(
      RecordedChatRequest request, String toolCallId, String expectedDummyValue)
      throws IOException {
    final var toolResult =
        request.messages().stream()
            .filter(message -> toolCallId.equals(message.toolCallId()))
            .findFirst()
            .orElseThrow();
    final Map<String, Object> echoed = new TreeMap<>();
    objectMapper
        .readValue(toolResult.textContent(), new TypeReference<Map<String, Object>>() {})
        .forEach((name, value) -> echoed.put(name.toLowerCase(Locale.ROOT), value));
    TRANSPORT_HEADERS.forEach(echoed::remove);

    assertThat(echoed)
        .as("headers echoed for tool call %s", toolCallId)
        .isEqualTo(Map.of("x-dummy", expectedDummyValue));
  }

  private void setRemoteClientUrl(
      BpmnModelInstance model, String elementId, McpTestServerTransport transport) {
    final var urlTarget = transport.remoteConnectorInputMappingPrefix() + ".url";
    final var url = mcpTestServers.remoteClientInputMappings(transport).get(urlTarget);
    updateInputMappings(model, elementId, Map.of(urlTarget, url));
  }
}
