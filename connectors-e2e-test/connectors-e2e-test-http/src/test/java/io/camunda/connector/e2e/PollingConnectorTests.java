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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.connector.e2e.BpmnFile.Replace.replace;
import static io.camunda.connector.e2e.BpmnFile.replace;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@ExtendWith(MockitoExtension.class)
public class PollingConnectorTests {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired CamundaClient camundaClient;
  @MockitoBean private ProcessDefinition processDef;

  @BeforeEach
  public void setup() {
    wm.resetAll();
  }

  @Test
  void shouldResolveFeelExpressionInUrlAndTerminate() {
    wm.stubFor(
        get(urlEqualTo("/mock"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"orderId\": \"1234\", \"successfulOrder\": \"true\"}")
                    .withStatus(200)));

    var mockUrl = "=&#34;http://localhost:" + wm.getPort() + "/&#34; + myVar";

    var model =
        replace(
            "polling_connector.bpmn",
            replace("URL_SET", mockUrl),
            replace("CORRELATION_KEY_EXPRESSION", "=body.orderId"),
            replace("CORRELATION_KEY_PROCESS", "=orderId"),
            replace(
                "RESULT_EXPRESSION", "={ &#34;isOrderSuccessful&#34; : body.successfulOrder  }"));

    when(processDef.getProcessDefinitionKey()).thenReturn(2L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(model)
            .createInstance(Map.of("myVar", "mock", "orderId", "1234"));

    bpmnTest.waitForProcessCompletion();
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("isOrderSuccessful", "true");
  }
}
