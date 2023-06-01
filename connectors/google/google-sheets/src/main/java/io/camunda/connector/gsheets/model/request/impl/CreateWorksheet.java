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

public class CreateWorksheet implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotBlank @Secret private String worksheetName;
  private Integer worksheetIndex;
  private String type;

  public CreateWorksheet(
      String spreadsheetId, String worksheetName, Integer worksheetIndex, String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.worksheetIndex = worksheetIndex;
    this.type = type;
  }

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public String getWorksheetName() {
    return worksheetName;
  }

  public void setWorksheetName(String worksheetName) {
    this.worksheetName = worksheetName;
  }

  public Integer getWorksheetIndex() {
    return worksheetIndex;
  }

  public void setWorksheetIndex(Integer worksheetIndex) {
    this.worksheetIndex = worksheetIndex;
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
    CreateWorksheet that = (CreateWorksheet) o;
    return Objects.equals(spreadsheetId, that.spreadsheetId)
        && Objects.equals(worksheetName, that.worksheetName)
        && Objects.equals(worksheetIndex, that.worksheetIndex)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetName, worksheetIndex, type);
  }

  @Override
  public String toString() {
    return "CreateWorksheet{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetName='"
        + worksheetName
        + '\''
        + ", worksheetIndex="
        + worksheetIndex
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
