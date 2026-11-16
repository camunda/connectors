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
package io.camunda.connector.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks the request that the template builds: the {@code url}, {@code method}, {@code body} and
 * {@code authentication} inputs, as the engine evaluates them. A test worker takes the job instead
 * of the connector, so no HTTP call is made. This also covers SaaS, whose host cannot be mocked.
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
public class OrchestrationTemplateMappingTests extends BaseOrchestrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SAAS_API = "https://bru-2.zeebe.camunda.io/a1b2c3d4-e5f6/v2";
  private static final String SM_API = "https://camunda.example.com/v2";

  @Autowired CamundaProcessTestContext processTestContext;

  @Test
  void saasSearchUsesDefaultsAndSaasAuthentication() {
    var variables = evaluate(applyTemplate(saasTemplate("/process-instances", SEARCH)));

    assertThat(variables.get("url").asText()).isEqualTo(SAAS_API + "/process-instances/search");
    assertThat(variables.get("method").asText()).isEqualTo("post");
    assertThat(variables.get("body")).isEqualTo(json("{\"page\": {\"limit\": 100}}"));

    var authentication = variables.get("authentication");
    assertThat(authentication.get("type").asText()).isEqualTo("oauth-client-credentials-flow");
    assertThat(authentication.get("oauthTokenEndpoint").asText())
        .isEqualTo("https://login.cloud.camunda.io/oauth/token");
    assertThat(authentication.get("audience").asText()).isEqualTo("zeebe.camunda.io");
    assertThat(authentication.get("clientAuthentication").asText()).isEqualTo("credentialsBody");
    assertThat(authentication.get("clientId").asText()).isEqualTo("orchestration-client");
  }

  static Stream<Arguments> keys() {
    return Stream.of(
        Arguments.of(named("text key", KEY), Map.of()),
        Arguments.of(named("FEEL number", "=" + KEY), Map.of()),
        Arguments.of(
            named("variable holding a number", "=incidentKey"),
            Map.of("incidentKey", 2251799813685249L)),
        Arguments.of(named("variable holding text", "=incidentKey"), Map.of("incidentKey", KEY)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("keys")
  void getByKeyAppendsTheKeyToTheUrl(String key, Map<String, Object> processVariables) {
    var template =
        saasTemplate("/incidents", GET_BY_KEY).propertyByBinding("input.internal_key", key);

    var variables = evaluate(applyTemplate(template), processVariables);

    assertThat(variables.get("url").asText()).isEqualTo(SAAS_API + "/incidents/" + KEY);
    assertThat(variables.get("method").asText()).isEqualTo("get");
    assertThat(variables.has("body")).isFalse();
  }

  @Test
  void getByKeyWithUnsetVariableGivesNullUrl() {
    var template =
        saasTemplate("/incidents", GET_BY_KEY)
            .propertyByBinding("input.internal_key", "=incidentKey");

    var variables = evaluate(applyTemplate(template));

    // A null URL fails the job. Without the key, the request would go to /v2/incidents/.
    assertThat(variables.get("url").isNull()).isTrue();
  }

  /** Regression test for https://github.com/camunda/connectors/issues/10297. */
  @Test
  void getByKeyAfterUserEditsEntityOperationAndKey() {
    var template =
        saasTemplate("/incidents", GET_BY_KEY).propertyByBinding("input.internal_key", KEY);
    var model =
        moveInputsToEnd(
            applyTemplate(template),
            "input.internal_endpoint",
            "input.internal_operation",
            "input.internal_key");

    var variables = evaluate(model);

    assertThat(variables.get("url").asText()).isEqualTo(SAAS_API + "/incidents/" + KEY);
  }

  @Test
  void searchAfterUserEditsEntityAndFilter() {
    var template =
        saasTemplate("/user-tasks", SEARCH)
            .propertyByBinding("input.internal_filter", "={state: \"CREATED\"}");
    var model =
        moveInputsToEnd(
            applyTemplate(template),
            "input.internal_endpoint",
            "input.internal_operation",
            "input.internal_filter");

    var variables = evaluate(model);

    assertThat(variables.get("url").asText()).isEqualTo(SAAS_API + "/user-tasks/search");
    assertThat(variables.get("body"))
        .isEqualTo(json("{\"filter\": {\"state\": \"CREATED\"}, \"page\": {\"limit\": 100}}"));
  }

  @Test
  void selfManagedGetByKeyUsesBaseUrlAndCustomAuthentication() {
    var template =
        selfManagedTemplate(
                "/user-tasks", GET_BY_KEY, SM_API, "https://idp.example.com/oauth/token")
            .propertyByBinding("input.internal_key", KEY);

    var variables = evaluate(applyTemplate(template));

    assertThat(variables.get("url").asText()).isEqualTo(SM_API + "/user-tasks/" + KEY);
    assertThat(variables.get("method").asText()).isEqualTo("get");

    var authentication = variables.get("authentication");
    assertThat(authentication.get("oauthTokenEndpoint").asText())
        .isEqualTo("https://idp.example.com/oauth/token");
    assertThat(authentication.get("audience").asText()).isEqualTo("orchestration-api");
    assertThat(authentication.get("scopes").asText()).isEqualTo("orchestration");
  }

  @Test
  void searchBodyIncludesFilterSortAndPaging() {
    var template =
        saasTemplate("/process-instances", SEARCH)
            .propertyByBinding("input.internal_filter", "={state: \"ACTIVE\"}")
            .propertyByBinding("input.internal_sort", "=[{field: \"startDate\", order: \"DESC\"}]")
            .propertyByBinding("input.internal_results", "25")
            .propertyByBinding("input.internal_from", "10")
            .propertyByBinding("input.internal_after", "cursorA")
            .propertyByBinding("input.internal_before", "cursorB");

    var variables = evaluate(applyTemplate(template));

    assertThat(variables.get("body"))
        .isEqualTo(
            json(
                """
                {
                  "filter": {"state": "ACTIVE"},
                  "sort": [{"field": "startDate", "order": "DESC"}],
                  "page": {"after": "cursorA", "before": "cursorB", "from": 10, "limit": 25}
                }
                """));
  }

  @Test
  void searchBodyHasNoPageWhenPagingIsEmpty() {
    var template =
        saasTemplate("/process-instances", SEARCH).propertyByBinding("input.internal_results", "");

    var variables = evaluate(applyTemplate(template));

    assertThat(variables.get("body")).isEqualTo(json("{}"));
  }

  private JsonNode evaluate(BpmnModelInstance model) {
    return evaluate(model, Map.of());
  }

  /**
   * Runs the process with a test worker on a unique job type and returns the variables of the job,
   * which include the evaluated inputs.
   */
  private JsonNode evaluate(BpmnModelInstance model, Map<String, Object> processVariables) {
    var jobType = "orchestration-template-probe-" + UUID.randomUUID();
    var job = new AtomicReference<ActivatedJob>();
    processTestContext
        .mockJobWorker(jobType)
        .withHandler((client, activatedJob) -> {
          job.set(activatedJob);
          client.newCompleteCommand(activatedJob.getKey()).send().join();
        });

    run(withJobType(model, jobType), processVariables).waitForProcessCompletion();

    return json(job.get().getVariables());
  }

  private static JsonNode json(String value) {
    try {
      return MAPPER.readTree(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON: " + value, e);
    }
  }
}
