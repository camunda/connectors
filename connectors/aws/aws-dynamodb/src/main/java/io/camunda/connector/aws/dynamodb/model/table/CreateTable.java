/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model.table;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class CreateTable implements AwsInput {

  @NotBlank @Secret private String tableName;
  @NotBlank @Secret private String partitionKey;
  @NotBlank private String partitionKeyRole;
  @NotBlank private String partitionKeyType;
  @Secret private String sortKey;
  private String sortKeyRole;
  private String sortKeyType;
  private Long readCapacityUnits;
  private Long writeCapacityUnits;
  private String billingModeStr;
  private boolean deletionProtection;
  private transient String type;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }

  public String getPartitionKey() {
    return partitionKey;
  }

  public void setPartitionKey(final String partitionKey) {
    this.partitionKey = partitionKey;
  }

  public String getPartitionKeyRole() {
    return partitionKeyRole;
  }

  public void setPartitionKeyRole(final String partitionKeyRole) {
    this.partitionKeyRole = partitionKeyRole;
  }

  public String getPartitionKeyType() {
    return partitionKeyType;
  }

  public void setPartitionKeyType(final String partitionKeyType) {
    this.partitionKeyType = partitionKeyType;
  }

  public String getSortKey() {
    return sortKey;
  }

  public void setSortKey(final String sortKey) {
    this.sortKey = sortKey;
  }

  public String getSortKeyRole() {
    return sortKeyRole;
  }

  public void setSortKeyRole(final String sortKeyRole) {
    this.sortKeyRole = sortKeyRole;
  }

  public String getSortKeyType() {
    return sortKeyType;
  }

  public void setSortKeyType(final String sortKeyType) {
    this.sortKeyType = sortKeyType;
  }

  public Long getReadCapacityUnits() {
    return readCapacityUnits;
  }

  public void setReadCapacityUnits(final Long readCapacityUnits) {
    this.readCapacityUnits = readCapacityUnits;
  }

  public Long getWriteCapacityUnits() {
    return writeCapacityUnits;
  }

  public void setWriteCapacityUnits(final Long writeCapacityUnits) {
    this.writeCapacityUnits = writeCapacityUnits;
  }

  public String getBillingModeStr() {
    return billingModeStr;
  }

  public void setBillingModeStr(final String billingModeStr) {
    this.billingModeStr = billingModeStr;
  }

  public boolean isDeletionProtection() {
    return deletionProtection;
  }

  public void setDeletionProtection(final boolean deletionProtection) {
    this.deletionProtection = deletionProtection;
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
    final CreateTable that = (CreateTable) o;
    return deletionProtection == that.deletionProtection
        && Objects.equals(tableName, that.tableName)
        && Objects.equals(partitionKey, that.partitionKey)
        && Objects.equals(partitionKeyRole, that.partitionKeyRole)
        && Objects.equals(partitionKeyType, that.partitionKeyType)
        && Objects.equals(sortKey, that.sortKey)
        && Objects.equals(sortKeyRole, that.sortKeyRole)
        && Objects.equals(sortKeyType, that.sortKeyType)
        && Objects.equals(readCapacityUnits, that.readCapacityUnits)
        && Objects.equals(writeCapacityUnits, that.writeCapacityUnits)
        && Objects.equals(billingModeStr, that.billingModeStr)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        tableName,
        partitionKey,
        partitionKeyRole,
        partitionKeyType,
        sortKey,
        sortKeyRole,
        sortKeyType,
        readCapacityUnits,
        writeCapacityUnits,
        billingModeStr,
        deletionProtection,
        type);
  }

  @Override
  public String toString() {
    return "CreateTable{"
        + "tableName='"
        + tableName
        + "'"
        + ", partitionKey='"
        + partitionKey
        + "'"
        + ", partitionKeyRole='"
        + partitionKeyRole
        + "'"
        + ", partitionKeyType='"
        + partitionKeyType
        + "'"
        + ", sortKey='"
        + sortKey
        + "'"
        + ", sortKeyRole='"
        + sortKeyRole
        + "'"
        + ", sortKeyType='"
        + sortKeyType
        + "'"
        + ", readCapacityUnits="
        + readCapacityUnits
        + ", writeCapacityUnits="
        + writeCapacityUnits
        + ", billingModeStr='"
        + billingModeStr
        + "'"
        + ", deletionProtection="
        + deletionProtection
        + ", type='"
        + type
        + "'"
        + "}";
  }
}
