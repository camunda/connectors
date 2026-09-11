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

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.connector.e2e.BpmnFile.Replace.replace;
import static io.camunda.connector.e2e.BpmnFile.replace;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.util.StreamUtils.copyToByteArray;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.http.base.model.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.model.auth.BasicAuthentication;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class, HttpTests.SpyTestConfig.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "spring.http.converters.preferred-json-mapper=jackson2"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@Import(HttpTests.SpyTestConfig.class)
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
public class HttpTests {

  @TestConfiguration
  static class SpyTestConfig {

    @Bean
    @Primary
    public DocumentFactory documentFactorySpied() {
      return spy(new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
    }
  }

  private static final String TEXT_FILE = "text.txt";
  private static final String PNG_FILE = "camunda1.png";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir File tempDir;
  @Autowired CamundaClient camundaClient;

  @Autowired ProcessStateManager stateStore;

  @Autowired WebhookConnectorRegistry webhookConnectorRegistry;

  @MockitoBean SearchQueryClient searchQueryClient;

  @Autowired MockMvc mockMvc;

  @Autowired DocumentFactory documentFactory;

  @LocalServerPort int serverPort;

  private final ObjectMapper mapper = new ObjectMapper();

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
            .property("body", "={\"order\": {\"status\": \"processing\", \"id\": string(42+3)}}")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
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
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void ApiKeyAuthenticationWithApiKeyInHeaders() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withHeader("api-auth", matching("apiKey"))
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
            .property("authentication.type", ApiKeyAuthentication.TYPE)
            .property("authentication.apiKeyLocation", "headers")
            .property("authentication.name", "api-auth")
            .property("authentication.value", "apiKey")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void ApiKeyAuthenticationWithApiKeyInQueryParameters() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withQueryParam("api-auth", matching("apiKey"))
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
            .property("authentication.type", ApiKeyAuthentication.TYPE)
            .property("authentication.apiKeyLocation", "query")
            .property("authentication.name", "api-auth")
            .property("authentication.value", "apiKey")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
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
                ResponseDefinitionBuilder.okForJson(Map.of(OAuthConstants.ACCESS_TOKEN, "123"))));

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
            .property("authentication.clientAuthentication", OAuthConstants.BASIC_AUTH_HEADER)
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void successfulModelRun() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withQueryParam("testQueryParam", matching("testQueryParamValue"))
            .withHeader("testHeader", matching("testHeaderValue"))
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
            .property("connectionTimeoutInSeconds", "20")
            .property("body", "={order: {id: 1, items:[{id:1}, {id:2}]}}")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void successfulWebhookModelRun() throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/test-webhook";

    var model = replace("webhook_connector.bpmn", replace("http://webhook", mockUrl));

    // Prepare a mocked process connectorData backed by our test model
    when(searchQueryClient.getProcessModel(1L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(searchQueryClient.getProcessDefinition(1L)).thenReturn(processDef);

    // Deploy the webhook
    stateStore.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionRef(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                Set.of(processDef.getProcessDefinitionKey())),
            ImportType.LATEST_VERSIONS));

    // Wait for webhook to be activated before starting the process
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> webhookConnectorRegistry.getActiveWebhook("test-webhook").isPresent());

    var bpmnTest =
        ZeebeTest.with(camundaClient).deploy(model).createInstance().waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("webhookExecuted", true);
  }

  @Test
  void successfulWebhookModelWithQueryParamsRun() throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/test-webhook?value=test";

    var model = replace("webhook_connector.bpmn", replace("http://webhook", mockUrl));

    // Prepare a mocked process connectorData backed by our test model
    when(searchQueryClient.getProcessModel(1L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(processDef.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(1L)).thenReturn(processDef);

    // Deploy the webhook
    stateStore.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionRef(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                Set.of(processDef.getProcessDefinitionKey())),
            ImportType.LATEST_VERSIONS));

    // Wait for webhook to be activated before starting the process
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> webhookConnectorRegistry.getActiveWebhook("test-webhook").isPresent());

    var bpmnTest =
        ZeebeTest.with(camundaClient).deploy(model).createInstance().waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("webhookExecuted", true);
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("queryParam", "test");
  }

  @Test
  void shouldCreateDocumentsAndReturnResponse_whenMultipartRequest() throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/testId";
    var model =
        replace(
            "webhook_document.bpmn",
            BpmnFile.Replace.replace(
                "<ACTIVATION_CONDITION>", "=request.headers.theheader = &#34;THEVALUE&#34;"));

    when(searchQueryClient.getProcessModel(2L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(2L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(processDef.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(2L)).thenReturn(processDef);

    stateStore.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionRef(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                Collections.singleton(processDef.getProcessDefinitionKey())),
            ImportType.LATEST_VERSIONS));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(100))
        .until(() -> webhookConnectorRegistry.getActiveWebhook("testId").isPresent());

    var bpmnTest = ZeebeTest.with(camundaClient).deploy(model).createInstance();
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ZERO)
        .untilAsserted(
            () ->
                assertThat(bpmnTest.getProcessInstanceEvent()).hasActiveElements("Event_13sti90"));

    ClassPathResource textFile = new ClassPathResource("files/text.txt");
    ClassPathResource imageFile = new ClassPathResource("files/camunda1.png");
    byte[] textFileContent = copyToByteArray(textFile.getInputStream());
    byte[] imageFileContent = copyToByteArray(imageFile.getInputStream());
    var response =
        mockMvc
            .perform(
                multipart(mockUrl)
                    .part(new MockPart("param1", PNG_FILE, imageFileContent, MediaType.IMAGE_PNG))
                    .part(new MockPart("param2", TEXT_FILE, textFileContent, MediaType.TEXT_PLAIN))
                    .header("THEHEADER", "THEVALUE"))
            .andExpect(status().isOk())
            .andReturn();

    bpmnTest.waitForProcessCompletion();
    String jsonResponse = response.getResponse().getContentAsString();
    Map<String, Object> actualResponse = mapper.readValue(jsonResponse, Map.class);
    List<Map> documents = (List<Map>) actualResponse.get("documents");

    assertThat(bpmnTest.getProcessInstanceEvent())
        .isCompleted()
        .hasVariable("body", Map.of())
        .hasVariable("documents", documents);
    verify(documentFactory, times(2)).create(any());
    Assertions.assertThat(documents).hasSize(2);

    Map<String, Object> pngDocument = documents.get(0);
    Assertions.assertThat(pngDocument).containsKeys("storeId", "documentId");
    Map<String, Object> pngMetadata = (Map<String, Object>) pngDocument.get("metadata");
    Assertions.assertThat(pngMetadata.get("fileName")).isEqualTo(PNG_FILE);
    Assertions.assertThat(pngMetadata.get("contentType")).isEqualTo(MediaType.IMAGE_PNG_VALUE);

    Map<String, Object> textDocument = documents.get(1);
    Assertions.assertThat(textDocument).containsKeys("storeId", "documentId");
    Map<String, Object> textMetadata = (Map<String, Object>) textDocument.get("metadata");
    Assertions.assertThat(textMetadata.get("fileName")).isEqualTo(TEXT_FILE);
    Assertions.assertThat(textMetadata.get("contentType")).isEqualTo(MediaType.TEXT_PLAIN_VALUE);

    var pngStoredDocument =
        documentFactory.resolve(
            new CamundaDocumentReferenceImpl(
                pngDocument.get("storeId").toString(),
                pngDocument.get("documentId").toString(),
                pngDocument.get("contentHash").toString(),
                null));
    Assertions.assertThat(pngStoredDocument.asByteArray()).isEqualTo(imageFileContent);

    var textStoredDocument =
        documentFactory.resolve(
            new CamundaDocumentReferenceImpl(
                textDocument.get("storeId").toString(),
                textDocument.get("documentId").toString(),
                textDocument.get("contentHash").toString(),
                null));
    Assertions.assertThat(new String(textStoredDocument.asByteArray(), StandardCharsets.UTF_8))
        .isEqualTo("Hello from\n" + "the Camunda Connectors!");
  }

  @Test
  void graphQL() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withHeader("testHeader", matching("testHeaderValue"))
            .withBasicAuth("username", "password")
            .withRequestBody(matchingJsonPath("$..query", equalTo("{hero { name } }")))
            .withRequestBody(matchingJsonPath("$..variables.hello", equalTo("world")))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("graphqlTask").endEvent().done();

    var elementTemplatePath =
        "../../connectors/http/graphql/element-templates/graphql-outbound-connector.json";
    var elementTemplate =
        ElementTemplate.from(elementTemplatePath)
            .property("graphql.url", mockUrl)
            .property("graphql.method", "post")
            .property("graphql.headers", "={testHeader: \"testHeaderValue\"}")
            .property("graphql.query", "{hero { name } }")
            .property("graphql.variables", "={hello:\"world\"}")
            .property("authentication.type", BasicAuthentication.TYPE)
            .property("authentication.username", "username")
            .property("authentication.password", "password")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedElementTemplateFile = new File(tempDir, "result.bpmn");
    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "graphqlTask", updatedElementTemplateFile);

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void graphQLViaGet() {
    // Prepare an HTTP mock server
    wm.stubFor(
        get(urlPathMatching("/mock"))
            .withHeader("testHeader", matching("testHeaderValue"))
            .withBasicAuth("username", "password")
            .withQueryParams(
                Map.of(
                    "query",
                    equalTo("{hero { name } }"),
                    "variables",
                    equalTo("{\"hello\":\"world\"}")))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("graphqlTask").endEvent().done();

    var elementTemplatePath =
        "../../connectors/http/graphql/element-templates/graphql-outbound-connector.json";
    var elementTemplate =
        ElementTemplate.from(elementTemplatePath)
            .property("graphql.url", mockUrl)
            .property("graphql.method", "get")
            .property("graphql.headers", "={testHeader: \"testHeaderValue\"}")
            .property("graphql.query", "{hero { name } }")
            .property("graphql.variables", "={hello:\"world\"}")
            .property("authentication.type", BasicAuthentication.TYPE)
            .property("authentication.username", "username")
            .property("authentication.password", "password")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedElementTemplateFile = new File(tempDir, "result.bpmn");
    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "graphqlTask", updatedElementTemplateFile);

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("orderStatus", "processing");
  }

  @Test
  void useErrorResponse() {
    // Prepare an HTTP mock server
    wm.stubFor(
        post(urlPathMatching("/mock"))
            .willReturn(
                badRequest()
                    .withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("message", "custom message")
                            .put("booleanField", true)
                            .put("temp", 36))));

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";

    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("restTask")
            .boundaryEvent("errorId")
            .error()
            .zeebeOutput("=temp", "temp")
            .zeebeOutput("=message", "message")
            .zeebeOutput("=booleanField", "booleanField")
            .endEvent()
            .done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "POST")
            .property(
                "errorExpression",
                "=if matches(error.code, \"400\") and error.variables.response.body.temp = 36 then bpmnError(\"Too hot\", error.variables.response.body.message, error.variables.response.body) else bpmnError(\"Not too hot\", \"The message default\",{fake: \"fakeValue\"})")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("temp", 36)
        .hasVariable("booleanField", true)
        .hasVariable("message", "custom message");
  }

  @Test
  void intrinsicFunctionBase64InBearerToken() {
    // Test that intrinsic functions (like base64) work correctly in @FEEL annotated fields
    // Using if-else format as done in element templates, with a hardcoded string as param:
    // "= if condition then {\"camunda.function.type\":\"base64\",\"params\":[\"Hello World\"]} else
    // fallback"

    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";
    String originalText = "Hello World";
    String expectedBase64 =
        Base64.getEncoder().encodeToString(originalText.getBytes(StandardCharsets.UTF_8));

    // Prepare an HTTP mock server that expects the base64 encoded string as bearer token
    wm.stubFor(
        get(urlPathMatching("/mock"))
            .withHeader("Authorization", equalTo("Bearer " + expectedBase64))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("result", "success", "received", expectedBase64))));

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    // Use the if-else FEEL expression format with intrinsic function for bearer token
    // The param is a hardcoded string "Hello World"
    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "GET")
            .property("authentication.type", "bearer")
            .property(
                "authentication.token",
                "=if true then {\"camunda.function.type\":\"base64\",\"params\":[\"Hello World\"]} else \"fallback\"")
            .property("resultExpression", "={result: response.body.result}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("result", "success");
  }
}
