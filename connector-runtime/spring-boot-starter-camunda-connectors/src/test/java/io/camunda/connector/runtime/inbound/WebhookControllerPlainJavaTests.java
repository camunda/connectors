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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookControllerPlainJavaTests {

  private static final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Captor ArgumentCaptor<Activity> activityCaptor;

  @Test
  public void multipleWebhooksOnSameContextPathAreNotSupported() {
    WebhookConnectorRegistry webhookConnectorRegistry = new WebhookConnectorRegistry();
    var connectorA = buildConnector(webhookDefinition("processA", 1, "myPath"));
    webhookConnectorRegistry.register(connectorA);

    var connectorB = buildConnector(webhookDefinition("processA", 1, "myPath"));
    assertThrowsExactly(
        RuntimeException.class, () -> webhookConnectorRegistry.register(connectorB));
  }

  @Test
  public void webhookMultipleVersionsDisableWebhook() {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    var processA1 = buildConnector(webhookDefinition("processA", 1, "myPath"));
    var processA2 = buildConnector(webhookDefinition("processA", 2, "myPath"));
    var processB1 = buildConnector(webhookDefinition("processB", 1, "myPath2"));

    webhook.register(processA1);
    webhook.deregister(processA1);

    webhook.register(processA2);

    webhook.register(processB1);
    webhook.deregister(processB1);

    var connectorForPath1 = webhook.getWebhookConnectorByContextPath("myPath");

    assertTrue(connectorForPath1.isPresent(), "Connector is present");
    assertEquals(2, connectorForPath1.get().context().getDefinition().version(), "The newest one");

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
  }

  private static Stream<Arguments> invalidCases() {
    return Stream.of(
        Arguments.of("&20encoded+whitespace"),
        Arguments.of("€"),
        Arguments.of("-my-path"),
        Arguments.of("my_path_"));
  }

  private static Stream<Arguments> validCases() {
    return Stream.of(
        Arguments.of("z"),
        Arguments.of("validAlphaOnly"),
        Arguments.of("hello-world"),
        Arguments.of("123-456_789"));
  }

  @ParameterizedTest
  @MethodSource("invalidCases")
  public void webhookPathLogIfInvalidCharacters(String webhookPath) {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, webhookPath));

    // when
    webhook.register(processA1);

    // then
    verify(processA1.context(), times(1)).log(activityCaptor.capture());
    assertEquals(Severity.WARNING, activityCaptor.getValue().severity());

    var existingConnector = webhook.getWebhookConnectorByContextPath(webhookPath);
    assertTrue(existingConnector.isPresent());
    assertEquals(processA1, existingConnector.get());
  }

  @ParameterizedTest
  @MethodSource("validCases")
  public void webhookPathDontLogValidCharacters(String webhookPath) {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, webhookPath));

    // when
    webhook.register(processA1);

    // then
    verify(processA1.context(), times(0)).log(activityCaptor.capture());
  }

  private static long nextProcessDefinitionKey = 0L;

  public static ActiveInboundConnector buildConnector(InboundConnectorDefinitionImpl definition) {
    WebhookConnectorExecutable executable = mock(WebhookConnectorExecutable.class);
    try {
      Mockito.when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
          .thenReturn(mock(WebhookResult.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new ActiveInboundConnector(executable, buildContext(definition));
  }

  public static InboundConnectorContextImpl buildContext(InboundConnectorDefinitionImpl def) {
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

  public static InboundConnectorDefinitionImpl webhookDefinition(
      String bpmnProcessId, int version, String path) {
    return webhookDefinition(++nextProcessDefinitionKey, bpmnProcessId, version, path);
  }

  public static InboundConnectorDefinitionImpl webhookDefinition(
      long processDefinitionKey, String bpmnProcessId, int version, String path) {

    return new InboundConnectorDefinitionImpl(
        Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
        new StartEventCorrelationPoint(bpmnProcessId, version, processDefinitionKey),
        bpmnProcessId,
        version,
        processDefinitionKey,
        "testElement",
        "testTenantId");
  }
}
