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

import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared fixture for the real-LLM AI Agent CPT tests: common setup, the mocked HTTP tool job
 * worker, and the tool result fixtures used across provider-specific subclasses (e.g. {@link
 * AiAgentE2ETestIT}, {@link GoogleVertexAiE2ETestIT}).
 *
 * <p>Subclasses are expected to carry their own {@code @SpringBootTest},
 * {@code @CamundaSpringProcessTest}, {@code @ActiveProfiles}, and
 * {@code @EnabledIfEnvironmentVariable} annotations, along with their own BPMN/process id constants
 * and {@code @Test} scenarios.
 */
abstract class AbstractAiAgentE2ETestIT {

  static final String HTTP_JSON_JOB_TYPE = "io.camunda:http-json:1";

  static final String JOKE_1 =
      "Why did the AI cross the road? To process the chicken on the other side.";

  static final String ORDER_STATUS_TRACKING_NUMBER = "1Z999AA10123456784";

  @Autowired protected CamundaClient camundaClient;
  @Autowired protected CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(Duration.ofMinutes(3));
  }

  @BeforeEach
  void mockHttpTools() {
    // Intercept ListUsers, Jokes_API and (Vertex-only) GetOrderStatus HTTP jobs — the HTTP
    // connector is disabled in the Docker bundle via CONNECTOR_OUTBOUND_DISABLED so these jobs
    // stay open for the test to complete. Matching is done by element id (a single job worker per
    // job type, dispatching on the element that raised the job) rather than by request content.
    processTestContext
        .mockJobWorker(HTTP_JSON_JOB_TYPE)
        .withHandler(
            (jobClient, job) -> {
              var result =
                  switch (job.getElementId()) {
                    case "ListUsers" -> knownUsers();
                    case "Jokes_API" -> JOKE_1;
                    case "GetOrderStatus" -> orderStatus();
                    default -> null;
                  };
              var cmd = jobClient.newCompleteCommand(job);
              if (result != null) {
                cmd = cmd.variable("toolCallResult", result);
              }
              cmd.send().join();
            });
  }

  static List<Map<String, Object>> knownUsers() {
    return List.of(
        Map.of("id", 1, "name", "Leanne Graham", "username", "Bret"),
        Map.of("id", 2, "name", "Ervin Howell", "username", "Antonette"));
  }

  static Map<String, Object> orderStatus() {
    return Map.of(
        "orderId", "ORD-1001",
        "status", "shipped",
        "trackingNumber", ORDER_STATUS_TRACKING_NUMBER,
        "estimatedDelivery", "2026-08-10");
  }
}
