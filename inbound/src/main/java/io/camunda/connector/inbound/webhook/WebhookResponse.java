package io.camunda.connector.inbound.webhook;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Define how much information we want to expose as result
public class WebhookResponse {

    private List<String> unauthorizedConnectors = new ArrayList<>();
    private List<String> unactivatedConnectors = new ArrayList<>();
    private Map<String, ProcessInstanceEvent> executedConnectors = new HashMap<>();
    private List<String> errors = new ArrayList<>();

    public void addUnauthorizedConnector(WebhookConnectorProperties connectorProperties) {
        unauthorizedConnectors.add(connectorProperties.getConnectorIdentifier());
    }

    public void addUnactivatedConnector(WebhookConnectorProperties connectorProperties) {
        unactivatedConnectors.add(connectorProperties.getConnectorIdentifier());
    }

    public void addExecutedConnector(WebhookConnectorProperties connectorProperties, ProcessInstanceEvent processInstanceEvent) {
        executedConnectors.put(connectorProperties.getConnectorIdentifier(), processInstanceEvent);
    }

    public void addException(WebhookConnectorProperties connectorProperties, Exception exception) {
        errors.add(connectorProperties.getConnectorIdentifier() + ">" + exception.getMessage());
    }

    public List<String> getUnauthorizedConnectors() {
        return unauthorizedConnectors;
    }

    public List<String> getUnactivatedConnectors() {
        return unactivatedConnectors;
    }

    public Map<String, ProcessInstanceEvent> getExecutedConnectors() {
        return executedConnectors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
