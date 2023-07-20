/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public final class CreateTable extends TableOperation {

  @NotBlank private String partitionKey;
  @NotBlank private String partitionKeyRole;
  @NotBlank private String partitionKeyType;
  private String sortKey;
  private String sortKeyRole;
  private String sortKeyType;
  private Long readCapacityUnits;
  private Long writeCapacityUnits;
  private String billingModeStr;
  private boolean deletionProtection;

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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateTable that = (CreateTable) o;
    return deletionProtection == that.deletionProtection
        && Objects.equals(partitionKey, that.partitionKey)
        && Objects.equals(partitionKeyRole, that.partitionKeyRole)
        && Objects.equals(partitionKeyType, that.partitionKeyType)
        && Objects.equals(sortKey, that.sortKey)
        && Objects.equals(sortKeyRole, that.sortKeyRole)
        && Objects.equals(sortKeyType, that.sortKeyType)
        && Objects.equals(readCapacityUnits, that.readCapacityUnits)
        && Objects.equals(writeCapacityUnits, that.writeCapacityUnits)
        && Objects.equals(billingModeStr, that.billingModeStr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        partitionKey,
        partitionKeyRole,
        partitionKeyType,
        sortKey,
        sortKeyRole,
        sortKeyType,
        readCapacityUnits,
        writeCapacityUnits,
        billingModeStr,
        deletionProtection);
  }

  @Override
  public String toString() {
    return "CreateTable{"
        + "partitionKey='"
        + partitionKey
        + '\''
        + ", partitionKeyRole='"
        + partitionKeyRole
        + '\''
        + ", partitionKeyType='"
        + partitionKeyType
        + '\''
        + ", sortKey='"
        + sortKey
        + '\''
        + ", sortKeyRole='"
        + sortKeyRole
        + '\''
        + ", sortKeyType='"
        + sortKeyType
        + '\''
        + ", readCapacityUnits="
        + readCapacityUnits
        + ", writeCapacityUnits="
        + writeCapacityUnits
        + ", billingModeStr='"
        + billingModeStr
        + '\''
        + ", deletionProtection="
        + deletionProtection
        + "} "
        + super.toString();
  }
}
