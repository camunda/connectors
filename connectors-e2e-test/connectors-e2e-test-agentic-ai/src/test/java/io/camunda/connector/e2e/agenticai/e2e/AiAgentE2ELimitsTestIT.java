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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AiAgentE2ETestApplication.class)
@CamundaSpringProcessTest
@ActiveProfiles("e2e-real-llm")
public class AiAgentE2ELimitsTestIT {

  private static final String LIMITED_BPMN_RESOURCE = "ai-agent-e2e-limited.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String LIMITED_PROCESS_ID = "ai-agent-e2e-limited";

  @Autowired private CamundaClient camundaClient;

  @BeforeAll
  static void setUp() {
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(3));
  }

  @Test
  void shouldRaiseIncidentWhenMaxModelCallsExceeded() {
    // given — process configured with maxModelCalls=2
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(LIMITED_BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    // when
    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(LIMITED_PROCESS_ID)
            .latestVersion()
            .variables(Map.of("inputText", "Write a haiku about the sea"))
            .send()
            .join();

    // first model call completes — user task is active
    CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

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
            Map.of("userSatisfied", false, "followUpInput", "Not satisfied, write another haiku"))
        .send()
        .join();

    // second model call completes — a new user task appears
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
              Assertions.assertThat(
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

    // completing with not-satisfied drives a 3rd model call, which exceeds the limit=2
    camundaClient
        .newCompleteUserTaskCommand(secondTaskKey)
        .variables(
            Map.of("userSatisfied", false, "followUpInput", "Still not satisfied, try again"))
        .send()
        .join();

    // then — the agent raises an incident because maxModelCalls=2 is exceeded
    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var incidents =
                  camundaClient
                      .newIncidentSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
                      .send()
                      .join();
              Assertions.assertThat(incidents.items()).isNotEmpty();
              Assertions.assertThat(incidents.items().getFirst().getErrorMessage())
                  .startsWith("Maximum number of model calls reached");
            });
  }
}
