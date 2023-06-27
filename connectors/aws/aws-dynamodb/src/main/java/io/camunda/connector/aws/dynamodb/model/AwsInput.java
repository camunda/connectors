/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.dynamodb.model.item.AddItem;
import io.camunda.connector.aws.dynamodb.model.item.DeleteItem;
import io.camunda.connector.aws.dynamodb.model.item.GetItem;
import io.camunda.connector.aws.dynamodb.model.item.UpdateItem;
import io.camunda.connector.aws.dynamodb.model.table.CreateTable;
import io.camunda.connector.aws.dynamodb.model.table.DeleteTable;
import io.camunda.connector.aws.dynamodb.model.table.DescribeTable;
import io.camunda.connector.aws.dynamodb.model.table.ScanTable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  // channel
  @JsonSubTypes.Type(value = CreateTable.class, name = "createTable"),
  @JsonSubTypes.Type(value = DeleteTable.class, name = "deleteTable"),
  @JsonSubTypes.Type(value = DescribeTable.class, name = "describeTable"),
  @JsonSubTypes.Type(value = ScanTable.class, name = "scanTable"),
  @JsonSubTypes.Type(value = AddItem.class, name = "addItem"),
  @JsonSubTypes.Type(value = DeleteItem.class, name = "deleteItem"),
  @JsonSubTypes.Type(value = GetItem.class, name = "getItem"),
  @JsonSubTypes.Type(value = UpdateItem.class, name = "updateItem")
})
public interface AwsInput {

  String getType();

  void setType(final String type);
}
