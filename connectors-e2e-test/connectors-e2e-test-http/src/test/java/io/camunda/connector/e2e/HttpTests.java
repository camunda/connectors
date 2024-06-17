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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.connector.e2e.BpmnFile.Replace.replace;
import static io.camunda.connector.e2e.BpmnFile.replace;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorManager;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.model.ProcessDefinition;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class HttpTests {

  @TempDir File tempDir;

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @Autowired InboundConnectorManager inboundManager;

  @Autowired CamundaOperateClient camundaOperateClient;

  @LocalServerPort int serverPort;

  @BeforeEach
  void beforeAll() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  @Test
  void basicAuth() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withQueryParam("testQueryParam", matching("testQueryParamValue"))
            .withHeader("testHeader", matching("testHeaderValue"))
            .withBasicAuth("username", "password")
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "post")
            .property("headers", "={testHeader: \"testHeaderValue\"}")
            .property("queryParameters", "={testQueryParam: \"testQueryParamValue\"}")
            .property("authentication.type", BasicAuthentication.TYPE)
            .property("authentication.username", "username")
            .property("authentication.password", "password")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariableWithValue("orderStatus", "processing");
  }

  @Test
  void bearerAuth() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withHeader("Authorization", matching("Bearer 123"))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "post")
            .property("authentication.type", BearerAuthentication.TYPE)
            .property("authentication.token", "123")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariableWithValue("orderStatus", "processing");
  }

  @Test
  void oAuth() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withHeader("Authorization", matching("Bearer 123"))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));
    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    wm.stubFor(
        post(urlPathMatching("/mock-oauth"))
            .withBasicAuth("test-clientId", "test-clientSecret")
            .willReturn(
                ResponseDefinitionBuilder.okForJson(Map.of(Constants.ACCESS_TOKEN, "123"))));

    var mockOauthUrl = "http://localhost:" + wm.getPort() + "/mock-oauth";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "post")
            .property("authentication.type", OAuthAuthentication.TYPE)
            .property("authentication.oauthTokenEndpoint", mockOauthUrl)
            .property("authentication.clientId", "test-clientId")
            .property("authentication.clientSecret", "test-clientSecret")
            .property("authentication.clientAuthentication", Constants.BASIC_AUTH_HEADER)
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariableWithValue("orderStatus", "processing");
  }

  @Test
  void successfulModelRun() throws Exception {
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withQueryParam("testQueryParam", matching("testQueryParamValue"))
            .withHeader("testHeader", matching("testHeaderValue"))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model = replace("rest_connector.bpmn", replace("http://localhost/test", mockUrl));

    var bpmnTest =
        ZeebeTest.with(zeebeClient).deploy(model).createInstance().waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariableWithValue("orderStatus", "processing");
  }

  @Test
  void successfulWebhookModelRun() throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/test-webhook";

    var model = replace("webhook_connector.bpmn", replace("http://webhook", mockUrl));

    // Prepare a mocked process definition backed by our test model
    when(camundaOperateClient.getProcessDefinitionModel(1L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getKey()).thenReturn(1L);
    when(processDef.getTenantId()).thenReturn(zeebeClient.getConfiguration().getDefaultTenantId());
    when(processDef.getBpmnProcessId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());

    // Deploy the webhook
    inboundManager.handleNewProcessDefinitions(Set.of(processDef));

    var bpmnTest =
        ZeebeTest.with(zeebeClient).deploy(model).createInstance().waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("webhookExecuted", true);
  }
}
