/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.Dimension;
import io.camunda.connector.gsheets.model.request.SpreadsheetInput;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class CreateEmptyColumnOrRow extends SpreadsheetInput {

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
    super(spreadsheetId);
    this.worksheetId = worksheetId;
    this.dimension = dimension;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateEmptyColumnOrRow that = (CreateEmptyColumnOrRow) o;
    return Objects.equals(worksheetId, that.worksheetId)
        && dimension == that.dimension
        && Objects.equals(startIndex, that.startIndex)
        && Objects.equals(endIndex, that.endIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetId, dimension, startIndex, endIndex);
  }

  @Override
  public String toString() {
    return "CreateEmptyColumnOrRow{"
        + "worksheetId="
        + worksheetId
        + ", dimension="
        + dimension
        + ", startIndex="
        + startIndex
        + ", endIndex="
        + endIndex
        + "} "
        + super.toString();
  }
}
