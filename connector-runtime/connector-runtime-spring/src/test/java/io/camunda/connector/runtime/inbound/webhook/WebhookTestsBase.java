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
package io.camunda.connector.runtime.inbound.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

public abstract class WebhookTestsBase {

  private static final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();

  public static RegisteredExecutable.Activated buildConnector(
      String bpmnProcessId, int version, String path) {
    var connectorData = webhookDefinition(bpmnProcessId, version, path);
    WebhookConnectorExecutable executable = mock(WebhookConnectorExecutable.class);
    try {
      Mockito.when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
          .thenReturn(mock(WebhookResult.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new RegisteredExecutable.Activated(executable, buildContext(connectorData));
  }

  private static InboundConnectorContextImpl buildContext(
      InboundConnectorDetails.ValidInboundConnectorDetails def) {
    var context =
        new InboundConnectorContextImpl(
            new NullSecretProvider(),
            new DefaultValidationProvider(),
            def,
            mock(InboundCorrelationHandler.class),
            e -> {},
            mapper,
            EvictingQueue.create(10));

    context.reportHealth(Health.up());
    return spy(context);
  }

  private static InboundConnectorDetails.ValidInboundConnectorDetails webhookDefinition(
      String bpmnProcessId, int version, String path) {
    var details =
        InboundConnectorDetails.of(
            bpmnProcessId + version + path,
            List.of(
                webhookElement(
                    (bpmnProcessId + version).hashCode(), bpmnProcessId, version, path)));
    assertThat(details).isInstanceOf(InboundConnectorDetails.ValidInboundConnectorDetails.class);
    return (InboundConnectorDetails.ValidInboundConnectorDetails) details;
  }

  private static InboundConnectorElement webhookElement(
      long processDefinitionKey, String bpmnProcessId, int version, String path) {

    return new InboundConnectorElement(
        Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
        new StartEventCorrelationPoint(bpmnProcessId, version, processDefinitionKey),
        new ProcessElement(
            bpmnProcessId, version, processDefinitionKey, "testElement", "<default>"));
  }

  private static class NullSecretProvider implements SecretProvider {
    @Override
    public String getSecret(String name, SecretContext context) {
      return null;
    }
  }

  boolean isRegistered(
      WebhookConnectorRegistry registry, RegisteredExecutable.Activated connector) {
    return registry.getExecutablesByContext().values().stream()
        .flatMap(executables -> executables.getAllExecutables().stream())
        .anyMatch(e -> e.equals(connector));
  }
}
