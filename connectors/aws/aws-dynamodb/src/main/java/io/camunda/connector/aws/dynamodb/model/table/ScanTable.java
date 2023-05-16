/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model.table;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class ScanTable implements AwsInput {
  @NotBlank @Secret private String tableName;
  @Secret private String filterExpression;
  @Secret private String projectionExpression;
  @Secret private Map<String, String> expressionAttributeNames;
  @Secret private Map<String, Object> expressionAttributeValues;
  private transient String type;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }

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
  public String getType() {
    return type;
  }

  @Override
  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ScanTable scanTable = (ScanTable) o;
    return Objects.equals(tableName, scanTable.tableName)
        && Objects.equals(filterExpression, scanTable.filterExpression)
        && Objects.equals(projectionExpression, scanTable.projectionExpression)
        && Objects.equals(expressionAttributeNames, scanTable.expressionAttributeNames)
        && Objects.equals(expressionAttributeValues, scanTable.expressionAttributeValues)
        && Objects.equals(type, scanTable.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        tableName,
        filterExpression,
        projectionExpression,
        expressionAttributeNames,
        expressionAttributeValues,
        type);
  }

  @Override
  public String toString() {
    return "ScanTable{"
        + "tableName='"
        + tableName
        + "'"
        + ", filterExpression='"
        + filterExpression
        + "'"
        + ", projectionExpression='"
        + projectionExpression
        + "'"
        + ", expressionAttributeNames="
        + expressionAttributeNames
        + ", expressionAttributeValues="
        + expressionAttributeValues
        + ", type='"
        + type
        + "'"
        + "}";
  }
}
