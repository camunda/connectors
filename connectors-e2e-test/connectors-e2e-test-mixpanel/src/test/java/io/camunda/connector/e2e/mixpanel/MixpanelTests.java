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
package io.camunda.connector.e2e.mixpanel;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Applies the real Mixpanel element template to service tasks and runs them against a WireMock
 * stand-in for the Mixpanel API.
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
class MixpanelTests {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/mixpanel/element-templates/mixpanel-connector.json";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @Test
  void importEventSendsTheEventWithATimeAndInsertId() {
    wm.stubFor(
        post(urlPathEqualTo("/import"))
            .withBasicAuth("sa-user", "sa-secret")
            .withQueryParam("project_id", equalTo("12345"))
            .withQueryParam("strict", equalTo("1"))
            .withRequestBody(matchingJsonPath("$[0].event", equalTo("Order shipped")))
            .withRequestBody(matchingJsonPath("$[0].properties.distinct_id", equalTo("user-1")))
            .withRequestBody(matchingJsonPath("$[0].properties.orderId", equalTo("o-1")))
            .withRequestBody(matchingJsonPath("$[0].properties.time"))
            .withRequestBody(matchingJsonPath("$[0].properties['$insert_id']"))
            .willReturn(
                okJson("{\"code\": 200, \"num_records_imported\": 1, \"status\": \"OK\"}")));

    File importEvent =
        template("import-events")
            .property("authentication.username", "sa-user")
            .property("authentication.password", "sa-secret")
            .property("import-events_eventName", "Order shipped")
            .property("import-events_distinctId", "user-1")
            .property("import-events_eventProperties", "={orderId: \"o-1\"}")
            .property("resultExpression", "={imported: response.body.num_records_imported}")
            .writeTo(new File(tempDir, "import.json"));

    var test = run(apply(importEvent, "importEvent"));

    assertThat(test.getProcessInstanceEvent()).hasVariable("imported", 1);
  }

  @Test
  void importEventRejectedByMixpanelRaisesAnIncident() {
    wm.stubFor(
        post(urlPathEqualTo("/import"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\": 400, \"error\": \"Some data points in the request failed"
                            + " validation.\", \"status\": \"Bad Request\"}")));

    File importEvent =
        template("import-events")
            .property("authentication.username", "sa-user")
            .property("authentication.password", "sa-secret")
            .property("import-events_eventName", "Order shipped")
            .property("import-events_distinctId", "user-1")
            .property("retryCount", "0")
            .writeTo(new File(tempDir, "rejected.json"));

    ZeebeTest.with(camundaClient)
        .deploy(apply(importEvent, "importEvent"))
        .createInstance()
        .waitForActiveIncidents();
  }

  @Test
  void querySavedReportAuthenticatesWithTheReusableCredential() {
    wm.stubFor(
        get(urlPathEqualTo("/insights"))
            .withBasicAuth("credential-user", "credential-secret")
            .withQueryParam("project_id", equalTo("12345"))
            .withQueryParam("bookmark_id", equalTo("678"))
            .withQueryParam("workspace_id", absent())
            .willReturn(okJson("{\"series\": {\"Signed up\": {\"2026-11-01T00:00:00Z\": 42}}}")));

    File queryReport =
        template("insights-query")
            .property(
                "serviceAuthenticationConfiguration",
                "={authentication: {type: \"basic\", username: \"credential-user\","
                    + " password: \"credential-secret\"}}")
            .property("insights-query_reportId", "678")
            .property(
                "resultExpression",
                "={signups: get value(response.body.series, [\"Signed up\","
                    + " \"2026-11-01T00:00:00Z\"])}")
            .writeTo(new File(tempDir, "report.json"));

    var test = run(apply(queryReport, "queryReport"));

    assertThat(test.getProcessInstanceEvent()).hasVariable("signups", 42);
  }

  /** Points the operation's hidden base URL at WireMock instead of the Mixpanel host. */
  private ElementTemplate template(String operationId) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("operationId", operationId)
        .property(operationId + "_baseUrl", wm.baseUrl())
        .property("projectId", "12345");
  }

  private BpmnModelInstance apply(File template, String taskId) {
    BpmnModelInstance model =
        Bpmn.createProcess().executable().startEvent().serviceTask(taskId).endEvent().done();
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, taskId + ".bpmn"))
        .apply(template, taskId, new File(tempDir, taskId + "-applied.bpmn"));
  }

  private ZeebeTest run(BpmnModelInstance model) {
    return ZeebeTest.with(camundaClient).deploy(model).createInstance().waitForProcessCompletion();
  }
}
