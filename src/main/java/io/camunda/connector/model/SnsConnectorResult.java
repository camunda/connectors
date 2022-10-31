/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import java.util.Objects;

public class SnsConnectorResult {

  private String messageId;

  public SnsConnectorResult() {}

  public SnsConnectorResult(String messageId) {
    this.messageId = messageId;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SnsConnectorResult that = (SnsConnectorResult) o;
    return messageId.equals(that.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(messageId);
  }

  @Override
  public String toString() {
    return "SnsConnectorResult{" + "messageId='" + messageId + '\'' + '}';
  }
}
