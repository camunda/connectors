/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.SpreadsheetInput;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class CreateWorksheet extends SpreadsheetInput {

  @NotBlank private String worksheetName;
  private Integer worksheetIndex;

  public CreateWorksheet() {}

  public CreateWorksheet(String spreadsheetId, String worksheetName, Integer worksheetIndex) {
    super(spreadsheetId);
    this.worksheetName = worksheetName;
    this.worksheetIndex = worksheetIndex;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateWorksheet that = (CreateWorksheet) o;
    return Objects.equals(worksheetName, that.worksheetName)
        && Objects.equals(worksheetIndex, that.worksheetIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetName, worksheetIndex);
  }

  @Override
  public String toString() {
    return "CreateWorksheet{"
        + "worksheetName='"
        + worksheetName
        + '\''
        + ", worksheetIndex="
        + worksheetIndex
        + "} "
        + super.toString();
  }
}
