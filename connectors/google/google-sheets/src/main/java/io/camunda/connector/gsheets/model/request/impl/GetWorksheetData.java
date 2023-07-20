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

public class GetWorksheetData extends SpreadsheetInput {

  @NotBlank private String worksheetName;

  public GetWorksheetData() {}

  public GetWorksheetData(String spreadsheetId, String worksheetName) {
    super(spreadsheetId);
    this.worksheetName = worksheetName;
  }

  public String getWorksheetName() {
    return worksheetName;
  }

  public void setWorksheetName(String worksheetName) {
    this.worksheetName = worksheetName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GetWorksheetData that = (GetWorksheetData) o;
    return Objects.equals(worksheetName, that.worksheetName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetName);
  }

  @Override
  public String toString() {
    return "GetWorksheetData{" + "worksheetName='" + worksheetName + '\'' + "} " + super.toString();
  }
}
