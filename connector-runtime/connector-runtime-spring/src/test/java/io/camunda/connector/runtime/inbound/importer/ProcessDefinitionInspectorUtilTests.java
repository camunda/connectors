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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.operate.OperateClient;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.FileInputStream;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

public class ProcessDefinitionInspectorUtilTests {

  @Test
  public void testSingleWebhookInCollaboration() {
    var inboundConnectors = fromModel("single-webhook-collaboration.bpmn", "process");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_event", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testMultipleWebhooksInCollaborationP1() {
    var inboundConnectors = fromModel("multi-webhook-collaboration.bpmn", "process1");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_event", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testMultipleWebhooksInCollaborationP2() {
    var inboundConnectors = fromModel("multi-webhook-collaboration.bpmn", "process2");
    assertEquals(1, inboundConnectors.size());
    assertEquals("intermediate_event", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testMultipleWebhookStartEventsInCollaborationP1() {
    var inboundConnectors = fromModel("multi-webhook-start-collaboration.bpmn", "process1");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_1", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testMultipleWebhookStartEventsInCollaborationP2() {
    var inboundConnectors = fromModel("multi-webhook-start-collaboration.bpmn", "process2");
    assertEquals(1, inboundConnectors.size());
    assertEquals("start_2", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testSingleWebhookBoundaryEvent() {
    var inboundConnectors = fromModel("single-webhook-boundary.bpmn", "BoundaryEventTest");
    assertEquals(1, inboundConnectors.size());
    assertEquals("boundary_event", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testSingleWebhookSubprocess() {
    var inboundConnectors = fromModel("single-webhook-subprocess.bpmn", "subprocess_webhook");
    assertEquals(1, inboundConnectors.size());
    assertEquals("webhook_in_subprocess", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testSingleKafkaSubprocess() {
    var inboundConnectors =
        fromModel("single-kafka-consumer-subprocess.bpmn", "kafka-consumer-subprocess");
    assertEquals(1, inboundConnectors.size());
    assertEquals("kafka_in_subprocess", inboundConnectors.getFirst().element().elementId());
  }

  @Test
  public void testMultiWebhookStartMessage() {
    var inboundConnectors =
        fromModel("multi-webhook-start-message.bpmn", "multi-webhook-start-message");
    assertEquals(2, inboundConnectors.size());
    assertNotNull(
        inboundConnectors.stream()
            .filter(ic -> ic.element().elementId().equals("wh-start-msg-1"))
            .findFirst()
            .get());
    assertNotNull(
        inboundConnectors.stream()
            .filter(ic -> ic.element().elementId().equals("wh-start-msg-2"))
            .findFirst()
            .get());
  }

  @Test
  public void testDuplicatePropertiesAreRemoved() {
    var inboundConnectors =
        fromModel(
            "multi-webhook-start-message-duplicate-property.bpmn", "multi-webhook-start-message");
    Assertions.assertEquals("firstRes", inboundConnectors.getFirst().resultVariable());
    System.out.println(inboundConnectors);
  }

  private List<InboundConnectorElement> fromModel(String fileName, String processId) {
    try {
      var operateClientMock = mock(OperateClient.class);
      var inspector = new ProcessDefinitionInspector(operateClientMock);
      var modelFile = ResourceUtils.getFile("classpath:bpmn/" + fileName);
      var model = Bpmn.readModelFromStream(new FileInputStream(modelFile));
      var processDefinitionID = new ProcessDefinitionIdentifier(processId, "tenant1");
      var processDefinitionVersion = new ProcessImportResult.ProcessDefinitionVersion(1, 1);
      when(operateClientMock.getProcessModel(1)).thenReturn(model);
      return inspector.findInboundConnectors(processDefinitionID, processDefinitionVersion);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
