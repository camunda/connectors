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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs.ServerToolUseTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/**
 * End-to-end coverage for an Anthropic Skills/code-execution turn driven through the REAL {@code
 * BetaMessageAccumulator} (via {@link StreamingAnthropicMessagesSseChatModelStubs}), closing the
 * coverage gap that let a native-path {@code server_tool_use}/{@code code_execution_tool_result}
 * bug ship undetected: unlike {@link AiAgentJobWorkerAnthropicSkillsAndToolsTests} (which only
 * drives a plain-text turn, since server-side skill/tool execution can't be emulated by WireMock,
 * to assert the outgoing *request* shape), this test's stubbed *response* itself contains {@code
 * server_tool_use} and {@code code_execution_tool_result} content blocks - the shape a real
 * Skills-enabled turn returns - and asserts both that the agent completes the turn without
 * mistaking either block for a client tool call, and that a follow-up model call's assistant
 * history round-trips those blocks back onto the wire losslessly.
 *
 * <p>Reuses {@link AiAgentJobWorkerAnthropicSkillsAndToolsTests}'s native-Anthropic element
 * template wiring (v2/own-LLM-layer template, {@code provider.anthropic.*} properties, skills
 * configured via {@code container.skills}).
 */
class AiAgentJobWorkerAnthropicCodeExecutionTests extends BaseAiAgentJobWorkerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  protected String elementTemplatePath() {
    return AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
  }

  /**
   * Overridden directly (rather than the {@code withOpenAiCompatibleProvider} hook {@link
   * BaseAiAgentJobWorkerTest#createProcessInstance} composes) so this test's native-Anthropic
   * provider configuration - not the openaiCompatible default - configures the element template.
   * Mirrors {@link AiAgentJobWorkerAnthropicSkillsAndToolsTests#createProcessInstance}.
   */
  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final Function<ElementTemplate, ElementTemplate> composed =
        ((Function<ElementTemplate, ElementTemplate>) this::configureAnthropicWithSkills)
            .andThen(elementTemplateModifier);
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), composed);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);
    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  /**
   * Points the connector at this test's WireMock server via the native (v2) Anthropic direct
   * backend and configures an Anthropic-hosted skill, so the turn under test is plausible as a real
   * Skills-enabled response (a skills-configured agent triggering code execution), mirroring {@link
   * AiAgentJobWorkerAnthropicSkillsAndToolsTests#configureAnthropicSkillsAndTools}.
   */
  private ElementTemplate configureAnthropicWithSkills(ElementTemplate template) {
    return template
        .property("provider.type", "anthropic")
        .property("provider.anthropic.backend.type", "direct")
        .property("provider.anthropic.backend.direct.endpoint", wireMock.getHttpBaseUrl())
        .property("provider.anthropic.backend.apiKey", "dummy")
        .property("provider.anthropic.model.model", "test-model")
        .property("provider.anthropic.skills", "=[\"pptx\"]");
  }

  @Test
  void completesTurnAndRoundTripsServerToolBlocksIntoFollowUpModelCall() throws Exception {
    final var userPrompt = "Use code execution to double check that 1 == 1.";
    final var followUpPrompt = "Can you also verify 2 == 2?";
    final var precedingText = "Let me verify that with code execution.";
    final var serverToolUseId = "srvtoolu_01ABC";
    final var codeInputJson = "{\"code\": \"print(1)\"}";
    final var stdout = "1\n";
    final var followingText = "Confirmed: 1 == 1.";
    final var satisfiedResponseText = "Great, glad that helped!";

    StreamingAnthropicMessagesSseChatModelStubs.stubServerToolUseConversation(
        new ServerToolUseTurnStub(
            precedingText, serverToolUseId, codeInputJson, stdout, followingText, 10, 20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    // The process reaching READY with zero tool calls proves the server-tool blocks were never
    // mistaken for a client tool call: the test process has no "code_execution" ad-hoc element, so
    // an (incorrect) attempt to dispatch one would have kept the process from completing rather
    // than completing with toolCalls == 0.
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 0))
                .hasResponseText(satisfiedResponseText));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    assertAssistantHistoryCarriesServerToolBlocks(
        secondRequest, serverToolUseId, codeInputJson, stdout);
  }

  /**
   * Asserts the second request's {@code messages[]} carries the first turn's assistant message with
   * its content blocks round-tripped, in original order, exactly as the domain {@code
   * ProviderContent} type preserves them: {@code text}, {@code server_tool_use}, {@code
   * code_execution_tool_result}, {@code text} - proving the response-side {@code ProviderContent}
   * capture and the request-side round-trip both work end to end through the real accumulator, not
   * just at the unit level.
   */
  private void assertAssistantHistoryCarriesServerToolBlocks(
      JsonNode request, String serverToolUseId, String codeInputJson, String stdout)
      throws Exception {
    final var assistantMessage =
        StreamSupport.stream(request.path("messages").spliterator(), false)
            .filter(message -> "assistant".equals(message.path("role").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found in second request"));

    final var contentBlocks = assistantMessage.path("content");
    final var contentBlockTypes =
        StreamSupport.stream(contentBlocks.spliterator(), false)
            .map(block -> block.path("type").asText())
            .toList();
    assertThat(contentBlockTypes)
        .as("assistant history content block types, in order")
        .containsExactly("text", "server_tool_use", "code_execution_tool_result", "text");

    final var serverToolUseBlock = contentBlocks.get(1);
    assertThat(serverToolUseBlock.path("id").asText())
        .as("round-tripped server_tool_use id")
        .isEqualTo(serverToolUseId);
    assertThat(serverToolUseBlock.path("name").asText())
        .as("round-tripped server_tool_use name")
        .isEqualTo("code_execution");
    assertThat(serverToolUseBlock.path("input"))
        .as("round-tripped server_tool_use input")
        .isEqualTo(OBJECT_MAPPER.readTree(codeInputJson));

    final var codeExecutionToolResultBlock = contentBlocks.get(2);
    assertThat(codeExecutionToolResultBlock.path("tool_use_id").asText())
        .as("round-tripped code_execution_tool_result tool_use_id")
        .isEqualTo(serverToolUseId);
    assertThat(codeExecutionToolResultBlock.path("content").path("stdout").asText())
        .as("round-tripped code_execution_tool_result content.stdout")
        .isEqualTo(stdout);
  }

  private static List<LoggedRequest> recordedLoggedRequests() {
    final List<LoggedRequest> requests =
        new ArrayList<>(findAll(postRequestedFor(urlPathEqualTo(MESSAGES_PATH))));
    requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
    return requests;
  }

  private static JsonNode parseBody(LoggedRequest loggedRequest) {
    try {
      return OBJECT_MAPPER.readTree(loggedRequest.getBodyAsString());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse recorded Anthropic messages request body: "
              + loggedRequest.getBodyAsString(),
          e);
    }
  }
}
