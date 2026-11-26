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
package io.camunda.connector.e2e.snowflake;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Applies the real Snowflake element template to service tasks and runs them against a WireMock
 * stand-in for the Snowflake SQL API.
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
class SnowflakeTests {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/snowflake/element-templates/snowflake-connector.json";
  private static final String TOKEN = "inline-access-token";
  private static final String HANDLE = "01b2c3d4-0000-1111-0000-000000000001";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @Test
  void runSqlStatementPassesProcessVariablesAsBindingsAndReturnsTheRows() {
    wm.stubFor(
        snowflake(post(urlPathEqualTo("/api/v2/statements")), TOKEN)
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "statement": "select name from customers where id = ?",
                      "warehouse": "MY_WH",
                      "bindings": {"1": {"type": "TEXT", "value": "C-42"}}
                    }\
                    """))
            .willReturn(
                okJson(
                    """
                    {
                      "code": "090001",
                      "statementHandle": "%s",
                      "resultSetMetaData": {"numRows": 1, "rowType": [{"name": "NAME", "type": "TEXT"}]},
                      "data": [["Ada"]]
                    }\
                    """
                        .formatted(HANDLE))));

    File runStatement =
        template("SubmitStatement")
            .property(
                "SubmitStatement_body",
                "={statement: \"select name from customers where id = ?\", warehouse: \"MY_WH\","
                    + " bindings: {\"1\": {type: \"TEXT\", value: string(customerId)}}}")
            .property(
                "resultExpression",
                "={customerName: response.body.data[1][1], status: response.status}")
            .writeTo(new File(tempDir, "run.json"));

    // A process variable named like an unset parameter must not be sent: a reused request ID
    // would make Snowflake return the earlier request's result instead of running the statement.
    var test =
        run(
            apply(singleTask("runStatement"), runStatement, "runStatement"),
            Map.of("customerId", "C-42", "requestId", "11111111-2222-3333-4444-555555555555"));

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("customerName", "Ada")
        .hasVariable("status", 200);
    // The request ID is left empty, so it is not sent. async and nullable carry Snowflake's
    // own defaults.
    var query = wm.getAllServeEvents().getFirst().getRequest().getQueryParams();
    assertThat(query.keySet()).containsExactlyInAnyOrder("async", "nullable");
    assertThat(query.get("async").firstValue()).isEqualTo("false");
    assertThat(query.get("nullable").firstValue()).isEqualTo("true");
  }

  @Test
  void asyncStatementReturnsAHandleThatGetStatementStatusResolves() {
    wm.stubFor(
        snowflake(post(urlPathEqualTo("/api/v2/statements")), TOKEN)
            .withQueryParam("async", equalTo("true"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "code": "333334",
                          "message": "Asynchronous execution in progress.",
                          "statementHandle": "%s",
                          "statementStatusUrl": "/api/v2/statements/%s"
                        }\
                        """
                            .formatted(HANDLE, HANDLE))));
    wm.stubFor(
        snowflake(get(urlPathEqualTo("/api/v2/statements/" + HANDLE)), TOKEN)
            .withQueryParam("partition", equalTo("1"))
            .willReturn(
                okJson("{\"statementHandle\": \"%s\", \"data\": [[\"42\"]]}".formatted(HANDLE))));

    File runStatement =
        template("SubmitStatement")
            .property("SubmitStatement_query_async", "true")
            .property("SubmitStatement_body", "={statement: \"call refresh_scores()\"}")
            .property(
                "resultExpression",
                "={handle: response.body.statementHandle, submitStatus: response.status}")
            .writeTo(new File(tempDir, "submit.json"));
    File getStatus =
        template("GetStatementStatus")
            .property("GetStatementStatus_path_statementHandle", "=handle")
            .property("GetStatementStatus_query_partition", "1")
            .property("resultExpression", "={score: response.body.data[1][1]}")
            .writeTo(new File(tempDir, "status.json"));

    BpmnModelInstance model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("submit")
            .serviceTask("getStatus")
            .endEvent()
            .done();
    model = apply(model, runStatement, "submit");
    model = apply(model, getStatus, "getStatus");

    var test = run(model, Map.of());

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("handle", HANDLE)
        .hasVariable("submitStatus", 202)
        .hasVariable("score", "42");
  }

  @Test
  void cancelStatementAuthenticatesWithTheReusableCredential() {
    wm.stubFor(
        snowflake(post(urlPathEqualTo("/api/v2/statements/" + HANDLE + "/cancel")), "pat-secret")
            .willReturn(
                okJson(
                    "{\"message\": \"successfully canceled\", \"statementHandle\": \"%s\"}"
                        .formatted(HANDLE))));

    File cancel =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operationId", "CancelStatement")
            .property("baseUrl", wm.baseUrl())
            .property(
                "serviceAuthenticationConfiguration",
                "={authentication: {type: \"bearer\", token: \"pat-secret\"}}")
            .property("CancelStatement_path_statementHandle", HANDLE)
            .property("resultExpression", "={cancelMessage: response.body.message}")
            .writeTo(new File(tempDir, "cancel.json"));

    // A process variable named body must not become the cancel request's body, and one named
    // like the REST connector's credential input must not replace the Snowflake credential.
    var test =
        run(
            apply(singleTask("cancel"), cancel, "cancel"),
            Map.of(
                "body",
                Map.of("statement", "drop table customers"),
                "authenticationConfiguration",
                Map.of(
                    "url",
                    "https://example.com",
                    "authentication",
                    Map.of("type", "bearer", "token", "unrelated-token"))));

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("cancelMessage", "successfully canceled");
    var request = wm.getAllServeEvents().getFirst().getRequest();
    assertThat(request.getBodyAsString()).isEmpty();
    assertThat(request.getQueryParams()).isEmpty();
  }

  /**
   * Matches what every SQL API call must send: the bearer token, JSON Accept and Content-Type
   * headers, a User-Agent and no token type header, which Snowflake infers from the token.
   */
  private static MappingBuilder snowflake(MappingBuilder request, String token) {
    return request
        .withHeader("Authorization", equalTo("Bearer " + token))
        .withHeader("Accept", equalTo("application/json"))
        // Snowflake answers 415 to a POST without a JSON content type, even with no body.
        .withHeader("Content-Type", containing("application/json"))
        .withHeader("User-Agent", equalTo("Camunda"))
        .withHeader("X-Snowflake-Authorization-Token-Type", absent());
  }

  private ElementTemplate template(String operationId) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("operationId", operationId)
        .property("baseUrl", wm.baseUrl())
        .property("authentication.token", TOKEN);
  }

  private static BpmnModelInstance singleTask(String taskId) {
    return Bpmn.createProcess().executable().startEvent().serviceTask(taskId).endEvent().done();
  }

  private BpmnModelInstance apply(BpmnModelInstance model, File template, String taskId) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, taskId + ".bpmn"))
        .apply(template, taskId, new File(tempDir, taskId + "-applied.bpmn"));
  }

  private ZeebeTest run(BpmnModelInstance model, Map<String, Object> variables) {
    return ZeebeTest.with(camundaClient)
        .deploy(model)
        .createInstance(variables)
        .waitForProcessCompletion();
  }
}
