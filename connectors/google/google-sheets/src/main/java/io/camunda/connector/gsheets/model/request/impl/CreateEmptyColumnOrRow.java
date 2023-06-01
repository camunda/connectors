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
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CreateEmptyColumnOrRow implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotNull private Integer worksheetId;
  @NotNull private Dimension dimension;
  private Integer startIndex;
  private Integer endIndex;
  private String type;

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
    CreateEmptyColumnOrRow that = (CreateEmptyColumnOrRow) o;
    return Objects.equals(spreadsheetId, that.spreadsheetId)
        && Objects.equals(worksheetId, that.worksheetId)
        && dimension == that.dimension
        && Objects.equals(startIndex, that.startIndex)
        && Objects.equals(endIndex, that.endIndex)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetId, dimension, startIndex, endIndex, type);
  }

  @Override
  public String toString() {
    return "CreateEmptyColumnOrRow{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetId="
        + worksheetId
        + ", dimension="
        + dimension
        + ", startIndex="
        + startIndex
        + ", endIndex="
        + endIndex
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
