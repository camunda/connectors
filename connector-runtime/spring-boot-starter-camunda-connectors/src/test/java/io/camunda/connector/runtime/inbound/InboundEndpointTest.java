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
package io.camunda.connector.runtime.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.controller.InboundConnectorRestController;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class InboundEndpointTest {

  static class AnotherExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    @Override
    public void activate(InboundConnectorContext context) throws Exception {}

    @Override
    public void deactivate() throws Exception {}
  }

  static class TestWebhookExecutable implements WebhookConnectorExecutable {

    @Override
    public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {
      return null;
    }
  }

  @Test
  public void testDataReturnedForWebhookConnectorExecutableSubclass() {
    var executableRegistry = mock(InboundExecutableRegistry.class);

    when(executableRegistry.query(any()))
        .thenReturn(
            List.of(
                new ActiveExecutableResponse(
                    UUID.randomUUID(),
                    TestWebhookExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", "webhook"),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElement("", 1, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry);

    var response = statusController.getActiveInboundConnectors(null, null, null);
    assertEquals(1, response.size());
    assertEquals("myPath", response.getFirst().data().get("path"));
  }

  @Test
  public void executableClassNullHandledCorrectly() {
    var executableRegistry = mock(InboundExecutableRegistry.class);
    when(executableRegistry.query(any()))
        .thenReturn(
            List.of(
                new ActiveExecutableResponse(
                    UUID.randomUUID(),
                    null, // executable class is null
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", "webhook"),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElement("", 1, 1, "", ""))),
                    Health.down(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry);

    var response = statusController.getActiveInboundConnectors(null, null, null);
    assertEquals(1, response.size());
    assertEquals(Health.down(), (response.getFirst()).health());
  }

  @Test
  public void shouldReturnConnectorsGroupedByConnectorId() {
    var executableRegistry = mock(InboundExecutableRegistry.class);
    var type1 = "webhook";
    var uuid1 = UUID.randomUUID();
    var type2 = "anotherType";
    var uuid2 = UUID.randomUUID();
    var uuid3 = UUID.randomUUID();

    when(executableRegistry.getConnectorName(type1)).thenReturn("Webhook");
    when(executableRegistry.getConnectorName(type2)).thenReturn("AnotherType");
    when(executableRegistry.query(any()))
        .thenReturn(
            List.of(
                new ActiveExecutableResponse(
                    uuid1,
                    TestWebhookExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", type1),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElement("ProcessA", 1, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis()),
                new ActiveExecutableResponse(
                    uuid2,
                    AnotherExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.other.prop", "myOtherValue", "inbound.type", type2),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElement("ProcessB", 2, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis()),
                new ActiveExecutableResponse(
                    uuid3,
                    AnotherExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.other.prop", "myOtherValue2", "inbound.type", type2),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElement("ProcessC", 2, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry);

    var response = statusController.getConnectorInstances(null);
    assertEquals(2, response.size());
    ConnectorInstances first = response.get(0);
    ConnectorInstances second = response.get(1);

    assertEquals(type1, first.connectorId());
    assertEquals("Webhook", first.connectorName());
    assertEquals(1, first.instances().size());
    assertEquals(uuid1, first.instances().get(0).executableId());

    assertEquals(type2, second.connectorId());
    assertEquals("AnotherType", second.connectorName());
    assertEquals(2, second.instances().size());
    var firstInstance = second.instances().get(0);
    var firstInstanceData = firstInstance.data();
    var secondInstance = second.instances().get(1);
    var secondInstanceData = secondInstance.data();

    assertEquals(uuid2, firstInstance.executableId());
    assertEquals("ProcessB", firstInstance.elements().getFirst().bpmnProcessId());
    assertEquals("myOtherValue", firstInstanceData.get("inbound.other.prop"));
    assertEquals(uuid3, secondInstance.executableId());
    assertEquals("ProcessC", secondInstance.elements().getFirst().bpmnProcessId());
    assertEquals("myOtherValue2", secondInstanceData.get("inbound.other.prop"));
  }
}
