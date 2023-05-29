/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model.message;

import java.util.Map;
import java.util.Objects;

public class SqsInboundMessage {
  private String messageId;
  private String receiptHandle;
  private String mD5OfBody;
  private Object body;
  private Map<String, String> attributes;
  private String mD5OfMessageAttributes;
  private Map<String, MessageAttributeValue> messageAttributes;

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(final String messageId) {
    this.messageId = messageId;
  }

  public String getReceiptHandle() {
    return receiptHandle;
  }

  public void setReceiptHandle(final String receiptHandle) {
    this.receiptHandle = receiptHandle;
  }

  public String getmD5OfBody() {
    return mD5OfBody;
  }

  public void setmD5OfBody(final String mD5OfBody) {
    this.mD5OfBody = mD5OfBody;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(final Map<String, String> attributes) {
    this.attributes = attributes;
  }

  public String getMD5OfMessageAttributes() {
    return mD5OfMessageAttributes;
  }

  public void setMD5OfMessageAttributes(final String mD5OfMessageAttributes) {
    this.mD5OfMessageAttributes = mD5OfMessageAttributes;
  }

  public Map<String, MessageAttributeValue> getMessageAttributes() {
    return messageAttributes;
  }

  public void setMessageAttributes(final Map<String, MessageAttributeValue> messageAttributes) {
    this.messageAttributes = messageAttributes;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SqsInboundMessage that = (SqsInboundMessage) o;
    return Objects.equals(messageId, that.messageId)
        && Objects.equals(receiptHandle, that.receiptHandle)
        && Objects.equals(mD5OfBody, that.mD5OfBody)
        && Objects.equals(body, that.body)
        && Objects.equals(attributes, that.attributes)
        && Objects.equals(mD5OfMessageAttributes, that.mD5OfMessageAttributes)
        && Objects.equals(messageAttributes, that.messageAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        messageId,
        receiptHandle,
        mD5OfBody,
        body,
        attributes,
        mD5OfMessageAttributes,
        messageAttributes);
  }

  @Override
  public String toString() {
    return "SqsInboundMessage{"
        + "messageId='"
        + messageId
        + "'"
        + ", receiptHandle='"
        + receiptHandle
        + "'"
        + ", mD5OfBody='"
        + mD5OfBody
        + "'"
        + ", body="
        + body
        + ", attributes="
        + attributes
        + ", mD5OfMessageAttributes='"
        + mD5OfMessageAttributes
        + "'"
        + ", messageAttributes="
        + messageAttributes
        + "}";
  }
}
