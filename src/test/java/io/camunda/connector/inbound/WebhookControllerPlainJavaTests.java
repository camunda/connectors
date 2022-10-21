package io.camunda.connector.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.inbound.feel.FeelEngineWrapper;
import io.camunda.connector.inbound.registry.InboundConnectorProperties;
import io.camunda.connector.inbound.registry.InboundConnectorRegistry;
import io.camunda.connector.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.inbound.webhook.WebhookResponse;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WebhookControllerPlainJavaTests {

    @Test
    public void multipleWebhooksOnSameContextPath() throws IOException {
        InboundConnectorRegistry registry = new InboundConnectorRegistry();
        ZeebeClient zeebeClient = mock(ZeebeClient.class);
        when(zeebeClient.newCreateInstanceCommand()).thenReturn(new CreateCommandDummy());
        InboundWebhookRestController controller = new InboundWebhookRestController(registry, zeebeClient, new FeelEngineWrapper(), new ObjectMapper());

        registry.reset();
        registry.registerWebhookConnector(webhookProperties("processA", "myPath"));
        registry.registerWebhookConnector(webhookProperties("processB", "myPath"));;


        ResponseEntity<WebhookResponse> responseEntity = controller.inbound("myPath", "{}".getBytes(), new HashMap<>());

        assertEquals(200, responseEntity.getStatusCode().value());
        assertTrue(responseEntity.getBody().getUnauthorizedConnectors().isEmpty());
        assertTrue(responseEntity.getBody().getUnactivatedConnectors().isEmpty());
        assertEquals(2,
                responseEntity.getBody().getExecutedConnectors().size());
        assertEquals(Set.of("webhook-myPath-processA-1", "webhook-myPath-processB-1"),
                responseEntity.getBody().getExecutedConnectors().keySet());

    }


    public static InboundConnectorProperties webhookProperties(String bpmnProcessId, String contextPath) {
        return new InboundConnectorProperties(bpmnProcessId, 1, 123l, Map.of(
                "inbound.type", "webhook",
                "inbound.context", contextPath,
                "inbound.secretExtractor", "=\"TEST\"",
                "inbound.secret", "TEST",
                "inbound.activationCondition", "=true",
                "inbound.variableMapping", "={}"
        ));
    }

    public static class ProcessInstanceEventDummy implements ProcessInstanceEvent {
        public long getProcessDefinitionKey () {
            return 0;
        }
        public String getBpmnProcessId () {
            return null;
        }
        public int getVersion () {
            return 0;
        }
        public long getProcessInstanceKey () {
            return 0;
        }
    }

    public static class CreateCommandDummy implements CreateProcessInstanceCommandStep1, CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2, CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 {
        public CreateProcessInstanceCommandStep2 bpmnProcessId(String bpmnProcessId) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 processDefinitionKey(long processDefinitionKey) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 version(int version) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 latestVersion() {
            return this;
        }
        public CreateProcessInstanceCommandStep3 variables(InputStream variables) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 variables(String variables) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 variables(Map<String, Object> variables) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 variables(Object variables) {
            return this;
        }
        public CreateProcessInstanceCommandStep3 startBeforeElement(String elementId) {
            return this;
        }
        public CreateProcessInstanceWithResultCommandStep1 withResult() {
            return null;
        }
        public FinalCommandStep<ProcessInstanceEvent> requestTimeout(Duration requestTimeout) {
            return null;
        }
        public ZeebeFuture<ProcessInstanceEvent> send() {
            ZeebeClientFutureImpl future = new ZeebeClientFutureImpl<>();
            future.complete( new ProcessInstanceEventDummy() );
            return future;
        }
    }
}
