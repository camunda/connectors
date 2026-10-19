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
package io.camunda.connector.runtime.inbound.executable;

import static io.camunda.connector.runtime.inbound.webhook.WebhookTestsBase.buildConnector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchExecutableProcessorWebhookTest {

  private static final String PATH = "myPath";

  private InboundConnectorFactory factory;
  private InboundConnectorContextFactory contextFactory;
  private WebhookConnectorRegistry webhookRegistry;
  private BatchExecutableProcessor batchProcessor;

  @BeforeEach
  void setUp() {
    factory = mock(InboundConnectorFactory.class);
    contextFactory = mock(DefaultInboundConnectorContextFactory.class);
    webhookRegistry = new WebhookConnectorRegistry();
    batchProcessor =
        new BatchExecutableProcessor(
            factory,
            contextFactory,
            mock(ConnectorsInboundMetrics.class),
            webhookRegistry,
            new ActivityLogRegistry());
  }

  @Test
  void failedActivation_shouldDeregisterWebhook() throws Exception {
    // given
    var connector = buildConnector("processA", 1, PATH);
    doThrow(new RuntimeException("activation failed")).when(connector.executable()).activate(any());

    // when
    var result = activate(connector);

    // then
    assertThat(result).isInstanceOf(FailedToActivate.class);
    assertThat(((FailedToActivate) result).reason()).isEqualTo("activation failed");
    assertThat(webhookRegistry.getExecutablesByContext()).doesNotContainKey(PATH);
  }

  @Test
  void correctedRedeploy_afterFailedActivation_shouldBecomeActive() throws Exception {
    // given - a first deployment whose activation fails
    var broken = buildConnector("processA", 1, PATH);
    doThrow(new RuntimeException("activation failed")).when(broken.executable()).activate(any());
    assertThat(activate(broken)).isInstanceOf(FailedToActivate.class);

    // when - the same webhook is redeployed with a configuration that activates fine
    var corrected = buildConnector("processA", 1, PATH);
    var result = activate(corrected);

    // then
    assertThat(result).isInstanceOf(Activated.class);
    verify(corrected.executable()).activate(corrected.context());
    assertThat(webhookRegistry.getActiveWebhook(PATH))
        .hasValueSatisfying(
            active -> assertThat(active.executable()).isSameAs(corrected.executable()));
  }

  private RegisteredExecutable activate(Activated connector) {
    when(factory.getInstance(any())).thenReturn(connector.executable());
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(connector.context());
    var definition = connector.context().getDefinition();
    var details =
        InboundConnectorDetails.of(
            definition.deduplicationId(), connector.context().connectorElements());
    var id = ExecutableId.fromDeduplicationId(definition.deduplicationId());
    return batchProcessor.activateBatch(Map.of(id, details), cancelled -> {}).get(id);
  }
}
