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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.runtime.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.inbound.webhook.WebhookResponse;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.camunda.zeebe.spring.client.metrics.SimpleMetricsRecorder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class WebhookControllerPlainJavaTests {

  private static final String CONNECTOR_SECRET_NAME = "DUMMY_SECRET";
  private static final String CONNECTOR_SECRET_VALUE = "s3cr3T";

  private SimpleMetricsRecorder metrics;

  @BeforeEach
  public void setupMetrics() {
    metrics = new SimpleMetricsRecorder();
  }

  @Test
  public void multipleWebhooksOnSameContextPath() throws Exception {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    WebhookConnectorExecutable executable = Mockito.mock(WebhookConnectorExecutable.class);
    Mockito.when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenReturn(Mockito.mock(WebhookProcessingResult.class));

    // Register webhook function 'implementation'
    webhook.registerWebhookFunction("webhook", executable);

    InboundConnectorContext webhookA =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processA", 1, "myPath"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .result(new MessageCorrelationResult("", 0))
            .build();

    InboundConnectorContext webhookB =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processB", 1, "myPath"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .result(new MessageCorrelationResult("", 0))
            .build();

    InboundWebhookRestController controller = new InboundWebhookRestController(webhook, metrics);

    // Register processes
    webhook.activateEndpoint(webhookA);
    webhook.activateEndpoint(webhookB);

    ResponseEntity<WebhookResponse> responseEntity =
        controller.inbound(
            "myPath",
            new HashMap<>(),
            "{}".getBytes(),
            new HashMap<>(),
            new MockHttpServletRequest());

    assertEquals(200, responseEntity.getStatusCode().value());
    assertTrue(responseEntity.getBody().getUnactivatedConnectors().isEmpty());
    assertEquals(2, responseEntity.getBody().getExecutedConnectors().size());
    assertEquals(
        Set.of("myPath-processA-1", "myPath-processB-1"),
        responseEntity.getBody().getExecutedConnectors().keySet());
    assertEquals(
        1,
        metrics.getCount(
            MetricsRecorder.METRIC_NAME_INBOUND_CONNECTOR,
            MetricsRecorder.ACTION_ACTIVATED,
            InboundWebhookRestController.METRIC_WEBHOOK_VALUE));
    assertEquals(
        1,
        metrics.getCount(
            MetricsRecorder.METRIC_NAME_INBOUND_CONNECTOR,
            MetricsRecorder.ACTION_COMPLETED,
            InboundWebhookRestController.METRIC_WEBHOOK_VALUE));
    assertEquals(
        0,
        metrics.getCount(
            MetricsRecorder.METRIC_NAME_INBOUND_CONNECTOR,
            MetricsRecorder.ACTION_FAILED,
            InboundWebhookRestController.METRIC_WEBHOOK_VALUE));
  }

  @Test
  public void webhookMultipleVersionsDisableWebhook() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    var processA1 =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processA", 1, "myPath"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .build();

    var processA2 =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processA", 2, "myPath"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .build();

    var processB1 =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processB", 1, "myPath2"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .build();

    webhook.activateEndpoint(processA1);
    webhook.deactivateEndpoint(processA1.getProperties());

    webhook.activateEndpoint(processA2);

    webhook.activateEndpoint(processB1);
    webhook.deactivateEndpoint(processB1.getProperties());

    Collection<InboundConnectorContext> connectors1 =
        webhook.getWebhookConnectorByContextPath("myPath");

    assertEquals(1, connectors1.size()); // only one
    assertEquals(
        2, connectors1.iterator().next().getProperties().getVersion()); // And the newest one

    Collection<InboundConnectorContext> connectors2 =
        webhook.getWebhookConnectorByContextPath("myPath2");
    assertEquals(0, connectors2.size()); // No one - as it was disabled
  }

  @Test
  public void webhookDeactivation_shouldReturnNotFound() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 =
        new InboundConnectorContextBuilder()
            .properties(webhookProperties("processA", 1, "myPath"))
            .secret(CONNECTOR_SECRET_NAME, CONNECTOR_SECRET_VALUE)
            .build();

    // when
    webhook.activateEndpoint(processA1);
    webhook.deactivateEndpoint(processA1.getProperties());

    // then
    assertFalse(webhook.containsContextPath("myPath"));
  }

  private static long nextProcessDefinitionKey = 0L;

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
