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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookControllerPlainJavaTests {

  private static final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Test
  public void multipleWebhooksOnSameContextPathAreNotSupported() {
    WebhookConnectorRegistry webhookConnectorRegistry = new WebhookConnectorRegistry();
    var connectorA = buildConnector(webhookDefinition("processA", 1, "myPath"));
    webhookConnectorRegistry.register(connectorA);

    var connectorB = buildConnector(webhookDefinition("processA", 1, "myPath"));
    assertThrowsExactly(
        RuntimeException.class, () -> webhookConnectorRegistry.register(connectorB));
    assertFalse(webhookConnectorRegistry.isRegistered(connectorB));
  }

  @Test
  public void webhookMultipleVersionsDisableWebhook() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    var processA1 = buildConnector(webhookDefinition("processA", 1, "myPath"));
    var processA2 = buildConnector(webhookDefinition("processA", 2, "myPath"));
    var processB1 = buildConnector(webhookDefinition("processB", 1, "myPath2"));

    webhook.register(processA1);

    // create a new object to ensure correct instance comparison
    var processA1Copy = buildConnector(webhookDefinition("processA", 1, "myPath"));
    webhook.deregister(processA1Copy);

    webhook.register(processA2);

    webhook.register(processB1);
    var processB1Copy = buildConnector(webhookDefinition("processB", 1, "myPath2"));
    webhook.deregister(processB1Copy);

    var connectorForPath1 = webhook.getWebhookConnectorByContextPath("myPath");

    assertTrue(connectorForPath1.isPresent(), "A2 context is present");
    assertTrue(webhook.isRegistered(processA2), "A2 is registered");
    assertFalse(webhook.isRegistered(processA1), "A1 is not registered");
    assertFalse(webhook.isRegistered(processB1), "B1 is not registered");
    assertEquals(
        2,
        connectorForPath1.get().context().getDefinition().elements().getFirst().version(),
        "The newest one");

    var connectorForPath2 = webhook.getWebhookConnectorByContextPath("myPath2");
    assertTrue(connectorForPath2.isEmpty(), "No one - as it was deleted.");
  }

  @Test
  public void webhookDeactivation_shouldReturnNotFound() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, "myPath"));

    // when
    webhook.register(processA1);
    webhook.deregister(processA1);

    // then
    assertFalse(webhook.getWebhookConnectorByContextPath("myPath").isPresent());
    assertFalse(webhook.isRegistered(processA1));
  }

  @Test
  public void webhookDeactivation_samePathButDifferentConnector_shouldFail() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, "myPath"));
    var processA2 = buildConnector(webhookDefinition("processA", 2, "myPath"));

    // when
    webhook.register(processA1);

    // then
    assertThrowsExactly(RuntimeException.class, () -> webhook.deregister(processA2));
    assertFalse(webhook.isRegistered(processA2));
    assertTrue(webhook.isRegistered(processA1));
  }

  private static long nextProcessDefinitionKey = 0L;

  public static RegisteredExecutable.Activated buildConnector(
      InboundConnectorDetails connectorData) {
    WebhookConnectorExecutable executable = mock(WebhookConnectorExecutable.class);
    try {
      Mockito.when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
          .thenReturn(mock(WebhookResult.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new RegisteredExecutable.Activated(executable, buildContext(connectorData));
  }

  public static InboundConnectorContextImpl buildContext(InboundConnectorDetails def) {
    var context =
        new InboundConnectorContextImpl(
            name -> null,
            new DefaultValidationProvider(),
            def,
            mock(InboundCorrelationHandler.class),
            e -> {},
            mapper,
            EvictingQueue.create(10));

    return spy(context);
  }

  public static InboundConnectorDetails webhookDefinition(
      String bpmnProcessId, int version, String path) {
    return new InboundConnectorDetails(
        bpmnProcessId + version + path,
        List.of(webhookElement(++nextProcessDefinitionKey, bpmnProcessId, version, path)));
  }

  public static InboundConnectorElement webhookElement(
      long processDefinitionKey, String bpmnProcessId, int version, String path) {

    return new InboundConnectorElement(
        Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
        new StartEventCorrelationPoint(bpmnProcessId, version, processDefinitionKey),
        new ProcessElement(
            bpmnProcessId, version, processDefinitionKey, "testElement", "testTenantId"));
  }
}
