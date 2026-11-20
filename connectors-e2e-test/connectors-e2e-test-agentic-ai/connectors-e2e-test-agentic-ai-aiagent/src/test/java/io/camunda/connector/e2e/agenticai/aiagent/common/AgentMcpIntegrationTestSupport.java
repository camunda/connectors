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
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.McpTestServerTransport;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AgentMcpIntegrationTestSupport {

  /** The first stdio task of a run starts the server container. */
  public static final Duration STDIO_START_TIMEOUT = Duration.ofMinutes(1);

  private static final String ECHOED_META = "{\"exampleMetaKey\":\"exampleMetaValue\"}";

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

  public static final List<String> GATEWAYS =
      List.of(
          "A_MCP_Client",
          "A_HTTP_MCP_Client",
          "A_HTTP_Remote_MCP_Client",
          "A_SSE_Remote_MCP_Client",
          "Filesystem_MCP_Flow");

  private static final List<ToolCall> TOOL_CALLS =
      List.of(
          ToolCall.of("stdio111", "MCP_A_MCP_Client___echoMeta", "{}"),
          ToolCall.of("http222", "MCP_A_HTTP_MCP_Client___add", "{\"a\": 5, \"b\": 3}"),
          ToolCall.of("remote333", "MCP_A_HTTP_Remote_MCP_Client___echoMeta", "{}"),
          ToolCall.of("remote444", "MCP_A_HTTP_Remote_MCP_Client___echoHeaders", "{}"),
          ToolCall.of("remote555", "MCP_A_SSE_Remote_MCP_Client___echoHeaders", "{}"),
          ToolCall.of("sse666", "MCP_Filesystem_MCP_Flow___uppercase", "{\"message\": \"hello\"}"));

  private static final List<ExpectedToolResult> EXPECTED_TOOL_RESULTS =
      List.of(
          new ExpectedToolResult("stdio111", ECHOED_META),
          new ExpectedToolResult("http222", "8"),
          new ExpectedToolResult("remote333", "{}"),
          new ExpectedToolResult("remote444", null),
          new ExpectedToolResult("remote555", null),
          new ExpectedToolResult("sse666", "HELLO"));

  public static final Map<String, Integer> TOOL_CALLS_BY_GATEWAY =
      Map.of(
          "A_MCP_Client", 1,
          "A_HTTP_MCP_Client", 1,
          "A_HTTP_Remote_MCP_Client", 2,
          "A_SSE_Remote_MCP_Client", 1,
          "Filesystem_MCP_Flow", 1);

  private final McpTestServers mcpTestServers;
  private final ObjectMapper objectMapper;

  public AgentMcpIntegrationTestSupport(McpTestServers mcpTestServers, ObjectMapper objectMapper) {
    this.mcpTestServers = mcpTestServers;
    this.objectMapper = objectMapper;
  }

  /** The tool calls of the scenario that calls every MCP gateway. */
  public static ToolCall[] toolCalls() {
    return TOOL_CALLS.toArray(ToolCall[]::new);
  }

  public static String[] toolCallNames() {
    return TOOL_CALLS.stream().map(ToolCall::name).toArray(String[]::new);
  }

  public static List<ExpectedToolResult> expectedToolResults() {
    return EXPECTED_TOOL_RESULTS;
  }

  /**
   * The server ports are known only at runtime, so the remote gateways get their URL here. Returns
   * a customized copy and leaves the given model unchanged.
   */
  public BpmnModelInstance setRemoteClientUrls(BpmnModelInstance model) {
    final var customized = model.clone();
    setRemoteClientUrl(customized, "A_HTTP_Remote_MCP_Client", McpTestServerTransport.HTTP);
    setRemoteClientUrl(customized, "A_SSE_Remote_MCP_Client", McpTestServerTransport.SSE);
    return customized;
  }

  /** Asserts the headers echoed to the remote HTTP and SSE tool calls of the scenario. */
  public void assertEchoedHeaders(RecordedChatRequest request) throws IOException {
    assertEchoedHeaders(request, "remote444", "HTTP");
    assertEchoedHeaders(request, "remote555", "SSE");
  }

  /** Asserts that the headers echoed for the tool call are exactly the configured ones. */
  private void assertEchoedHeaders(
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

  /**
   * Asserts how often each gateway element completed. Tool discovery activates every gateway once,
   * and every tool call activates its gateway once more. The Filesystem gateway is a flow, so its
   * inner Filesystem task completes as often as the flow element.
   */
  public void assertGatewayCompletions(
      ProcessInstanceEvent processInstance, Map<String, Integer> toolCallsByGateway) {
    for (final var gateway : GATEWAYS) {
      final var expected = 1 + toolCallsByGateway.getOrDefault(gateway, 0);
      CamundaAssert.assertThat(processInstance).hasCompletedElement(gateway, expected);
      if (gateway.equals("Filesystem_MCP_Flow")) {
        CamundaAssert.assertThat(processInstance).hasCompletedElement("Filesystem", expected);
      }
    }
  }

  public record ExpectedToolResult(String toolCallId, String text) {}

  private void setRemoteClientUrl(
      BpmnModelInstance model, String elementId, McpTestServerTransport transport) {
    final var urlTarget = transport.remoteConnectorInputMappingPrefix() + ".url";
    final var url = mcpTestServers.remoteClientInputMappings(transport).get(urlTarget);
    updateInputMappings(model, elementId, Map.of(urlTarget, url));
  }
}
