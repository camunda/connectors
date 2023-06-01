/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.Input;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class GetSpreadsheetDetails implements Input {

  @NotBlank @Secret private String spreadsheetId;
  private String type;

  public GetSpreadsheetDetails(String spreadsheetId, String type) {
    this.spreadsheetId = spreadsheetId;
    this.type = type;
  }

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GetSpreadsheetDetails that = (GetSpreadsheetDetails) o;
    return Objects.equals(spreadsheetId, that.spreadsheetId) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, type);
  }

  @Override
  public String toString() {
    return "GetSpreadsheetDetails{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
