package io.camunda.connector.kafkainbound;

import io.camunda.connector.api.annotation.Secret;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class KafkaConnectorProperties {
    @Secret
    @NotNull
    private String sender;

    @Max(10)
    @Min(1)
    private int messagesPerMinute; // how often should mock subscription will produce messages

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public int getMessagesPerMinute() {
        return messagesPerMinute;
    }

    public void setMessagesPerMinute(int messagesPerMinute) {
        this.messagesPerMinute = messagesPerMinute;
    }

    @Override
    public String toString() {
        return "MyConnectorProperties{" +
                "messageSender='" + sender + '\'' +
                ", messagesPerMinute=" + messagesPerMinute +
                '}';
    }
}
