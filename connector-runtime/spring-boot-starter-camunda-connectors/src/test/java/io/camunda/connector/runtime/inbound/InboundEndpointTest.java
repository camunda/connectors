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
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.controller.InboundConnectorRestController;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class InboundEndpointTest {

  private static final ExecutableId RANDOM_ID =
      ExecutableId.fromDeduplicationId(RandomStringUtils.insecure().next(10));

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
                    RANDOM_ID,
                    TestWebhookExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", "webhook"),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElementWithRuntimeData("", 1, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry, null);

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
                    RANDOM_ID,
                    null, // executable class is null
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", "webhook"),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElementWithRuntimeData("", 1, 1, "", ""))),
                    Health.down(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry, null);

    var response = statusController.getActiveInboundConnectors(null, null, null);
    assertEquals(1, response.size());
    assertEquals(Health.down(), (response.getFirst()).health());
  }
}
