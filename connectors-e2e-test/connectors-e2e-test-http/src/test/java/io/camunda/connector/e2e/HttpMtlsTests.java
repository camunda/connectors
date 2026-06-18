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

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end test for the mutual TLS feature of the HTTP REST connector. Runs the connector through
 * the full runtime against an HTTPS WireMock server that requires a client certificate, with the
 * client identity and server trust material supplied as PEM via the {@code clientTls.*}
 * element-template properties.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.validation.hosts.enabled=true",
      "camunda.connector.validation.hosts.unsafe-allow-loopback=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
public class HttpMtlsTests {

  // HTTPS server that requires a client certificate, to exercise the mTLS feature end-to-end.
  @RegisterExtension
  static WireMockExtension wmMtls =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .httpDisabled(true)
                  .dynamicHttpsPort()
                  .keystorePath(mtlsResourcePath("server-keystore.p12"))
                  .keystorePassword("password")
                  .keyManagerPassword("password")
                  .keystoreType("PKCS12")
                  .needClientAuth(true)
                  .trustStorePath(mtlsResourcePath("server-truststore.p12"))
                  .trustStorePassword("password")
                  .trustStoreType("PKCS12"))
          .build();

  @TempDir File tempDir;
  @Autowired CamundaClient camundaClient;

  @Test
  void mtlsClientCertificate() throws Exception {
    // The server requires a client certificate; the connector must present one and trust the
    // server's self-signed certificate.
    wmMtls.stubFor(
        post(urlPathMatching("/mock"))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "https://localhost:" + wmMtls.getHttpsPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "post")
            .property("clientTls.clientCertificate", mtlsResource("client.crt"))
            .property("clientTls.clientPrivateKey", mtlsResource("client.key"))
            .property("clientTls.trustedCertificate", mtlsResource("server.crt"))
            .property("body", "={\"order\": {\"status\": \"processing\"}}")
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
  void mtlsWithoutClientCertificateRaisesIncident() throws Exception {
    wmMtls.stubFor(
        post(urlPathMatching("/mock"))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("order", Map.of("status", "processing")))));

    var mockUrl = "https://localhost:" + wmMtls.getHttpsPort() + "/mock";

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask("restTask").endEvent().done();

    // Trust the server but present no client identity: the server requires a client certificate,
    // so the TLS handshake is rejected and the connector fails with an incident.
    var elementTemplate =
        ElementTemplate.from(
                "../../connectors/http/rest/element-templates/http-json-connector.json")
            .property("url", mockUrl)
            .property("method", "post")
            .property("clientTls.trustedCertificate", mtlsResource("server.crt"))
            .property("body", "={\"order\": {\"status\": \"processing\"}}")
            .property("resultExpression", "={orderStatus: response.body.order.status}")
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "restTask", new File(tempDir, "result.bpmn"));

    ZeebeTest.with(camundaClient).deploy(updatedModel).createInstance().waitForActiveIncidents();
  }

  private static String mtlsResource(String name) throws Exception {
    return Files.readString(Path.of(mtlsResourcePath(name)), StandardCharsets.UTF_8);
  }

  private static String mtlsResourcePath(String name) {
    try {
      return Path.of(HttpMtlsTests.class.getResource("/mtls/" + name).toURI()).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Missing test resource /mtls/" + name, e);
    }
  }
}
