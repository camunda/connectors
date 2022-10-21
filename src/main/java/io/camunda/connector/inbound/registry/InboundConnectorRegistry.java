package io.camunda.connector.inbound.registry;

import io.camunda.connector.inbound.webhook.WebhookConnectorProperties;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InboundConnectorRegistry {

    private Set<Long> registeredProcessDefinitionKeys = new HashSet<>();
    private Map<String, List<WebhookConnectorProperties>> registeredWebhookConnectorsByContextPath = new HashMap<>();
    //private List<InboundConnectorProperties> registeredInboundConnectors = new ArrayList<>();

    /**
     * Reset registry and forget about all connectors, especially useful in tests when the context needs to get cleared
     */
    public void reset() {
        registeredProcessDefinitionKeys = new HashSet<>();
        registeredWebhookConnectorsByContextPath = new HashMap<>();
    }

    public boolean processDefinitionChecked(long processDefinitionKey) {
        return registeredProcessDefinitionKeys.contains(processDefinitionKey);
    }

    public void markProcessDefinitionChecked(long processDefinitionKey) {
        registeredProcessDefinitionKeys.add(processDefinitionKey);
    }

    public void registerWebhookConnector(InboundConnectorProperties properties) {
        registeredProcessDefinitionKeys.add(properties.getProcessDefinitionKey());
        WebhookConnectorProperties webhookConnectorProperties = new WebhookConnectorProperties(properties);
        String context = webhookConnectorProperties.getContext();

        if (!registeredWebhookConnectorsByContextPath.containsKey(context)) {
            registeredWebhookConnectorsByContextPath.put(context, new ArrayList<>());
        }
        registeredWebhookConnectorsByContextPath.get(context).add(webhookConnectorProperties);
    }

    public boolean containsContextPath(String context) {
        return registeredWebhookConnectorsByContextPath.containsKey(context);
    }

    public Collection<WebhookConnectorProperties> getWebhookConnectorByContextPath(String context) {
        return registeredWebhookConnectorsByContextPath.get(context);
    }

    public void registerOtherInboundConnector(InboundConnectorProperties properties) {
        //registeredInboundConnectors.add(properties);
        // Now all known connectors on the classpath need to be known
        // Somehow the type of the connector must resolve to either a
        //PollingInboundConnectorFunction function1 = null;
        //SubscriptionInboundConnector function2 = null;
        // Then this runtime will either start a Subscription or some polling component
        // TODO: Will be addded at a later state
    }
}
