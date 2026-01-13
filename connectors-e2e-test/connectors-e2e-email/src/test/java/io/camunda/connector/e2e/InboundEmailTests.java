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

import static io.camunda.connector.e2e.BpmnFile.replace;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionId;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class InboundEmailTests extends BaseEmailTest {

  private static final String EMAIL_INTERMEDIATE_EVENT_BPMN = "email-intermediate-event.bpmn";

  @MockitoBean SearchQueryClient searchQueryClient;
  @Autowired CamundaClient camundaClient;
  @Autowired ProcessStateManager processStateManager;
  @MockitoBean private ProcessDefinition processDef;

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @Test
  public void testEmailComplexIntermediateConnector() {

    var model =
        replace(
            EMAIL_INTERMEDIATE_EVENT_BPMN,
            BpmnFile.Replace.replace("USERNAME", "test@camunda.com"),
            BpmnFile.Replace.replace("PASSWORD", "password"),
            BpmnFile.Replace.replace("IMAP_HOST", "localhost"),
            BpmnFile.Replace.replace("IMAP_PORT", getUnsecureImapPort()),
            BpmnFile.Replace.replace("SMTP_HOST", "localhost"),
            BpmnFile.Replace.replace("SMTP_PORT", getUnsecureSmtpPort()));

    mockProcessDefinition(model);
    processStateManager.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionId(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                Set.of(1L)),
            ImportResult.ImportType.LATEST_VERSIONS));

    var bpmnTest = ZeebeTest.with(camundaClient).deploy(model).createInstance();

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> Assertions.assertEquals(2, super.getLastReceivedEmails().length));

    super.replyTo(super.getLastReceivedEmails());

    bpmnTest.waitForProcessCompletion();
  }

  private void mockProcessDefinition(BpmnModelInstance model) {
    when(searchQueryClient.getProcessModel(1)).thenReturn(model);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(processDef.getVersion()).thenReturn(1);
    lenient().when(searchQueryClient.getProcessDefinition(1L)).thenReturn(processDef);
  }
}
