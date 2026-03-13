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
package io.camunda.connector.e2e.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.feel.jackson.CamundaClientFeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E tests for FEEL expression deserialization using CamundaClient for remote evaluation. These
 * tests focus on cluster variable access (camunda.vars.env.*) and error handling scenarios that
 * only work with the CamundaClient API.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
public class FeelDeserializerCamundaClientTest {

  private static final Duration CLUSTER_VAR_TIMEOUT = Duration.ofSeconds(30);

  @Autowired CamundaClient camundaClient;

  private ObjectMapper mapper;

  @BeforeEach
  void setup() {
    var evaluator = new CamundaClientFeelExpressionEvaluator(() -> camundaClient);
    mapper =
        new ObjectMapper()
            .registerModule(new JacksonModuleFeelFunction(true, evaluator))
            .registerModule(new JavaTimeModule());
  }

  // ============ CLUSTER VARIABLE TESTS ============

  @Test
  void clusterVariable_string() throws JsonProcessingException {
    // given
    String variableName =
        "testStringVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "Hello from cluster")
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "props": "= camunda.vars.env.%s" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.props()).isEqualTo("Hello from cluster");
  }

  @Test
  void clusterVariable_integer() throws JsonProcessingException {
    // given
    String variableName = "testIntVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, 42)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "props": "= camunda.vars.env.%s + 8" }
        """
            .formatted(variableName);

    // when
    TargetTypeLong targetType = mapper.readValue(json, TargetTypeLong.class);

    // then
    assertThat(targetType.props()).isEqualTo(50L);
  }

  @Test
  void clusterVariable_nestedObject() throws JsonProcessingException {
    // given
    String variableName =
        "testNestedVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> nestedObject =
        Map.of("person", Map.of("name", "Alice", "address", Map.of("city", "Berlin")));
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, nestedObject)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "props": "= camunda.vars.env.%s.person.address.city" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.props()).isEqualTo("Berlin");
  }

  @Test
  void clusterVariable_complexExpression() throws JsonProcessingException {
    // given
    String priceVar = "price_" + java.util.UUID.randomUUID().toString().replace("-", "");
    String quantityVar = "quantity_" + java.util.UUID.randomUUID().toString().replace("-", "");

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(priceVar, 100)
        .send()
        .join();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(quantityVar, 5)
        .send()
        .join();
    awaitClusterVariableAvailable(priceVar);
    awaitClusterVariableAvailable(quantityVar);

    String json =
        """
        { "props": "= camunda.vars.env.%s * camunda.vars.env.%s" }
        """
            .formatted(priceVar, quantityVar);

    // when
    TargetTypeLong targetType = mapper.readValue(json, TargetTypeLong.class);

    // then - 100 * 5 = 500
    assertThat(targetType.props()).isEqualTo(500L);
  }

  @Test
  void clusterVariable_conditionalExpression() throws JsonProcessingException {
    // given
    String thresholdVar = "threshold_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(thresholdVar, 50)
        .send()
        .join();
    awaitClusterVariableAvailable(thresholdVar);

    String json =
        """
        { "props": "= if 75 > camunda.vars.env.%s then \\"above\\" else \\"below\\"" }
        """
            .formatted(thresholdVar);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.props()).isEqualTo("above");
  }

  // ============ NEGATIVE / ERROR HANDLING TESTS ============

  @Test
  void invalidFeelExpression_throwsException() {
    // given - invalid FEEL syntax
    String json =
        """
        { "props": "= this is not valid FEEL !!!" }
        """;

    // when/then
    assertThatThrownBy(() -> mapper.readValue(json, TargetTypeString.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void nonExistentClusterVariable_returnsNull() throws JsonProcessingException {
    // given - reference to non-existent cluster variable
    String json =
        """
        { "props": "= camunda.vars.env.nonExistentVariable12345" }
        """;

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then - non-existent variables resolve to null in FEEL
    assertThat(targetType.props()).isNull();
  }

  /**
   * Waits until a cluster variable is available (not returning 404). There is a delay between
   * creating a cluster variable and it being ready to use.
   */
  private void awaitClusterVariableAvailable(String variableName) {
    Awaitility.await("cluster variable " + variableName + " should be available")
        .atMost(CLUSTER_VAR_TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptionsMatching(
            e -> e instanceof ProblemException && e.getMessage().contains("404"))
        .until(
            () -> {
              try {
                var result =
                    camundaClient
                        .newEvaluateExpressionCommand()
                        .expression("=camunda.vars.env." + variableName)
                        .send()
                        .join();
                return result.getResult() != null;
              } catch (Exception e) {
                return false;
              }
            });
  }

  // Target type records
  private record TargetTypeString(@FEEL String props) {}

  private record TargetTypeLong(@FEEL Long props) {}
}
