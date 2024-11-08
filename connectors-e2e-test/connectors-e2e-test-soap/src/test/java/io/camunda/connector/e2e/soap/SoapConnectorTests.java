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
package io.camunda.connector.e2e.soap;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.io.File;
import java.util.Map;
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
public class SoapConnectorTests extends SoapConnectorBaseTest {

  @Test
  public void faultResponse() {
    wm.stubFor(
        post(urlPathMatching("/soapservice/service"))
            .withRequestBody(WireMock.equalToXml(NUMBER_OF_WORDS_REQUEST))
            .willReturn(
                new ResponseDefinitionBuilder()
                    .withStatus(500)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(NUMBER_OF_WORDS_ERROR_RESPONSE)));

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("serviceUrl", "http://localhost:" + wm.getPort() + "/soapservice/service")
            .property("authentication.authentication", "none")
            .property("soapVersion.version", "1.1")
            .property("header.type", "none")
            .property("body.type", "json")
            .property("body.json", "={\"ns:NumberToWords\":{\"ns:ubiNum\": 500}}")
            .property("namespaces", "={\"ns\":\"http://www.dataaccess.com/webservicesserver/\"}")
            .property(
                "errorExpression",
                "if error.code=\"SOAP_FAULT_RECEIVED\" then bpmnError(\"Code\", \"The code\", error.variables) else null")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("soapSimpleRequest", elementTemplate);

    Map<String, Object> expectedResult =
        Map.of(
            "Envelope",
            Map.of(
                "Body",
                Map.of(
                    "Fault",
                    Map.of(
                        "faultcode",
                        "soap:Server",
                        "faultstring",
                        "Server was unable to process request. Object reference not set to an instance of an object.",
                        "detail",
                        Map.of(
                            "error", "Object reference not set to an instance of an object.")))));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("response", expectedResult);
  }

  @Test
  void noAuthSimpleRequest() {
    wm.stubFor(
        post(urlPathMatching("/soapservice/service"))
            .withRequestBody(WireMock.equalToXml(NUMBER_OF_WORDS_REQUEST))
            .willReturn(new ResponseDefinitionBuilder().withBody(NUMBER_OF_WORDS_RESPONSE)));

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("serviceUrl", "http://localhost:" + wm.getPort() + "/soapservice/service")
            .property("authentication.authentication", "none")
            .property("soapVersion.version", "1.1")
            .property("header.type", "none")
            .property("body.type", "json")
            .property("body.json", "={\"ns:NumberToWords\":{\"ns:ubiNum\": 500}}")
            .property("namespaces", "={\"ns\":\"http://www.dataaccess.com/webservicesserver/\"}")
            .property("resultVariable", "res")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("soapSimpleRequest", elementTemplate);

    Map<String, Object> expectedResult =
        Map.of(
            "Envelope",
            Map.of(
                "Body",
                Map.of("NumberToWordsResponse", Map.of("NumberToWordsResult", "five hundred "))));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("res", expectedResult);
  }

  @Test
  void noAuthTemplatedRequest() {
    wm.stubFor(
        post(urlPathMatching("/soapservice/service"))
            .withRequestBody(WireMock.equalToXml(NUMBER_OF_WORDS_REQUEST))
            .willReturn(new ResponseDefinitionBuilder().withBody(NUMBER_OF_WORDS_RESPONSE)));

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("serviceUrl", "http://localhost:" + wm.getPort() + "/soapservice/service")
            .property("authentication.authentication", "none")
            .property("soapVersion.version", "1.1")
            .property("header.type", "none")
            .property("body.type", "template")
            .property(
                "body.template",
                "<ns:NumberToWords><ns:ubiNum>{{number}}</ns:ubiNum></ns:NumberToWords>")
            .property("body.context", "={number: 500}")
            .property("namespaces", "={\"ns\":\"http://www.dataaccess.com/webservicesserver/\"}")
            .property("resultVariable", "res")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("soapSimpleRequest", elementTemplate);

    Map<String, Object> expectedResult =
        Map.of(
            "Envelope",
            Map.of(
                "Body",
                Map.of("NumberToWordsResponse", Map.of("NumberToWordsResult", "five hundred "))));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("res", expectedResult);
  }
}
