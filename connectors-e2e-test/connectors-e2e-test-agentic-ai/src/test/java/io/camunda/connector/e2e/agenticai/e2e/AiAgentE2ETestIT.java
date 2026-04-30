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

import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AiAgentE2ETestApplication.class)
@CamundaSpringProcessTest
@ActiveProfiles("e2e-real-llm")
public class AiAgentE2ETestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e-openai.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e-openai";

  @Autowired private CamundaClient camundaClient;

  @BeforeAll
  static void setUp() {
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(3));
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

    // then — wait for the user task to appear (CamundaAssert polls internally via setAssertionTimeout)
    CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

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

    // then — process should complete with an agent response containing date/time info
    CamundaAssert.assertThatProcessInstance(processInstance).isCompleted();
    CamundaAssert.assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field with information about the current date and time");
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

    // then — wait for the first user task (CamundaAssert polls internally via setAssertionTimeout)
    CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

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
              org.assertj.core.api.Assertions.assertThat(activeTasks).isNotEmpty();
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
    CamundaAssert.assertThatProcessInstance(processInstance).isCompleted();
    CamundaAssert.assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText field that mentions something about cats");
  }
}
