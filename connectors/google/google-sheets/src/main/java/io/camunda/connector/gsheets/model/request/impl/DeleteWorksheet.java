/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.SpreadsheetInput;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class DeleteWorksheet extends SpreadsheetInput {

  @NotNull private Integer worksheetId;

  public DeleteWorksheet() {}

  public DeleteWorksheet(String spreadsheetId, Integer worksheetId) {
    super(spreadsheetId);
    this.worksheetId = worksheetId;
  }

  public Integer getWorksheetId() {
    return worksheetId;
  }

  public void setWorksheetId(Integer worksheetId) {
    this.worksheetId = worksheetId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DeleteWorksheet that = (DeleteWorksheet) o;
    return Objects.equals(worksheetId, that.worksheetId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetId);
  }

  @Override
  public String toString() {
    return "DeleteWorksheet{" + "worksheetId=" + worksheetId + "} " + super.toString();
  }
}
