/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class SnsMessageAttribute {
  @JsonProperty("dataType")
  @JsonAlias("DataType")
  @NotBlank
  private String dataType;

  @JsonProperty("stringValue")
  @JsonAlias("StringValue")
  @NotBlank
  private String stringValue;

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getStringValue() {
    return stringValue;
  }

  public void setStringValue(String stringValue) {
    this.stringValue = stringValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SnsMessageAttribute that = (SnsMessageAttribute) o;
    return dataType.equals(that.dataType) && stringValue.equals(that.stringValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataType, stringValue);
  }

  @Override
  public String toString() {
    return "SNSMessageAttribute{"
        + "dataType='"
        + dataType
        + '\''
        + ", stringValue='"
        + stringValue
        + '\''
        + '}';
  }
}
