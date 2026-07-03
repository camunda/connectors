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
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AiAgentE2ETestApplication.class)
@CamundaSpringProcessTest
@ActiveProfiles("it-real-llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AiAgentE2ETestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e-openai.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e-openai";
  private static final String HTTP_JSON_JOB_TYPE = "io.camunda:http-json:1";

  private static final String JOKE_1 =
      "Why did the AI cross the road? To process the chicken on the other side.";

  @Autowired private CamundaClient camundaClient;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(Duration.ofMinutes(3));
  }

  @BeforeEach
  void mockHttpTools() {
    // Intercept ListUsers and Jokes_API HTTP jobs — the HTTP connector is disabled in the Docker
    // bundle via CONNECTOR_OUTBOUND_DISABLED so these jobs stay open for the test to complete
    processTestContext
        .mockJobWorker(HTTP_JSON_JOB_TYPE)
        .withHandler(
            (jobClient, job) -> {
              var result =
                  switch (job.getElementId()) {
                    case "ListUsers" -> knownUsers();
                    case "Jokes_API" -> JOKE_1;
                    default -> null;
                  };
              var cmd = jobClient.newCompleteCommand(job);
              if (result != null) {
                cmd = cmd.variable("toolCallResult", result);
              }
              cmd.send().join();
            });
  }

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
  void shouldCompleteFeedbackLoop() {
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
            .variables(Map.of("inputText", "Tell me a joke"))
            .send()
            .join();

    // then — wait for the first user task (CamundaAssert polls internally via
    // setAssertionTimeout)
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    // complete with follow-up (not satisfied)
    var firstTasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long firstTaskKey = firstTasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(firstTaskKey)
        .variables(
            Map.of(
                "userSatisfied",
                false,
                "followUpInput",
                "Can you also tell me a fun fact about cats?"))
        .send()
        .join();

    // then — wait for a new user task after the follow-up loop
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
              // A new task should appear with a different key
              var activeTasks =
                  tasks.items().stream().filter(t -> t.getUserTaskKey() != firstTaskKey).toList();
              assertThat(activeTasks).isNotEmpty();
            });

    // complete the second user task with satisfaction
    var secondTasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long secondTaskKey =
        secondTasks.items().stream()
            .filter(t -> t.getUserTaskKey() != firstTaskKey)
            .findFirst()
            .orElseThrow()
            .getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(secondTaskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();

    // then — process completes
    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that mentions something about cats");
  }

  @Test
  void shouldCompleteWithUserLookupTool() {
    // given
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
  void shouldCompleteWithMultipleToolCalls() {
    // given
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when — explicitly request both the date/time tool and the jokes API in one prompt
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "I need two things: use your date and time tool to tell me the current time,"
                        + " and also use your jokes API tool to fetch a random joke for me"))
            .send()
            .join();

    // then — agent called both tools and produced a combined response
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
            "The agent variable contains a responseText field with a specific current time"
                + " (including hours and minutes) from the GetDateAndTime tool AND a complete"
                + " joke with a punchline from the Jokes_API tool, proving both tools were"
                + " invoked");
  }

  private static List<Map<String, Object>> knownUsers() {
    return List.of(
        Map.of("id", 1, "name", "Leanne Graham", "username", "Bret"),
        Map.of("id", 2, "name", "Ervin Howell", "username", "Antonette"));
  }

  @Test
  void shouldRetainToolResultAcrossFeedbackLoop() {
    // given
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
    camundaClient
        .newCompleteUserTaskCommand(firstTaskKey)
        .variables(
            Map.of(
                "userSatisfied",
                false,
                "followUpInput",
                "Based on the time you just looked up, is it currently daytime or nighttime?"))
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
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that says whether it is daytime or nighttime AND includes a specific time value (hours and minutes) from the GetDateAndTime tool, proving conversation context was retained");
  }
}
