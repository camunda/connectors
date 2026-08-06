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
package io.camunda.connector.e2e.agenticai.e2e;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real-LLM CPT coverage for the Google Vertex AI provider, mirroring {@link AiAgentE2ETestIT}'s
 * OpenAI scenarios so Vertex AI gets the same automated coverage on every build.
 *
 * <p>Uses {@code gemini-2.5-flash}, the model version supported by the Vertex AI provider
 * implementation currently on {@code main} (the sunset {@code langchain4j-vertex-ai-gemini} SDK).
 * It does not exercise Gemini 3 tool calling or the {@code thoughtSignature} round-trip fixed in PR
 * #8178 (migration to the {@code google-genai} SDK) — that migration lives on {@code stable/8.9}
 * only and has not been forward-ported to {@code main} yet, so the code path it fixes doesn't exist
 * here. See this test's PR description for details.
 *
 * <p>The feedback-loop-with-joke and multi-tool-call scenarios from {@link AiAgentE2ETestIT} are
 * intentionally omitted here to keep real-LLM run cost/time bounded; the shared tools (Jokes_API,
 * GetDateAndTime) remain available in the BPMN for future coverage.
 *
 * <p>This class also carries a fourth, Vertex-only scenario, {@link
 * #shouldInferOrderStatusToolFromNaturalRequest()}, added after a QA engineer reviewing this PR
 * pointed out that {@link #shouldCompleteWithUserLookupTool()}'s prompt explicitly says "Use your
 * user lookup tool..." — i.e. it tells the model which tool to invoke rather than letting it infer
 * that from an ordinary request. That only exercises tool <em>execution</em>, not tool
 * <em>selection</em>, which is what a real user interaction would exercise. {@code
 * shouldCompleteWithUserLookupTool} is kept as-is (an explicit-invocation regression test for the
 * tool-calling/result plumbing itself), and the new test — an unprompted, natural-language,
 * customer-support-style order status question against the Vertex-only {@code GetOrderStatus} tool
 * — covers the tool-inference gap instead of replacing it.
 */
@SpringBootTest(classes = AiAgentE2ETestApplication.class)
@CamundaSpringProcessTest
@ActiveProfiles("it-real-llm")
@EnabledIfEnvironmentVariable(named = "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT", matches = ".+")
public class GoogleVertexAiE2ETestIT extends AbstractAiAgentE2ETestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e-google-vertex-ai.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e-google-vertex-ai";

  @Test
  void shouldCompleteHappyPath() {
    // given
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Map.of("inputText", "What is the current date and time in Berlin?"))
            .send()
            .join();

    // then — wait for the user task to appear (CamundaAssert polls internally via
    // setAssertionTimeout)
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    // complete the user task with satisfaction
    var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long taskKey = tasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(taskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();

    // then — process should complete with an agent response containing Berlin date/time info
    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that includes a specific time value (hours and minutes) and explicitly references the CET or CEST timezone or the city name Berlin");
  }

  @Test
  void shouldCompleteWithUserLookupTool() {
    // given — deliberately an explicit-invocation test: the prompt names the tool outright, so
    // this exercises tool *execution* (calling ListUsers and using its result) rather than tool
    // *selection*. See shouldInferOrderStatusToolFromNaturalRequest() below for the complementary
    // natural-inference scenario a QA reviewer asked for; both are kept since they cover different
    // failure modes.
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when — prompt explicitly requires the ListUsers HTTP connector tool
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use your user lookup tool to list available users and tell me the name of the first user you find"))
            .send()
            .join();

    // then — wait for user task (agent called ListUsers HTTP tool and responded)
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long taskKey = tasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(taskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that names one of the known users:"
                + " Leanne Graham or Ervin Howell, proving the ListUsers tool was invoked");
  }

  @Test
  void shouldInferOrderStatusToolFromNaturalRequest() {
    // given — a natural, customer-support-style request that does not name any tool; the model
    // has to infer on its own that a lookup tool (GetOrderStatus) is needed, exercising tool
    // *selection* rather than tool *execution* (see class javadoc)
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Hi, can you check the status of my order for me? The order ID is"
                        + " ORD-1001."))
            .send()
            .join();

    // then — wait for user task (agent inferred it needed the GetOrderStatus HTTP tool and
    // responded)
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long taskKey = tasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(taskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that references the order status"
                + " 'shipped' and the tracking number 1Z999AA10123456784 for order ORD-1001,"
                + " proving the GetOrderStatus tool was invoked");
  }

  @Test
  void shouldRetainToolResultAcrossFeedbackLoop() {
    // given — forces at least two model-call turns through the real Vertex AI provider, so any
    // regression in conversation-history round-tripping (tool calls, tool results, or — once the
    // google-genai migration lands on main — Gemini 3's thoughtSignature) would surface here.
    // GetDateAndTime stays available and callable more than once for the whole conversation, so the
    // assertion at the end of this test additionally proves the tool only actually executed once —
    // otherwise the model calling it again on the second turn could satisfy the judge below even
    // with broken context retention (see review discussion on PR #8226).
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when — first turn: ask for the current time (forces GetDateAndTime tool)
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use your date and time tool to tell me the exact current date and time"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    var firstTasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long firstTaskKey = firstTasks.items().getFirst().getUserTaskKey();

    // follow-up explicitly references the previously retrieved time — tests conversation context
    // across a second model call
    camundaClient
        .newCompleteUserTaskCommand(firstTaskKey)
        .variables(
            Map.of(
                "userSatisfied",
                false,
                "followUpInput",
                "Based on the time you just looked up, please restate that exact time back to"
                    + " me to confirm you remember it."))
        .send()
        .join();

    // wait for second user task
    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var tasks =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
                      .send()
                      .join();
              assertThat(
                      tasks.items().stream()
                          .filter(t -> t.getUserTaskKey() != firstTaskKey)
                          .toList())
                  .isNotEmpty();
            });

    long secondTaskKey =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join()
            .items()
            .stream()
            .filter(t -> t.getUserTaskKey() != firstTaskKey)
            .findFirst()
            .orElseThrow()
            .getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(secondTaskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();

    // then — agent answered the follow-up using the tool result retained in conversation context
    assertThatProcessInstance(processInstance).isCompleted();

    // GetDateAndTime is a real script task (FEEL now()) and the system prompt allows calling the
    // same tool again, so nothing but conversation-history retention stops the model from just
    // re-invoking it on the second turn and satisfying the judge below with a fresh, near-identical
    // timestamp even if round-tripping is broken. Asserting it only ever completed once across the
    // whole process instance (both turns combined) rules that out: the follow-up's time reference
    // can only have come from the retained first-turn tool result.
    assertThatProcessInstance(processInstance).hasCompletedElement("GetDateAndTime", 1);

    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that references the specific time"
                + " value (hours and minutes) returned by the earlier GetDateAndTime tool call,"
                + " proving the tool result was retained across the conversation turn");
  }
}
