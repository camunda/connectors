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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentMcpIntegrationTestSupport {

  private static final String ECHOED_META = "{\"exampleMetaKey\":\"exampleMetaValue\"}";

  public static final List<String> GATEWAYS =
      List.of(
          "A_MCP_Client",
          "A_HTTP_MCP_Client",
          "A_HTTP_Remote_MCP_Client",
          "A_SSE_Remote_MCP_Client",
          "Filesystem_MCP_Flow");

  private static final List<ScenarioToolCall> TOOL_CALLS =
      List.of(
          new ScenarioToolCall("stdio111", "MCP_A_MCP_Client___echoMeta", "{}", ECHOED_META),
          new ScenarioToolCall(
              "http222", "MCP_A_HTTP_MCP_Client___add", "{\"a\": 5, \"b\": 3}", "8"),
          new ScenarioToolCall("remote333", "MCP_A_HTTP_Remote_MCP_Client___echoMeta", "{}", "{}"),
          new ScenarioToolCall(
              "remote444", "MCP_A_HTTP_Remote_MCP_Client___echoHeaders", "{}", null),
          new ScenarioToolCall(
              "remote555", "MCP_A_SSE_Remote_MCP_Client___echoHeaders", "{}", null),
          new ScenarioToolCall(
              "sse666",
              "MCP_Filesystem_MCP_Flow___uppercase",
              "{\"message\": \"hello\"}",
              "HELLO"));

  /** How often the scenario calls a tool of each gateway. */
  public static final Map<String, Integer> TOOL_CALLS_BY_GATEWAY =
      TOOL_CALLS.stream()
          .collect(
              Collectors.groupingBy(ScenarioToolCall::gateway, Collectors.summingInt(call -> 1)));

  private final McpTestServers mcpTestServers;
  private final ObjectMapper objectMapper;

  public AgentMcpIntegrationTestSupport(McpTestServers mcpTestServers, ObjectMapper objectMapper) {
    this.mcpTestServers = mcpTestServers;
    this.objectMapper = objectMapper;
  }

  /** The tool calls of the scenario that calls every MCP gateway. */
  public static ToolCall[] toolCalls() {
    return TOOL_CALLS.stream().map(ScenarioToolCall::toolCall).toArray(ToolCall[]::new);
  }

  public static String[] toolCallNames() {
    return TOOL_CALLS.stream().map(ScenarioToolCall::name).toArray(String[]::new);
  }

  public static List<ScenarioToolCall> scenarioToolCalls() {
    return TOOL_CALLS;
  }

  /**
   * Sets the URLs of the remote client gateways. The server ports are known only at runtime.
   * Returns a customized copy and leaves the given model unchanged.
   */
  public BpmnModelInstance configureRemoteClientUrls(BpmnModelInstance model) {
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

  /**
   * Asserts that the headers echoed for the tool call contain the configured header. The transports
   * add more headers, which the assertion ignores.
   */
  private void assertEchoedHeaders(
      RecordedChatRequest request, String toolCallId, String expectedDummyValue)
      throws IOException {
    final var toolResult =
        request.messages().stream()
            .filter(message -> toolCallId.equals(message.toolCallId()))
            .findFirst()
            .orElseThrow();
    final Map<String, Object> echoed =
        objectMapper
            .readValue(toolResult.textContent(), new TypeReference<Map<String, Object>>() {})
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));

    assertThat(echoed)
        .as("headers echoed for tool call %s", toolCallId)
        .containsEntry("x-dummy", expectedDummyValue);
  }

  /** Asserts that every gateway element completed once, for scenarios without MCP tool calls. */
  public void assertGatewayCompletionsOfToolDiscoveryOnly(ProcessInstanceEvent processInstance) {
    assertGatewayCompletions(processInstance, Map.of());
  }

  /**
   * Asserts how often each gateway element completed. Tool discovery activates every gateway once,
   * and every tool call activates its gateway once more. The Filesystem gateway is a flow, so its
   * inner Filesystem task completes as often as the flow element.
   */
  public void assertGatewayCompletions(
      ProcessInstanceEvent processInstance, Map<String, Integer> toolCallsByGateway) {
    assertThat(GATEWAYS)
        .as("gateways named in the expected tool calls")
        .containsAll(toolCallsByGateway.keySet());
    for (final var gateway : GATEWAYS) {
      final var expected = 1 + toolCallsByGateway.getOrDefault(gateway, 0);
      CamundaAssert.assertThat(processInstance).hasCompletedElement(gateway, expected);
      if (gateway.equals("Filesystem_MCP_Flow")) {
        CamundaAssert.assertThat(processInstance).hasCompletedElement("Filesystem", expected);
      }
    }
  }

  /**
   * A tool call of the scenario that calls every MCP gateway, and the text of its result. The tool
   * name is {@code MCP_<gateway>___<tool>}.
   */
  public record ScenarioToolCall(String toolCallId, String name, String arguments, String text) {

    private ToolCall toolCall() {
      return ToolCall.of(toolCallId, name, arguments);
    }

    private String gateway() {
      return name.substring("MCP_".length(), name.indexOf("___"));
    }
  }

  private void setRemoteClientUrl(
      BpmnModelInstance model, String elementId, McpTestServerTransport transport) {
    final var urlTarget = transport.remoteConnectorInputMappingPrefix() + ".url";
    final var url = mcpTestServers.remoteClientInputMappings(transport).get(urlTarget);
    updateInputMappings(model, elementId, Map.of(urlTarget, url));
  }
}
