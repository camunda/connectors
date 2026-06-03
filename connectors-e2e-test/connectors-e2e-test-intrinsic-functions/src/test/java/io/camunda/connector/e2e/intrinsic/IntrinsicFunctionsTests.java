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
package io.camunda.connector.e2e.intrinsic;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
public class IntrinsicFunctionsTests {

  static final String TEST_LINK_PREFIX = "https://test.example.com/documents/";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir File tempDir;
  @Autowired CamundaClient camundaClient;
  @Autowired DocumentFactory documentFactory;

  // Spy on the runtime's document store so that `generateLink` can return a deterministic value —
  // the test cluster's fallback in-memory document store rejects link generation with HTTP 403,
  // which would otherwise make the `createLink` intrinsic untestable end-to-end.
  @MockitoSpyBean CamundaDocumentStore documentStore;

  @BeforeEach
  void stubGenerateLink() {
    doAnswer(
            inv ->
                TEST_LINK_PREFIX + ((CamundaDocumentReference) inv.getArgument(0)).getDocumentId())
        .when(documentStore)
        .generateLink(any(), any());
  }

  @Test
  void base64Intrinsic_withStringInput() {
    String originalText = "Hello World";
    String expectedBase64 =
        Base64.getEncoder().encodeToString(originalText.getBytes(StandardCharsets.UTF_8));

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo(expectedBase64)))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of(),
        "={result: {\"camunda.function.type\": \"base64\", \"params\": [\""
            + originalText
            + "\"]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void base64Intrinsic_withDocumentInput() {
    byte[] content = "Hello Document".getBytes(StandardCharsets.UTF_8);
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from(content).contentType("text/plain").build());
    String expectedBase64 = Base64.getEncoder().encodeToString(content);

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo(expectedBase64)))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"base64\", \"params\": [inputDoc]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void getTextIntrinsic_defaultCharset() {
    String text = "Some text content for getText";
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from(text.getBytes(java.nio.charset.Charset.defaultCharset()))
                .contentType("text/plain")
                .build());

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo(text)))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"getText\", \"params\": [inputDoc]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void getTextIntrinsic_withExplicitCharset() {
    String text = "Charset test";
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from(text.getBytes(StandardCharsets.UTF_16))
                .contentType("text/plain; charset=UTF-16")
                .build());

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo(text)))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"getText\", \"params\": [inputDoc, \"UTF-16\"]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void getJsonIntrinsic_returnsFullObject() {
    String json = "{\"name\":\"Alice\",\"age\":30}";
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from(json.getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .build());

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(
                equalToJson("{\"result\":{\"name\":\"Alice\",\"age\":30}}", true, true))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"getJson\", \"params\": [inputDoc]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void getJsonIntrinsic_withFeelFilter() {
    String json = "{\"name\":\"Alice\",\"age\":30}";
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from(json.getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .build());

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo("Alice")))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"getJson\", \"params\": [inputDoc, \"name\"]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  @Test
  void createLinkIntrinsic_returnsLink() {
    Document doc =
        documentFactory.create(
            DocumentCreationRequest.from("link me".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .build());
    String expectedLink =
        TEST_LINK_PREFIX + ((CamundaDocumentReference) doc.reference()).getDocumentId();

    wm.stubFor(
        post(urlPathMatching("/mock"))
            .withRequestBody(matchingJsonPath("$.result", equalTo(expectedLink)))
            .willReturn(ResponseDefinitionBuilder.okForJson(Map.of("status", "ok"))));

    runProcess(
        Map.of("inputDoc", referenceAsMap(doc)),
        "={result: {\"camunda.function.type\": \"createLink\", \"params\": [inputDoc]}}");

    wm.verify(postRequestedFor(urlPathMatching("/mock")));
  }

  /** Runs an HTTP POST connector against the WireMock mock URL with the given body FEEL. */
  private void runProcess(Map<String, Object> variables, String bodyFeelExpression) {
    var mockUrl = "http://localhost:" + wm.getPort() + "/mock";
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "POST")
            .property("body", bodyFeelExpression)
            .property("resultExpression", "={httpStatus: response.body.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance(variables)
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("httpStatus", "ok");
  }

  private static Map<String, Object> referenceAsMap(Document document) {
    var ref = (CamundaDocumentReference) document.reference();
    Map<String, Object> map = new HashMap<>();
    map.put("camunda.document.type", "camunda");
    map.put("storeId", ref.getStoreId());
    map.put("documentId", ref.getDocumentId());
    map.put("contentHash", ref.getContentHash());
    map.put("metadata", Map.of());
    return map;
  }
}
