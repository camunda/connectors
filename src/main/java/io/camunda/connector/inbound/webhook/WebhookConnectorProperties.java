package io.camunda.connector.inbound.webhook;

import io.camunda.connector.inbound.registry.InboundConnectorProperties;

public class WebhookConnectorProperties {

    public InboundConnectorProperties genericProperties;

    public WebhookConnectorProperties(InboundConnectorProperties properties) {
        this.genericProperties = properties;
    }

    public String getConnectorIdentifier() {
        return "" + genericProperties.getType() + "-" + getContext() + "-" + genericProperties.getBpmnProcessId() + "-" + genericProperties.getVersion();
    }
    public String readProperty(String propertyName) {
        String result = genericProperties.getProperties().get(propertyName);
        if (result==null) {
            throw new IllegalArgumentException("Property '"+propertyName+"' must be set for connector");
        }
        return result;
    }

    public String getContext() {
        return readProperty("inbound.context");
    }
    public String getSecretExtractor() {
        return readProperty("inbound.secretExtractor");
    }
    public String getSecret() {
        return readProperty("inbound.secret");
    }
    public String getActivationCondition() {
        return readProperty("inbound.activationCondition");
    }
    public String getVariableMapping() {
        return readProperty("inbound.variableMapping");
    }

    // Security / HMAC Validation

    // Dropdown that indicates whether customer wants to validate webhook request with HMAC. Values: enabled | disabled
    public String shouldValidateHMAC() {
        return genericProperties.getProperties().getOrDefault("inbound.shouldValidateHmac", "disabled");
    }
    // HMAC secret token. An arbitrary String, example 'mySecretToken'. Is it the same as getSecret(...)?
    public String getHMACSecret() {
        return genericProperties.getProperties().get("inbound.hmacSecret");
    }
    // Indicates which header is used to store HMAC signature. Example, X-Hub-Signature-256
    public String getHMACHeader() {
        return genericProperties.getProperties().get("inbound.hmacHeader");
    }
    // Indicates which algorithm was used to produce HMAC signature. Should correlate enum names of io.camunda.connector.inbound.security.signature.HMACAlgoCustomerChoice
    public String getHMACAlgo() {
        return genericProperties.getProperties().get("inbound.hmacAlgorithm");
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
