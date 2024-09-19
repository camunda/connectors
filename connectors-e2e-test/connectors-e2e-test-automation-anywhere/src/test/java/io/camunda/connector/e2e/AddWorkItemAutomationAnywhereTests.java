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

import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class AddWorkItemAutomationAnywhereTests extends BaseAutomationAnywhereTest {

  @Test
  void addItemTokenAuth() {
    wm.stubFor(
        post(urlPathMatching(WORK_ITEMS_ADD_URL))
            .withHeader(AUTH_TOKEN_HEADER, matching(AUTH_TOKEN))
            .withRequestBody(
                WireMock.equalToJson(EXPECTED_REQUEST_BODY_JSON_WITH_ITEM, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(ADD_ITEM_RESPONSE)));

    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("automationAnywhereTask")
            .endEvent()
            .done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("operation.type", "addWorkItemsToTheQueue")
            .property("configuration.controlRoomUrl", "http://localhost:" + wm.getPort())
            .property("authentication.type", "tokenBasedAuthentication")
            .property("authentication.token", AUTH_TOKEN)
            .property("operation.queueId", "12")
            .property(
                "operation.data", "={\"last_name\":\"Doe\",\"email\":\"jane.doe@example.com\"}")
            .property("resultExpression", "={itemId:response.body.list[1].id}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "automationAnywhereTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("itemId", 31250);
  }

  @Test
  void addItemPasswordBasedAuth() {
    wm.stubFor(
        post(urlPathMatching(AUTHENTICATION_URL))
            .withRequestBody(
                WireMock.equalToJson(EXPECTED_PASSWORD_BASED_AUTH_REQUEST, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(AUTHENTICATION_RESPONSE)));
    wm.stubFor(
        post(urlPathMatching(WORK_ITEMS_ADD_URL))
            .withHeader(AUTH_TOKEN_HEADER, matching(AUTH_TOKEN))
            .withRequestBody(
                WireMock.equalToJson(EXPECTED_REQUEST_BODY_JSON_WITH_ITEM, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(ADD_ITEM_RESPONSE)));

    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("automationAnywhereTask")
            .endEvent()
            .done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("configuration.controlRoomUrl", "http://localhost:" + wm.getPort())
            .property("operation.type", "addWorkItemsToTheQueue")
            .property("authentication.type", "passwordBasedAuthentication")
            .property("operation.queueId", "12")
            .property("authentication.multipleLogin", "true")
            .property("authentication.password", "password")
            .property("authentication.passwordBassesUsername", "username")
            .property(
                "operation.data", "={\"last_name\":\"Doe\",\"email\":\"jane.doe@example.com\"}")
            .property("resultExpression", "={itemId:response.body.list[1].id}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "automationAnywhereTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("itemId", 31250);
  }

  @Test
  void addItemApiKeyBasedAuth() {
    wm.stubFor(
        post(urlPathMatching(AUTHENTICATION_URL))
            .withRequestBody(WireMock.equalToJson(EXPECTED_API_KEY_BASED_AUTH_REQUEST, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(AUTHENTICATION_RESPONSE)));
    wm.stubFor(
        post(urlPathMatching(WORK_ITEMS_ADD_URL))
            .withHeader(AUTH_TOKEN_HEADER, matching(AUTH_TOKEN))
            .withRequestBody(WireMock.equalToJson(EXPECTED_REQUEST_BODY_JSON_WITH_ITEM, true, true))
            .willReturn(ResponseDefinitionBuilder.okForJson(ADD_ITEM_RESPONSE)));

    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("automationAnywhereTask")
            .endEvent()
            .done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("configuration.controlRoomUrl", "http://localhost:" + wm.getPort())
            .property("operation.type", "addWorkItemsToTheQueue")
            .property("authentication.type", "apiKeyAuthentication")
            .property("operation.queueId", "12")
            .property("authentication.apiUsername", "apiUserName")
            .property("authentication.apiKey", "myApiKey")
            .property(
                "operation.data", "={\"last_name\":\"Doe\",\"email\":\"jane.doe@example.com\"}")
            .property("resultExpression", "={itemId:response.body.list[1].id}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "automationAnywhereTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("itemId", 31250);
  }
}
