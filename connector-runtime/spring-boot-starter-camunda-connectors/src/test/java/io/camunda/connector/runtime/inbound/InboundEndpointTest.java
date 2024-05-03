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
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.controller.InboundConnectorRestController;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class InboundEndpointTest {

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
                                "myPath", "=expression", "=myPath"),
                            new ProcessElement("", 1, 1, "", ""))),
                    Health.up(),
                    Collections.emptyList())));

    InboundConnectorRestController statusController =
        new InboundConnectorRestController(executableRegistry);

    var response = statusController.getActiveInboundConnectors(null, null, null);
    assertEquals(1, response.size());
    assertEquals("myPath", response.get(0).data().get("path"));
  }
}
