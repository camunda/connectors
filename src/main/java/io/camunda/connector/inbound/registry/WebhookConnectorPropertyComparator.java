package io.camunda.connector.inbound.registry;

import io.camunda.connector.inbound.webhook.WebhookConnectorProperties;

import java.util.Comparator;

public class WebhookConnectorPropertyComparator implements Comparator<WebhookConnectorProperties> {

    @Override
    public int compare(WebhookConnectorProperties o1, WebhookConnectorProperties o2) {
        if (o1==null || o1.getBpmnProcessId()==null) {
            return 1;
        }
        if (o2==null || o2.getBpmnProcessId()==null) {
            return -1;
        }
        if (!o1.getBpmnProcessId().equals(o2.getBpmnProcessId())) {
            return o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
        }
        return Integer.compare(o1.getVersion(), o2.getVersion());
    }

}
