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

import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.FileInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

public class ProcessDefinitionInspectorUtilTests {

  @Test
  public void testSingleWebhookInCollaboration() throws Exception {
    var inboundConnectors = fromModel("single-webhook-collaboration.bpmn");
    assertEquals(inboundConnectors.size(), 1);
    assertEquals(inboundConnectors.get(0).getElementId(), "start_event");
  }

  @Test
  public void testMultipleWebhooksInCollaboration() throws Exception {
    var inboundConnectors = fromModel("multi-webhook-collaboration.bpmn");
    assertEquals(inboundConnectors.size(), 2);
    assertEquals(inboundConnectors.get(0).getElementId(), "start_event");
    assertEquals(inboundConnectors.get(1).getElementId(), "intermediate_event");
  }

  private List<InboundConnectorProperties> fromModel(String fileName) {
    try {
      var operateClientMock = mock(CamundaOperateClient.class);
      var inspector = new ProcessDefinitionInspector(operateClientMock);
      var modelFile = ResourceUtils.getFile("classpath:bpmn/" + fileName);
      var model = Bpmn.readModelFromStream(new FileInputStream(modelFile));
      var processDefinitionMock = mock(ProcessDefinition.class);
      when(processDefinitionMock.getKey()).thenReturn(1L);
      when(operateClientMock.getProcessDefinitionModel(1L)).thenReturn(model);
      return inspector.findInboundConnectors(processDefinitionMock);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
