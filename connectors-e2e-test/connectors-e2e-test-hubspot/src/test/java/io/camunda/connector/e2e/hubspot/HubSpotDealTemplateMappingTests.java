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
package io.camunda.connector.e2e.hubspot;

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
 * Checks the request that the template builds for the deal operations: the {@code url}, {@code
 * method}, {@code body} and {@code authentication} inputs, as the engine evaluates them. A test
 * worker takes the job instead of the connector, so no HTTP call is made.
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
public class HubSpotDealTemplateMappingTests extends BaseHubSpotTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String DEALS_URL = "https://api.hubspot.com/crm/v3/objects/deals";

  @Autowired CamundaProcessTestContext processTestContext;

  @Test
  void createDealPostsTheDefaultPropertiesWithABearerToken() {
    var variables = evaluate(applyTemplate(dealTemplate(CREATE_DEAL)), Map.of());

    assertThat(variables.get("url").asText()).isEqualTo(DEALS_URL);
    assertThat(variables.get("method").asText()).isEqualTo("POST");
    assertThat(variables.get("body"))
        .isEqualTo(
            json(
                """
                {
                  "properties": {
                    "dealname": "Auto Loan - Jane Doe",
                    "pipeline": "default",
                    "dealstage": "appointmentscheduled",
                    "amount": "20000"
                  },
                  "associations": []
                }
                """));

    var authentication = variables.get("authentication");
    assertThat(authentication.get("type").asText()).isEqualTo("bearer");
    assertThat(authentication.get("token").asText()).isEqualTo(ACCESS_TOKEN);
  }

  @Test
  void createDealSendsCustomPropertiesAndAssociations() {
    var template =
        dealTemplate(CREATE_DEAL)
            .property(
                CREATE_DEAL_PROPERTIES,
                "={dealname: \"Auto Loan - \" + customerName, dealstage: \"appointmentscheduled\","
                    + " amount: string(loanAmount), case_type: \"auto_loan\"}")
            .property(
                CREATE_DEAL_ASSOCIATIONS,
                "=[{to: {id: contactId}, types: [{associationCategory: \"HUBSPOT_DEFINED\","
                    + " associationTypeId: 3}]}]");

    var variables =
        evaluate(
            applyTemplate(template),
            Map.of("customerName", "Jane Doe", "loanAmount", 20000, "contactId", "123"));

    assertThat(variables.get("url").asText()).isEqualTo(DEALS_URL);
    assertThat(variables.get("body"))
        .isEqualTo(
            json(
                """
                {
                  "properties": {
                    "dealname": "Auto Loan - Jane Doe",
                    "dealstage": "appointmentscheduled",
                    "amount": "20000",
                    "case_type": "auto_loan"
                  },
                  "associations": [
                    {
                      "to": {"id": "123"},
                      "types": [{"associationCategory": "HUBSPOT_DEFINED", "associationTypeId": 3}]
                    }
                  ]
                }
                """));
  }

  static Stream<Arguments> dealIds() {
    return Stream.of(
        Arguments.of(named("text id", DEAL_ID), Map.of()),
        Arguments.of(named("FEEL number", "=" + DEAL_ID), Map.of()),
        Arguments.of(
            named("variable holding a number", "=dealId"),
            Map.of("dealId", Long.parseLong(DEAL_ID))),
        Arguments.of(named("variable holding text", "=dealId"), Map.of("dealId", DEAL_ID)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dealIds")
  void updateDealPatchesTheDealWithTheGivenProperties(
      String dealId, Map<String, Object> processVariables) {
    var template =
        dealTemplate(UPDATE_DEAL)
            .property(UPDATE_DEAL_ID, dealId)
            .property(
                UPDATE_DEAL_PROPERTIES,
                "={dealstage: \"qualifiedtobuy\", case_type: \"auto_loan\"}");

    var variables = evaluate(applyTemplate(template), processVariables);

    assertThat(variables.get("url").asText()).isEqualTo(DEALS_URL + "/" + DEAL_ID);
    assertThat(variables.get("method").asText()).isEqualTo("PATCH");
    assertThat(variables.get("body"))
        .isEqualTo(
            json(
                """
                {"properties": {"dealstage": "qualifiedtobuy", "case_type": "auto_loan"}}
                """));
  }

  @Test
  void updateDealWithUnsetDealIdGivesNullUrl() {
    var template =
        dealTemplate(UPDATE_DEAL)
            .property(UPDATE_DEAL_ID, "=dealId")
            .property(UPDATE_DEAL_PROPERTIES, "={dealstage: \"qualifiedtobuy\"}");

    var variables = evaluate(applyTemplate(template), Map.of());

    // A null URL fails the job. Without the id, the request would go to the deals collection.
    assertThat(variables.get("url").isNull()).isTrue();
  }

  /**
   * Runs the process with a test worker on a unique job type and returns the variables of the job,
   * which include the evaluated inputs.
   */
  private JsonNode evaluate(BpmnModelInstance model, Map<String, Object> processVariables) {
    var jobType = "hubspot-template-probe-" + UUID.randomUUID();
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
