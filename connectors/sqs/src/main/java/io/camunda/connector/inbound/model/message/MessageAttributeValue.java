/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model.message;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

public class MessageAttributeValue {
  private String stringValue;
  private ByteBuffer binaryValue;
  private List<String> stringListValues;
  private List<ByteBuffer> binaryListValues;
  private String dataType;

  public String getStringValue() {
    return stringValue;
  }

  public void setStringValue(final String stringValue) {
    this.stringValue = stringValue;
  }

  public ByteBuffer getBinaryValue() {
    return binaryValue;
  }

  public void setBinaryValue(final ByteBuffer binaryValue) {
    this.binaryValue = binaryValue;
  }

  public List<String> getStringListValues() {
    return stringListValues;
  }

  public void setStringListValues(final List<String> stringListValues) {
    this.stringListValues = stringListValues;
  }

  public List<ByteBuffer> getBinaryListValues() {
    return binaryListValues;
  }

  public void setBinaryListValues(final List<ByteBuffer> binaryListValues) {
    this.binaryListValues = binaryListValues;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(final String dataType) {
    this.dataType = dataType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MessageAttributeValue that = (MessageAttributeValue) o;
    return Objects.equals(stringValue, that.stringValue)
        && Objects.equals(binaryValue, that.binaryValue)
        && Objects.equals(stringListValues, that.stringListValues)
        && Objects.equals(binaryListValues, that.binaryListValues)
        && Objects.equals(dataType, that.dataType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stringValue, binaryValue, stringListValues, binaryListValues, dataType);
  }

  @Override
  public String toString() {
    return "MessageAttributeValue{"
        + "stringValue='"
        + stringValue
        + "'"
        + ", binaryValue="
        + binaryValue
        + ", stringListValues="
        + stringListValues
        + ", binaryListValues="
        + binaryListValues
        + ", dataType='"
        + dataType
        + "'"
        + "}";
  }
}
