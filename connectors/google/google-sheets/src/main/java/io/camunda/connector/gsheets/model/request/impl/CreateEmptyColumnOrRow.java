/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.Dimension;
import io.camunda.connector.gsheets.model.request.Input;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CreateEmptyColumnOrRow extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotNull private Integer worksheetId;
  @NotNull private Dimension dimension;
  private Integer startIndex;
  private Integer endIndex;

  public CreateEmptyColumnOrRow() {}

  public CreateEmptyColumnOrRow(
      String spreadsheetId,
      Integer worksheetId,
      Dimension dimension,
      Integer startIndex,
      Integer endIndex,
      String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetId = worksheetId;
    this.dimension = dimension;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
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

  public Dimension getDimension() {
    return dimension;
  }

  public void setDimension(Dimension dimension) {
    this.dimension = dimension;
  }

  public Integer getStartIndex() {
    return startIndex;
  }

  public void setStartIndex(Integer startIndex) {
    this.startIndex = startIndex;
  }

  public Integer getEndIndex() {
    return endIndex;
  }

  public void setEndIndex(Integer endIndex) {
    this.endIndex = endIndex;
  }
}
