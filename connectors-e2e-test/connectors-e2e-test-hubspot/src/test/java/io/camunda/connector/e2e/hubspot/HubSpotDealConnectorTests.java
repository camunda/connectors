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

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the deal operations of the template through the connector runtime against mocked HubSpot.
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
public class HubSpotDealConnectorTests extends BaseHubSpotTest {

  private static final String DEALS_PATH = "/crm/v3/objects/deals";

  private static final String CREATE_REQUEST_BODY =
      """
      {
        "properties": {
          "dealname": "Auto Loan - Jane Doe",
          "pipeline": "default",
          "dealstage": "appointmentscheduled",
          "amount": "20000"
        },
        "associations": [
          {
            "to": {"id": "123"},
            "types": [{"associationCategory": "HUBSPOT_DEFINED", "associationTypeId": 3}]
          }
        ]
      }
      """;

  private static final String UPDATE_REQUEST_BODY =
      """
      {"properties": {"dealstage": "qualifiedtobuy"}}
      """;

  private static final String ERROR_EXPRESSION =
      "=bpmnError(\"HUBSPOT_ERROR\", error.variables.response.body.message,"
          + " {hubspotStatus: error.code, hubspotMessage: error.variables.response.body.message})";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void createDealReturnsTheCreatedDeal() {
    wm.stubFor(
        post(urlPathEqualTo(DEALS_PATH))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withRequestBody(equalToJson(CREATE_REQUEST_BODY))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                        Map.of(
                            "id",
                            DEAL_ID,
                            "properties",
                            Map.of(
                                "dealname",
                                "Auto Loan - Jane Doe",
                                "dealstage",
                                "appointmentscheduled"),
                            "archived",
                            false))
                    .withStatus(201)));

    var template =
        createDealWithContact()
            .property("resultExpression", "={status: response.status, dealId: response.body.id}");
    var test = run(applyTemplate(template), Map.of()).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("status", 201)
        .hasVariable("dealId", DEAL_ID);
  }

  @Test
  void updateDealMovesTheDealToAnotherStage() {
    wm.stubFor(
        patch(urlPathEqualTo(DEALS_PATH + "/" + DEAL_ID))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withRequestBody(equalToJson(UPDATE_REQUEST_BODY))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of(
                        "id",
                        DEAL_ID,
                        "properties",
                        Map.of("dealstage", "qualifiedtobuy"),
                        "archived",
                        false))));

    var template =
        updateDealStage("=dealId")
            .property("resultExpression", "={dealstage: response.body.properties.dealstage}");
    var test = run(applyTemplate(template), Map.of("dealId", DEAL_ID)).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent()).hasVariable("dealstage", "qualifiedtobuy");
  }

  static Stream<Arguments> hubSpotErrors() {
    return Stream.of(
        Arguments.of(
            named("400 on create", CREATE_DEAL),
            400,
            "Property values were not valid: dealstage is not a valid stage of pipeline default"),
        Arguments.of(named("404 on update", UPDATE_DEAL), 404, "Object not found."),
        Arguments.of(
            named("409 on create", CREATE_DEAL),
            409,
            "A deal with this unique property value already exists."),
        Arguments.of(
            named("429 on update", UPDATE_DEAL), 429, "You have reached your secondly limit."));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("hubSpotErrors")
  void hubSpotErrorIsAvailableToTheErrorExpression(String operation, int status, String message) {
    wm.stubFor(
        requestFor(operation)
            .willReturn(
                jsonResponse(
                    Map.of(
                        "status", "error",
                        "message", message,
                        "correlationId", "4c1ff5ba-7d45-4f4b-9b5c-3c1b1b7f2a10"),
                    status)));

    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask(TASK_ID)
            .boundaryEvent("hubspotError")
            .error()
            .zeebeOutput("=hubspotStatus", "hubspotStatus")
            .zeebeOutput("=hubspotMessage", "hubspotMessage")
            .endEvent()
            .done();
    var template = templateFor(operation).property("errorExpression", ERROR_EXPRESSION);
    var test =
        run(applyTemplate(template, model), Map.of("dealId", DEAL_ID)).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("hubspotStatus", String.valueOf(status))
        .hasVariable("hubspotMessage", message);
  }

  @Test
  void unhandledHubSpotErrorRaisesAnIncident() {
    wm.stubFor(
        requestFor(UPDATE_DEAL)
            .willReturn(
                jsonResponse(Map.of("status", "error", "message", "Object not found."), 404)));

    var template = updateDealStage("=dealId").property("retryCount", "0");

    run(applyTemplate(template), Map.of("dealId", DEAL_ID)).waitForActiveIncidents();
  }

  @Test
  void updateDealWithUnsetDealIdRaisesAnIncidentWithoutCallingHubSpot() {
    var template = updateDealStage("=dealId").property("retryCount", "0");

    run(applyTemplate(template), Map.of()).waitForActiveIncidents();

    wm.verify(0, anyRequestedFor(anyUrl()));
  }

  private MappingBuilder requestFor(String operation) {
    return CREATE_DEAL.equals(operation)
        ? post(urlPathEqualTo(DEALS_PATH))
        : patch(urlPathEqualTo(DEALS_PATH + "/" + DEAL_ID));
  }

  private ElementTemplate templateFor(String operation) {
    return CREATE_DEAL.equals(operation) ? createDealWithContact() : updateDealStage("=dealId");
  }

  private ElementTemplate createDealWithContact() {
    return mockedDealTemplate(CREATE_DEAL)
        .property(
            CREATE_DEAL_ASSOCIATIONS,
            "=[{to: {id: \"123\"}, types: [{associationCategory: \"HUBSPOT_DEFINED\","
                + " associationTypeId: 3}]}]");
  }

  private ElementTemplate updateDealStage(String dealId) {
    return mockedDealTemplate(UPDATE_DEAL)
        .property(UPDATE_DEAL_ID, dealId)
        .property(UPDATE_DEAL_PROPERTIES, "={dealstage: \"qualifiedtobuy\"}");
  }

  private ElementTemplate mockedDealTemplate(String operation) {
    return dealTemplate(operation).property("baseUrl", "http://localhost:" + wm.getPort());
  }
}
