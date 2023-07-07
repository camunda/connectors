/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import java.util.Map;
import java.util.Objects;

public final class ScanTable extends TableOperation {
  private String filterExpression;
  private String projectionExpression;
  private Map<String, String> expressionAttributeNames;
  private Map<String, Object> expressionAttributeValues;

  public String getFilterExpression() {
    return filterExpression;
  }

  public void setFilterExpression(final String filterExpression) {
    this.filterExpression = filterExpression;
  }

  public String getProjectionExpression() {
    return projectionExpression;
  }

  public void setProjectionExpression(final String projectionExpression) {
    this.projectionExpression = projectionExpression;
  }

  public Map<String, String> getExpressionAttributeNames() {
    return expressionAttributeNames;
  }

  public void setExpressionAttributeNames(final Map<String, String> expressionAttributeNames) {
    this.expressionAttributeNames = expressionAttributeNames;
  }

  public Map<String, Object> getExpressionAttributeValues() {
    return expressionAttributeValues;
  }

  public void setExpressionAttributeValues(final Map<String, Object> expressionAttributeValues) {
    this.expressionAttributeValues = expressionAttributeValues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ScanTable scanTable = (ScanTable) o;
    return Objects.equals(filterExpression, scanTable.filterExpression)
        && Objects.equals(projectionExpression, scanTable.projectionExpression)
        && Objects.equals(expressionAttributeNames, scanTable.expressionAttributeNames)
        && Objects.equals(expressionAttributeValues, scanTable.expressionAttributeValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        filterExpression,
        projectionExpression,
        expressionAttributeNames,
        expressionAttributeValues);
  }

  @Override
  public String toString() {
    return "ScanTable{"
        + "filterExpression='"
        + filterExpression
        + '\''
        + ", projectionExpression='"
        + projectionExpression
        + '\''
        + ", expressionAttributeNames="
        + expressionAttributeNames
        + ", expressionAttributeValues="
        + expressionAttributeValues
        + "} "
        + super.toString();
  }
}
