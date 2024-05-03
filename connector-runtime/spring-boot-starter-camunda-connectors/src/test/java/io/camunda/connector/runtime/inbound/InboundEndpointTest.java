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
  public void testInboundEndpointResponse() {
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
                                "myPath", "=expression", "=myPath", null),
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
