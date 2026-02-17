/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound.model;

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.util.StringUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class QueueRequestData {

  @TemplateProperty(
      group = "configuration",
      label = "URL",
      description = "Specify the URL of the SQS queue where you would like to send message to")
  @NotEmpty
  private String url;

  @TemplateProperty(ignore = true)
  @Deprecated
  private String region;

  @TemplateProperty(
      label = "Message body",
      group = "input",
      feel = FeelMode.required,
      type = TemplateProperty.PropertyType.Text,
      description = "Data to send to the SQS queue")
  @NotNull
  private Object messageBody;

  @TemplateProperty(
      label = "Queue type",
      group = "configuration",
      type = TemplateProperty.PropertyType.Dropdown,
      defaultValue = "standard",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "standard", label = "Standard"),
        @TemplateProperty.DropdownPropertyChoice(value = "fifo", label = "FIFO")
      },
      description =
          "Specify whether the queue is a <a href=\"https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html\">standard</a> or <a href=\"https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues.html\">FIFO</a> queue")
  @NotNull
  private QueueType type = QueueType.standard;

  @TemplateProperty(
      label = "Message attributes",
      group = "input",
      type = TemplateProperty.PropertyType.Text,
      optional = true,
      feel = FeelMode.required,
      description = "Message attributes metadata")
  private Map<String, SqsMessageAttribute> messageAttributes;

  @TemplateProperty(
      group = "input",
      label = "Message group ID",
      condition = @TemplateProperty.PropertyCondition(property = "queue.type", equals = "fifo"),
      description =
          "Message group ID (FIFO only). See also <a href=\"https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html\">using the MessageGroupId Property</a> in the Amazon SQS developer guide")
  private String messageGroupId;

  @TemplateProperty(
      group = "input",
      label = "Message deduplication ID",
      condition = @TemplateProperty.PropertyCondition(property = "queue.type", equals = "fifo"),
      optional = true,
      description =
          "Message deduplication ID (FIFO only). See also <a href=\"https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagededuplicationid-property.html\">using the MessageDeduplicationId Property</a> in the Amazon SQS developer guide")
  private String messageDeduplicationId;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Deprecated
  public String getRegion() {
    return region;
  }

  @Deprecated
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

  public QueueType getType() {
    return type;
  }

  public void setType(QueueType type) {
    this.type = type;
  }

  public String getMessageGroupId() {
    return messageGroupId;
  }

  public void setMessageGroupId(String messageGroupId) {
    this.messageGroupId = messageGroupId;
  }

  public String getMessageDeduplicationId() {
    return messageDeduplicationId;
  }

  public void setMessageDeduplicationId(String messageDeduplicationId) {
    this.messageDeduplicationId = messageDeduplicationId;
  }

  @AssertTrue
  public boolean hasValidQueueProperties() {
    if (QueueType.standard == type) {
      return StringUtils.isNullOrEmpty(messageGroupId);
    } else if (QueueType.fifo == type) {
      return StringUtils.hasValue(messageGroupId);
    } else throw new IllegalArgumentException("No valid type value " + type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QueueRequestData that = (QueueRequestData) o;
    return Objects.equals(url, that.url)
        && Objects.equals(region, that.region)
        && Objects.equals(messageBody, that.messageBody)
        && Objects.equals(type, that.type)
        && Objects.equals(messageAttributes, that.messageAttributes)
        && Objects.equals(messageGroupId, that.messageGroupId)
        && Objects.equals(messageDeduplicationId, that.messageDeduplicationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        url, region, messageBody, type, messageAttributes, messageGroupId, messageDeduplicationId);
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
        + ", type="
        + type
        + ", messageAttributes="
        + messageAttributes
        + ", messageGroupId='"
        + messageGroupId
        + '\''
        + ", messageDeduplicationId='"
        + messageDeduplicationId
        + '\''
        + '}';
  }
}
