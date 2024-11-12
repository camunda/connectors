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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

public abstract class SoapConnectorBaseTest {

  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/soap/element-templates/soap-outbound-connector.json";

  protected static final String NUMBER_OF_WORDS_REQUEST =
      """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
              xmlns:ns="http://www.dataaccess.com/webservicesserver/"
              xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
              xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
              <SOAP-ENV:Header/>
              <SOAP-ENV:Body>
                <ns:NumberToWords>
                  <ns:ubiNum>500</ns:ubiNum>
                </ns:NumberToWords>
              </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
      """;

  protected static final String NUMBER_OF_WORDS_RESPONSE =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <m:NumberToWordsResponse xmlns:m="http://www.dataaccess.com/webservicesserver/">
            <m:NumberToWordsResult>five hundred </m:NumberToWordsResult>
          </m:NumberToWordsResponse>
        </soap:Body>
      </soap:Envelope>
      """;

  protected static final String NUMBER_OF_WORDS_ERROR_RESPONSE =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <soap:Fault>
            <faultcode>soap:Server</faultcode>
            <faultstring>Server was unable to process request. Object reference not set to an instance of an object.</faultstring>
            <detail>
              <error>Object reference not set to an instance of an object.</error>
            </detail>
          </soap:Fault>
        </soap:Body>
      </soap:Envelope>
      """;

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(true)))
          .build();

  @TempDir File tempDir;
  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @LocalServerPort int serverPort;

  protected BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .boundaryEvent()
        .error()
        .zeebeOutput("=response", "response")
        .endEvent()
        .done();
  }

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  protected ZeebeTest setupTestWithBpmnModel(String taskName, File elementTemplate) {
    BpmnModelInstance model = getBpmnModelInstance(taskName);
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, taskName);
    return getZeebeTest(updatedModel);
  }

  protected ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }

  protected BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }
}
