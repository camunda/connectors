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
import javax.validation.constraints.NotNull;

public class DeleteWorksheet implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotNull private Integer worksheetId;
  private String type;

  public DeleteWorksheet(String spreadsheetId, Integer worksheetId, String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetId = worksheetId;
    this.type = type;
  }

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public Integer getWorksheetId() {
    return worksheetId;
  }

  public void setWorksheetId(Integer worksheetId) {
    this.worksheetId = worksheetId;
  }

  public String getType() {
    return type;
  }

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
    DeleteWorksheet that = (DeleteWorksheet) o;
    return Objects.equals(spreadsheetId, that.spreadsheetId)
        && Objects.equals(worksheetId, that.worksheetId)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetId, type);
  }

  @Override
  public String toString() {
    return "DeleteWorksheet{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetId="
        + worksheetId
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
