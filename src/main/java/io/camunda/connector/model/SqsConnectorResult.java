package io.camunda.connector.model;

public class SqsConnectorResult {

  private String messageId;

  public SqsConnectorResult() {}

  public SqsConnectorResult(String messageId) {
    this.messageId = messageId;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }
}
