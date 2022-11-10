/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Objects;

public class TopicRequestData {

  @NotBlank @Secret private String topicArn;
  @NotBlank @Secret private String region;

  @Size(max = 99)
  private String subject;

  // we don't need to know the customer message as we will pass it as-is
  @NotNull private Object message;

  private Map<String, MessageAttributeValue> messageAttributes;

  public String getTopicArn() {
    return topicArn;
  }

  public void setTopicArn(String topicArn) {
    this.topicArn = topicArn;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public Object getMessage() {
    return message;
  }

  public void setMessage(Object message) {
    this.message = message;
  }

  public Map<String, MessageAttributeValue> getMessageAttributes() {
    return messageAttributes;
  }

  public void setMessageAttributes(Map<String, MessageAttributeValue> messageAttributes) {
    this.messageAttributes = messageAttributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TopicRequestData that = (TopicRequestData) o;
    return topicArn.equals(that.topicArn)
        && region.equals(that.region)
        && Objects.equals(subject, that.subject)
        && message.equals(that.message)
        && Objects.equals(messageAttributes, that.messageAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topicArn, region, subject, message, messageAttributes);
  }

  @Override
  public String toString() {
    return "TopicRequestData{"
        + "topicArn='"
        + topicArn
        + '\''
        + ", region='"
        + region
        + '\''
        + ", subject='"
        + subject
        + '\''
        + ", message="
        + message
        + ", messageAttributes="
        + messageAttributes
        + '}';
  }
}
