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
package io.camunda.connector.runtime.inbound.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.FileInputStream;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

public class ProcessDefinitionInspectorUtilTests {

  @Test
  public void testSingleWebhookInCollaboration() throws Exception {
    var inboundConnectors = fromModel("single-webhook-collaboration.bpmn", "process");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_event", inboundConnectors.get(0).elementId());
  }

  @Test
  public void testMultipleWebhooksInCollaborationP1() throws Exception {
    var inboundConnectors = fromModel("multi-webhook-collaboration.bpmn", "process1");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_event", inboundConnectors.get(0).elementId());
  }

  @Test
  public void testMultipleWebhooksInCollaborationP2() throws Exception {
    var inboundConnectors = fromModel("multi-webhook-collaboration.bpmn", "process2");
    assertEquals(1, inboundConnectors.size());
    assertEquals("intermediate_event", inboundConnectors.get(0).elementId());
  }

  @Test
  public void testMultipleWebhookStartEventsInCollaborationP1() throws Exception {
    var inboundConnectors = fromModel("multi-webhook-start-collaboration.bpmn", "process1");
    inboundConnectors.sort(Comparator.comparing(InboundConnectorDefinition::elementId));
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_1", inboundConnectors.get(0).elementId());
  }

  @Test
  public void testMultipleWebhookStartEventsInCollaborationP2() throws Exception {
    var inboundConnectors = fromModel("multi-webhook-start-collaboration.bpmn", "process2");
    inboundConnectors.sort(Comparator.comparing(InboundConnectorDefinition::elementId));
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_2", inboundConnectors.get(0).elementId());
  }

  private List<InboundConnectorDefinitionImpl> fromModel(String fileName, String processId) {
    try {
      var operateClientMock = mock(CamundaOperateClient.class);
      var inspector = new ProcessDefinitionInspector(operateClientMock);
      var modelFile = ResourceUtils.getFile("classpath:bpmn/" + fileName);
      var model = Bpmn.readModelFromStream(new FileInputStream(modelFile));
      var processDefinitionMock = mock(ProcessDefinition.class);
      when(processDefinitionMock.getKey()).thenReturn(1L);
      when(processDefinitionMock.getBpmnProcessId()).thenReturn(processId);
      when(operateClientMock.getProcessDefinitionModel(1L)).thenReturn(model);
      return inspector.findInboundConnectors(processDefinitionMock);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
