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
package io.camunda.connector.runtime.inbound.lifecycle;

import static io.camunda.connector.runtime.inbound.ProcessDefinitionTestUtil.processDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestInboundConnector;
import io.camunda.connector.runtime.app.TestWebhookConnector;
import io.camunda.connector.runtime.core.ConnectorUtil;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.ProcessDefinitionTestUtil;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionInspector;
import io.camunda.connector.runtime.inbound.operate.OperateClientAdapterImpl;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.model.ProcessDefinition;
import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InboundConnectorManagerTest {

  private InboundConnectorManager manager;
  private ProcessDefinitionTestUtil procDefUtil;
  private InboundConnectorFactory factory;
  private InboundConnectorContextFactory contextFactory;
  private InboundConnectorExecutable inboundConnectorExecutable;
  private WebhookConnectorExecutable webhookConnectorExecutable;
  private WebhookConnectorRegistry webhookRegistry;
  private SecretProviderAggregator secretProviderAggregator;
  private InboundCorrelationHandler correlationHandler;
  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @BeforeEach
  void resetMocks() {
    correlationHandler = mock(InboundCorrelationHandler.class);

    inboundConnectorExecutable = spy(new TestInboundConnector());
    webhookConnectorExecutable = spy(new TestWebhookConnector());
    webhookRegistry = new WebhookConnectorRegistry();
    factory = mock(InboundConnectorFactory.class);
    when(factory.getInstance(any())).thenReturn(inboundConnectorExecutable);

    secretProviderAggregator = mock(SecretProviderAggregator.class);

    ProcessDefinitionInspector inspector = mock(ProcessDefinitionInspector.class);
    CamundaOperateClient camundaOperateClient = mock(CamundaOperateClient.class);

    contextFactory =
        new DefaultInboundConnectorContextFactory(
            mapper,
            correlationHandler,
            secretProviderAggregator,
            v -> {},
            new OperateClientAdapterImpl(camundaOperateClient, mapper));

    manager =
        new InboundConnectorManager(
            factory, contextFactory, inspector, new DefaultNoopMetricsRecorder(), webhookRegistry);
    procDefUtil = new ProcessDefinitionTestUtil(manager, inspector);
  }

  @Test
  void shouldActivateConnector_NewBpmnDeployed_SingleConnector() throws Exception {
    // given
    var process = processDefinition("proc1", 1);
    var connector = inboundConnector(process);

    // when
    procDefUtil.deployProcessDefinition(process, connector);

    // then
    assertTrue(manager.isProcessDefinitionRegistered(process.getKey()));
    verify(factory, times(1)).getInstance(connector.type());
    verify(inboundConnectorExecutable, times(1)).activate(eq(inboundContext(connector)));
  }

  @Test
  void shouldNotActivate_NewBpmnDeployed_NoConnectors() throws Exception {
    // given
    var process = processDefinition("proc1", 1);

    // when
    procDefUtil.deployProcessDefinition(process, Collections.emptyList());

    // then
    assertTrue(manager.isProcessDefinitionRegistered(process.getKey()));
    verifyNoInteractions(factory);
    verifyNoInteractions(inboundConnectorExecutable);
  }

  @Test
  void shouldHandleCancellationCallback() throws Exception {
    // given
    var process = processDefinition("proc1", 1);
    var connector = inboundConnector(process);

    // when
    procDefUtil.deployProcessDefinition(process, connector);
    var context = ((TestInboundConnector) inboundConnectorExecutable).getProvidedContext();
    context.cancel(new RuntimeException("subscription interrupted"));

    // then
    assertTrue(manager.isProcessDefinitionRegistered(process.getKey()));
    assertTrue(
        manager
            .query(new ActiveInboundConnectorQuery(process.getBpmnProcessId(), null, null))
            .isEmpty());

    verify(inboundConnectorExecutable, times(1)).activate(eq(inboundContext(connector)));
    verify(inboundConnectorExecutable, times(1)).deactivate();
  }

  @Test
  void shouldActivateAndRegisterWebhook() throws Exception {
    when(factory.getInstance("io.camunda:test-webhook:1")).thenReturn(webhookConnectorExecutable);
    var process = processDefinition("webhook1", 1);
    var webhook = webhookConnector(process);
    procDefUtil.deployProcessDefinition(process, webhook);
    verify(webhookConnectorExecutable, times(1)).activate(eq(inboundContext(webhook)));
  }

  @Test
  void shouldActivateAndRegisterWebhookWithANewVersion() throws Exception {
    when(factory.getInstance(webhookConfig.type())).thenReturn(webhookConnectorExecutable);

    // Deploy one process with a webhook
    var pv1 = processDefinition("webhook1", 1);
    var wh1 = webhookConnector(pv1);
    procDefUtil.deployProcessDefinition(pv1, wh1);

    // De-register webhook
    manager.handleDeletedProcessDefinitions(Set.of(pv1.getKey()));

    // Deploy a new version of the process
    var pv2 = processDefinition("webhook1", 2);
    var wh2 = webhookConnector(pv2);
    procDefUtil.deployProcessDefinition(pv2, wh2);

    verify(factory, times(2)).getInstance(webhookConfig.type());
    verify(webhookConnectorExecutable, times(1)).activate(eq(inboundContext(wh1)));
    verify(webhookConnectorExecutable).deactivate();
    verify(webhookConnectorExecutable, times(1)).activate(eq(inboundContext(wh2)));
    verifyNoMoreInteractions(inboundConnectorExecutable);

    // New version should be active
    var connector = webhookRegistry.getWebhookConnectorByContextPath("myWebhookEndpoint");
    assertEquals(2, connector.get().context().getDefinition().version());
  }

  @Test
  void shouldNotActivateWebhookWhenDisabled() throws Exception {
    // Given
    ProcessDefinitionInspector inspector = mock(ProcessDefinitionInspector.class);
    // webhook connector registry is set to null,
    // emulating camunda.connector.webhook.enabled=false
    manager =
        new InboundConnectorManager(
            factory, contextFactory, inspector, new DefaultNoopMetricsRecorder(), null);
    procDefUtil = new ProcessDefinitionTestUtil(manager, inspector);

    when(factory.getInstance("io.camunda:test-webhook:1")).thenReturn(webhookConnectorExecutable);
    var process = processDefinition("webhook1", 1);
    var webhook = webhookConnector(process);

    // When
    procDefUtil.deployProcessDefinition(process, webhook);

    // Then
    verify(webhookConnectorExecutable, times(0)).activate(eq(inboundContext(webhook)));

    var query = new ActiveInboundConnectorQuery("webhook1", null, null);
    var activeInboundConnectors = manager.query(query);
    assertEquals(
        "webhook1", activeInboundConnectors.get(0).context().getDefinition().bpmnProcessId());
  }

  private InboundConnectorContext inboundContext(InboundConnectorDefinitionImpl definition) {
    return new InboundConnectorContextImpl(
        secretProviderAggregator, v -> {}, definition, correlationHandler, (event) -> {}, mapper);
  }

  private static final InboundConnectorConfiguration connectorConfig =
      ConnectorUtil.getRequiredInboundConnectorConfiguration(TestInboundConnector.class);

  private static final InboundConnectorConfiguration webhookConfig =
      ConnectorUtil.getRequiredInboundConnectorConfiguration(TestWebhookConnector.class);

  private static InboundConnectorDefinitionImpl inboundConnector(ProcessDefinition procDef) {
    return new InboundConnectorDefinitionImpl(
        Map.of(Keywords.INBOUND_TYPE_KEYWORD, connectorConfig.type()),
        new MessageCorrelationPoint("", "", null),
        procDef.getBpmnProcessId(),
        procDef.getVersion().intValue(),
        procDef.getKey(),
        "test-element",
        "test-tenant");
  }

  private static InboundConnectorDefinitionImpl webhookConnector(ProcessDefinition procDef) {
    return new InboundConnectorDefinitionImpl(
        Map.of(
            Keywords.INBOUND_TYPE_KEYWORD,
            webhookConfig.type(),
            "inbound.context",
            "myWebhookEndpoint"),
        new MessageCorrelationPoint("", "", null),
        procDef.getBpmnProcessId(),
        procDef.getVersion().intValue(),
        procDef.getKey(),
        "test-element",
        "test-tenant");
  }
}
