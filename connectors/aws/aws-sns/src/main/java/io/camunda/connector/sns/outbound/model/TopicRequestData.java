/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound.model;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class TopicRequestData {

  @NotBlank @Secret private String topicArn;
  @Deprecated @Secret private String region;

  @Size(max = 99)
  private String subject;

  // we don't need to know the customer message as we will pass it as-is
  @NotNull private Object message;

  private Map<String, SnsMessageAttribute> messageAttributes;

  public String getTopicArn() {
    return topicArn;
  }

  public void setTopicArn(String topicArn) {
    this.topicArn = topicArn;
  }

  @Deprecated
  public String getRegion() {
    return region;
  }

  @Deprecated
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

  public Map<String, SnsMessageAttribute> getMessageAttributes() {
    return messageAttributes;
  }

  public Map<String, MessageAttributeValue> getAwsSnsNativeMessageAttributes() {
    if (messageAttributes == null) {
      return Collections.emptyMap();
    }

    final Map<String, MessageAttributeValue> snsNativeMessageAttributes = new HashMap<>();
    messageAttributes.forEach(
        (key, value) ->
            snsNativeMessageAttributes.put(key, messageAttributeTransformer().apply(value)));

    return snsNativeMessageAttributes;
  }

  public void setMessageAttributes(Map<String, SnsMessageAttribute> messageAttributes) {
    this.messageAttributes = messageAttributes;
  }

  private Function<SnsMessageAttribute, MessageAttributeValue> messageAttributeTransformer() {
    return snsMessageAttribute -> {
      MessageAttributeValue msgAttr = new MessageAttributeValue();
      msgAttr.setDataType(snsMessageAttribute.getDataType());
      msgAttr.setStringValue(snsMessageAttribute.getStringValue());
      return msgAttr;
    };
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
