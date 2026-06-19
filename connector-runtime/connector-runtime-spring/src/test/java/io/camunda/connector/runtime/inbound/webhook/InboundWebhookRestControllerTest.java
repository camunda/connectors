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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

class InboundWebhookRestControllerTest {

  @Test
  void shouldLogRequestDetailsWithRedactionAndTruncation() throws Exception {
    var activityLogRegistry = new ActivityLogRegistry();
    var connector = buildConnector(activityLogRegistry);
    var webhookConnectorRegistry = new WebhookConnectorRegistry();
    webhookConnectorRegistry.register(connector);
    var controller = new InboundWebhookRestController(webhookConnectorRegistry);

    var request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("example.com");
    request.setServerPort(443);
    request.setRequestURI("/inbound/myPath");
    request.setMethod("POST");
    request.setContent("a".repeat(1005).getBytes(StandardCharsets.UTF_8));

    controller.inbound(
        "myPath",
        Map.of("authorization", "******", "x-test", "visible"),
        Map.of("token", "secret-token", "q", "visible"),
        request);

    var latestActivity = latestActivity(activityLogRegistry, connector.id());
    assertThat(latestActivity.message())
        .contains("POST https://example.com")
        .contains("/inbound/myPath")
        .contains("authorization: [redacted]")
        .contains("x-test: visible")
        .contains("token=[redacted]")
        .contains("q=visible")
        .contains("Body: " + "a".repeat(1000))
        .contains("... (truncated)");
  }

  @Test
  void shouldOmitBodyFromLogWhenMultipartRequestContainsParts() throws Exception {
    var activityLogRegistry = new ActivityLogRegistry();
    var connector = buildConnector(activityLogRegistry);
    var webhookConnectorRegistry = new WebhookConnectorRegistry();
    webhookConnectorRegistry.register(connector);
    var controller = new InboundWebhookRestController(webhookConnectorRegistry);

    var request = new MockMultipartHttpServletRequest();
    request.setScheme("https");
    request.setServerName("example.com");
    request.setServerPort(443);
    request.setRequestURI("/inbound/myPath");
    request.setMethod("POST");
    request.addFile(
        new MockMultipartFile(
            "file", "test.txt", "text/plain", "top secret file contents".getBytes()));
    request.setContent("top secret file contents".getBytes(StandardCharsets.UTF_8));

    controller.inbound("myPath", new HashMap<>(), new HashMap<>(), request);

    var latestActivity = latestActivity(activityLogRegistry, connector.id());
    assertThat(latestActivity.message())
        .contains("POST https://example.com")
        .contains("/inbound/myPath")
        .contains("Body: (omitted for multipart request)")
        .contains("Parts (1):")
        .doesNotContain("top secret file contents");
  }

  private static io.camunda.connector.api.inbound.Activity latestActivity(
      ActivityLogRegistry registry, ExecutableId executableId) {
    return registry.getLogs(executableId).stream().reduce((first, second) -> second).orElseThrow();
  }

  private static RegisteredExecutable.Activated buildConnector(ActivityLogRegistry activityLogRegistry)
      throws Exception {
    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(executable.triggerWebhook(any(WebhookProcessingPayload.class))).thenReturn(webhookResult);

    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(anyList(), any()))
        .thenThrow(new ConnectorInputException("invalid input"));

    var details = webhookDefinition("processA", 1, "myPath");
    var context =
        new InboundConnectorContextImpl(
            new NullSecretProvider(),
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            ConnectorsObjectMapperSupplier.getCopy(),
            activityLogRegistry,
            mock(CamundaClient.class));

    return new RegisteredExecutable.Activated(
        executable, context, ExecutableId.fromDeduplicationId(details.deduplicationId()));
  }

  private static InboundConnectorDetails.ValidInboundConnectorDetails webhookDefinition(
      String bpmnProcessId, int version, String path) {
    return (InboundConnectorDetails.ValidInboundConnectorDetails)
        InboundConnectorDetails.of(
            bpmnProcessId + version + path,
            List.of(
                new InboundConnectorElement(
                    Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
                    new StartEventCorrelationPoint(
                        bpmnProcessId, version, (bpmnProcessId + version).hashCode()),
                    new ProcessElementWithRuntimeData(
                        bpmnProcessId,
                        version,
                        (bpmnProcessId + version).hashCode(),
                        "testElement",
                        "<default>"))));
  }

  private static class NullSecretProvider implements SecretProvider {
    @Override
    public String getSecret(String name, SecretContext context) {
      return null;
    }
  }
}
