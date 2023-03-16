package io.camunda.connector.kafkainbound;

import java.util.Objects;

public class KafkaConnectorEvent {
    private KafkaSubscriptionEvent event;

    public KafkaConnectorEvent(KafkaSubscriptionEvent  event) {
        this.event = event;
    }

    public KafkaSubscriptionEvent getEvent() {
        return event;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KafkaConnectorEvent that = (KafkaConnectorEvent) o;
        return Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event);
    }

    @Override
    public String toString() {
        return "MyConnectorEvent{" +
                "event=" + event +
                '}';
    }
}
