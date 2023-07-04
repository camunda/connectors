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

import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.zeebe.spring.client.metrics.SimpleMetricsRecorder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookControllerPlainJavaTests {

  private static final String CONNECTOR_SECRET_NAME = "DUMMY_SECRET";
  private static final String CONNECTOR_SECRET_VALUE = "s3cr3T";

  private SimpleMetricsRecorder metrics;

  @BeforeEach
  public void setupMetrics() {
    metrics = new SimpleMetricsRecorder();
  }

  @Test
  public void multipleWebhooksOnSameContextPathAreNotSupported() throws Exception {
    WebhookConnectorRegistry webhookConnectorRegistry = new WebhookConnectorRegistry();
    var connectorA = buildConnector(webhookProperties("processA", 1, "myPath"));
    webhookConnectorRegistry.register(connectorA);

    var connectorB = buildConnector(webhookProperties("processA", 1, "myPath"));
    assertThrowsExactly(
        RuntimeException.class, () -> webhookConnectorRegistry.register(connectorB));
  }

  @Test
  public void webhookMultipleVersionsDisableWebhook() throws Exception {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    var processA1 = buildConnector(webhookProperties("processA", 1, "myPath"));
    var processA2 = buildConnector(webhookProperties("processA", 2, "myPath"));
    var processB1 = buildConnector(webhookProperties("processB", 1, "myPath2"));

    webhook.register(processA1);
    webhook.deregister(processA1);

    webhook.register(processA2);

    webhook.register(processB1);
    webhook.deregister(processB1);

    var connectorForPath1 = webhook.getWebhookConnectorByContextPath("myPath");

    assertTrue(connectorForPath1.isPresent(), "Connector is present");
    assertEquals(2, connectorForPath1.get().properties().getVersion(), "The newest one");

    var connectorForPath2 = webhook.getWebhookConnectorByContextPath("myPath2");
    assertTrue(connectorForPath2.isEmpty(), "No one - as it was deleted.");
  }

  @Test
  public void webhookDeactivation_shouldReturnNotFound() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookProperties("processA", 1, "myPath"));

    // when
    webhook.register(processA1);
    webhook.deregister(processA1);

    // then
    assertFalse(webhook.getWebhookConnectorByContextPath("myPath").isPresent());
  }

  private static long nextProcessDefinitionKey = 0L;

  public static ActiveInboundConnector buildConnector(InboundConnectorProperties properties) {
    WebhookConnectorExecutable executable = Mockito.mock(WebhookConnectorExecutable.class);
    try {
      Mockito.when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
          .thenReturn(Mockito.mock(WebhookProcessingResult.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new ActiveInboundConnector(executable, properties, buildContext(properties));
  }

  public static InboundConnectorContextImpl buildContext(InboundConnectorProperties properties) {
    final Map<String, String> secrets = new HashMap<>();
    SecretProvider secretProvider = secrets::get;
    return new InboundConnectorContextImpl(secretProvider, e -> {}, properties, null, (x) -> {});
  }

  public static InboundConnectorProperties webhookProperties(
      String bpmnProcessId, int version, String contextPath) {
    return webhookProperties(++nextProcessDefinitionKey, bpmnProcessId, version, contextPath);
  }

  public static InboundConnectorProperties webhookProperties(
      long processDefinitionKey, String bpmnProcessId, int version, String contextPath) {

    return new InboundConnectorProperties(
        new StartEventCorrelationPoint(processDefinitionKey, bpmnProcessId, version),
        Map.of(
            "inbound.type", "webhook",
            "inbound.context", contextPath,
            "inbound.secretExtractor", "=\"TEST\"",
            "inbound.secret", "TEST",
            "inbound.activationCondition", "=true",
            "inbound.variableMapping", "={}"),
        bpmnProcessId,
        version,
        processDefinitionKey,
        "testElement");
  }
}
