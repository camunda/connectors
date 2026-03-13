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
import io.camunda.connector.feel.CamundaClientFeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E tests for Supplier deserialization using CamundaClient. These tests verify cluster variable
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
public class FeelSupplierDeserializerCamundaClientTest {

  private static final Duration CLUSTER_VAR_TIMEOUT = Duration.ofSeconds(30);

  @Autowired CamundaClient camundaClient;

  private ObjectMapper mapper;

  @BeforeEach
  void setup() {
    var evaluator =
        new CamundaClientFeelExpressionEvaluator(
            camundaClient, ConnectorsObjectMapperSupplier.getCopy());
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
        "supplierStringVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "Hello from supplier")
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "supplier": "= camunda.vars.env.%s" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.supplier().get()).isEqualTo("Hello from supplier");
  }

  @Test
  void clusterVariable_integer() throws JsonProcessingException {
    // given
    String variableName =
        "supplierIntVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, 42)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "supplier": "= camunda.vars.env.%s + 8" }
        """
            .formatted(variableName);

    // when
    TargetTypeLong targetType = mapper.readValue(json, TargetTypeLong.class);

    // then
    assertThat(targetType.supplier().get()).isEqualTo(50L);
  }

  @Test
  void clusterVariable_nestedObject() throws JsonProcessingException {
    // given
    String variableName =
        "supplierNestedVar_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> nestedObject =
        Map.of("config", Map.of("url", "https://example.com", "timeout", 30));
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, nestedObject)
        .send()
        .join();
    awaitClusterVariableAvailable(variableName);

    String json =
        """
        { "supplier": "= camunda.vars.env.%s.config.url" }
        """
            .formatted(variableName);

    // when
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then
    assertThat(targetType.supplier().get()).isEqualTo("https://example.com");
  }

  // ============ NEGATIVE / ERROR HANDLING TESTS ============

  @Test
  void invalidFeelExpression_throwsExceptionOnGet() throws JsonProcessingException {
    // given - invalid FEEL syntax
    String json =
        """
        { "supplier": "= this is not valid FEEL !!!" }
        """;

    // when - deserialization succeeds (expression is captured, not evaluated)
    TargetTypeString targetType = mapper.readValue(json, TargetTypeString.class);

    // then - exception thrown when supplier is invoked
    assertThatThrownBy(() -> targetType.supplier().get()).isInstanceOf(RuntimeException.class);
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
  private record TargetTypeString(Supplier<String> supplier) {}

  private record TargetTypeLong(Supplier<Long> supplier) {}
}
