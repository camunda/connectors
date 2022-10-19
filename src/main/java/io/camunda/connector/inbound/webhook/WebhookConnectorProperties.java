package io.camunda.connector.inbound.webhook;

import io.camunda.connector.inbound.registry.InboundConnectorProperties;

public class WebhookConnectorProperties {

    public InboundConnectorProperties genericProperties;

    public WebhookConnectorProperties(InboundConnectorProperties properties) {
        this.genericProperties = properties;
    }

    public String getContext() {
        return genericProperties.getProperties().get("inbound.context");
    }
    public String getSecretExtractor() {
        return genericProperties.getProperties().get("inbound.secretExtractor");
    }
    public String getSecret() {
        return genericProperties.getProperties().get("inbound.secret");
    }
    public String getActivationCondition() {
        return genericProperties.getProperties().get("inbound.activationCondition");
    }
    public String getVariableMapping() {
        return genericProperties.getProperties().get("inbound.variableMapping");
    }

    public String getBpmnProcessId() {
        return genericProperties.getBpmnProcessId();
    }

    public int getVersion() {
        return genericProperties.getVersion();
    }

    public String getType() {
        return genericProperties.getType();
    }

    public long getProcessDefinitionKey() {
        return genericProperties.getProcessDefinitionKey();
    }

    @Override
    public String toString() {
        return "WebhookConnectorProperties-" + genericProperties.toString();
    }
}
