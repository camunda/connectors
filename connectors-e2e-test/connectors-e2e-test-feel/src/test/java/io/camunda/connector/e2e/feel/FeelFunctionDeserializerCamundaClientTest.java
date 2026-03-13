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
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.feel.jackson.CamundaClientFeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E tests for Function deserialization using CamundaClient. These tests verify cluster variable
 * resolution and error handling scenarios.
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
public class FeelFunctionDeserializerCamundaClientTest {

  private static final Duration CLUSTER_VAR_TIMEOUT = Duration.ofSeconds(30);

  @Autowired CamundaClient camundaClient;

  private ObjectMapper mapper;

  @BeforeEach
  void setup() {
    var evaluator = new CamundaClientFeelExpressionEvaluator(camundaClient);
    mapper =
        new ObjectMapper()
            .registerModule(new JacksonModuleFeelFunction(true, evaluator))
            .registerModule(new JavaTimeModule());
  }

  // ============ CLUSTER VARIABLE TESTS ============

  @Test
  void clusterVariable_combinedWithInput() throws JsonProcessingException {
    // given - create a cluster variable with a greeting prefix
    String variableName =
        "functionPrefixVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "Hello, ")
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "function": "= camunda.vars.env.%s + name" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then - cluster variable combined with input
    InputContextName input = new InputContextName("World!");
    assertThat(targetType.function().apply(input)).isEqualTo("Hello, World!");
  }

  @Test
  void clusterVariable_integer() throws JsonProcessingException {
    // given
    String variableName =
        "functionMultiplierVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, 10)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "function": "= value * camunda.vars.env.%s" }
        """
            .formatted(variableName);

    // when
    TargetTypeLong targetType = mapper.readValue(json, TargetTypeLong.class);

    // then - input multiplied by cluster variable
    InputContextValue input = new InputContextValue(5);
    assertThat(targetType.function().apply(input)).isEqualTo(50L);
  }

  @Test
  void clusterVariable_nestedObject() throws JsonProcessingException {
    // given
    String variableName =
        "functionConfigVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> config =
        Map.of("settings", Map.of("maxRetries", 3, "baseUrl", "https://api.example.com"));
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, config)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "function": "= camunda.vars.env.%s.settings.baseUrl + path" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then - nested cluster variable combined with input
    InputContextPath input = new InputContextPath("/users");
    assertThat(targetType.function().apply(input)).isEqualTo("https://api.example.com/users");
  }

  // ============ NEGATIVE / ERROR HANDLING TESTS ============

  @Test
  void invalidFeelExpression_throwsExceptionOnApply() throws JsonProcessingException {
    // given - invalid FEEL syntax
    String json =
        """
        { "function": "= this is not valid FEEL !!!" }
        """;

    // when - deserialization succeeds (expression is captured, not evaluated)
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then - exception thrown when function is applied
    InputContextName input = new InputContextName("test");
    assertThatThrownBy(() -> targetType.function().apply(input))
        .isInstanceOf(RuntimeException.class);
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
  private record InputContextName(String name) {}

  private record InputContextValue(Integer value) {}

  private record InputContextPath(String path) {}

  private record TargetTypeString(Function<Object, String> function) {}

  private record TargetTypeLong(Function<InputContextValue, Long> function) {}
}
