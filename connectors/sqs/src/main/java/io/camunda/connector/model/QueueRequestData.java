/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import io.camunda.connector.api.annotation.Secret;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class QueueRequestData {

  @NotEmpty @Secret private String url;
  @NotEmpty @Secret private String region;

  @NotNull
  private Object messageBody; // we don't need to know the customer message as we will pass it as-is

  private Map<String, SqsMessageAttribute> messageAttributes;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public Object getMessageBody() {
    return messageBody;
  }

  public void setMessageBody(Object messageBody) {
    this.messageBody = messageBody;
  }

  public Map<String, SqsMessageAttribute> getMessageAttributes() {
    return messageAttributes;
  }

  public Map<String, MessageAttributeValue> getAwsSqsNativeMessageAttributes() {
    if (messageAttributes == null) {
      return Collections.emptyMap();
    }

    final Map<String, MessageAttributeValue> sqsNativeMessageAttributes = new HashMap<>();
    messageAttributes.forEach(
        (key, value) ->
            sqsNativeMessageAttributes.put(key, messageAttributeTransformer().apply(value)));

    return sqsNativeMessageAttributes;
  }

  public void setMessageAttributes(Map<String, SqsMessageAttribute> messageAttributes) {
    this.messageAttributes = messageAttributes;
  }

  private Function<SqsMessageAttribute, MessageAttributeValue> messageAttributeTransformer() {
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
    QueueRequestData that = (QueueRequestData) o;
    return url.equals(that.url)
        && region.equals(that.region)
        && messageBody.equals(that.messageBody)
        && Objects.equals(messageAttributes, that.messageAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, region, messageBody, messageAttributes);
  }

  @Override
  public String toString() {
    return "QueueRequestData{"
        + "url='"
        + url
        + '\''
        + ", region='"
        + region
        + '\''
        + ", messageBody="
        + messageBody
        + ", messageAttributes="
        + messageAttributes
        + '}';
  }
}
