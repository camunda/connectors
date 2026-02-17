/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound.model;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.util.StringUtils;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class TopicRequestData {

  @TemplateProperty(
      group = "configuration",
      label = "Topic ARN",
      description = "Specify the topic you want to publish to")
  @NotBlank
  private String topicArn;

  @TemplateProperty(ignore = true)
  @Deprecated
  private String region;

  @TemplateProperty(
      label = "Topic type",
      group = "configuration",
      type = TemplateProperty.PropertyType.Dropdown,
      defaultValue = "standard",
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "standard", label = "Standard"),
        @TemplateProperty.DropdownPropertyChoice(value = "fifo", label = "FIFO")
      },
      description =
          "Select SNS topic type. Details at <a href=\"https://aws.amazon.com/sns/features/\">AWS SNS Features</a>")
  @NotNull
  private TopicType type = TopicType.standard;

  @TemplateProperty(
      group = "input",
      label = "Message group ID",
      condition = @TemplateProperty.PropertyCondition(property = "topic.type", equals = "fifo"),
      description =
          "Message group ID (FIFO only). See also <a href=\"https://docs.aws.amazon.com/sns/latest/dg/fifo-message-grouping.html\">message grouping for FIFO topics</a> in the Amazon SNS developer guide")
  private String messageGroupId;

  @TemplateProperty(
      group = "input",
      label = "Message deduplication ID",
      condition = @TemplateProperty.PropertyCondition(property = "topic.type", equals = "fifo"),
      optional = true,
      description =
          "Message deduplication ID. See also <a href=\"https://docs.aws.amazon.com/sns/latest/dg/fifo-message-dedup.html\">Message deduplication for FIFO topics</a> in the Amazon SNS developer guide")
  private String messageDeduplicationId;

  @TemplateProperty(
      group = "input",
      label = "Subject",
      optional = true,
      description = "Specify the subject of the message you want to publish in the SNS topic")
  private String subject;

  // we don't need to know the customer message as we will pass it as-is
  @TemplateProperty(
      group = "input",
      label = "Message",
      type = TemplateProperty.PropertyType.Text,
      description = "Data to publish in the SNS topic")
  @NotNull
  private Object message;

  @FEEL
  @TemplateProperty(
      group = "input",
      feel = FeelMode.required,
      label = "messageAttributes",
      optional = true,
      description = "Message attributes metadata")
  private Map<String, SnsMessageAttribute> messageAttributes;

  @AssertTrue
  public boolean hasValidTopicProperties() {
    if (TopicType.standard == type) {
      return StringUtils.isNullOrEmpty(messageGroupId);
    } else if (TopicType.fifo == type) {
      return StringUtils.hasValue(messageGroupId);
    } else throw new IllegalArgumentException("No valid type value " + type);
  }

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

  public TopicType getType() {
    return type;
  }

  public void setType(final TopicType type) {
    this.type = type;
  }

  public String getMessageGroupId() {
    return messageGroupId;
  }

  public void setMessageGroupId(final String messageGroupId) {
    this.messageGroupId = messageGroupId;
  }

  public String getMessageDeduplicationId() {
    return messageDeduplicationId;
  }

  public void setMessageDeduplicationId(final String messageDeduplicationId) {
    this.messageDeduplicationId = messageDeduplicationId;
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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final TopicRequestData that)) {
      return false;
    }
    return Objects.equals(topicArn, that.topicArn)
        && Objects.equals(region, that.region)
        && type == that.type
        && Objects.equals(messageGroupId, that.messageGroupId)
        && Objects.equals(messageDeduplicationId, that.messageDeduplicationId)
        && Objects.equals(subject, that.subject)
        && Objects.equals(message, that.message)
        && Objects.equals(messageAttributes, that.messageAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        topicArn,
        region,
        type,
        messageGroupId,
        messageDeduplicationId,
        subject,
        message,
        messageAttributes);
  }

  @Override
  public String toString() {
    return "TopicRequestData{"
        + "topicArn='"
        + topicArn
        + "'"
        + ", region='"
        + region
        + "'"
        + ", type="
        + type
        + ", messageGroupId='"
        + messageGroupId
        + "'"
        + ", messageDeduplicationId='"
        + messageDeduplicationId
        + "'"
        + ", subject='"
        + subject
        + "'"
        + ", message="
        + message
        + ", messageAttributes="
        + messageAttributes
        + "}";
  }
}
