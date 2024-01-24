/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

public interface OperationTypes {
  String CREATE_TABLE = "createTable";
  String DELETE_TABLE = "deleteTable";
  String DESCRIBE_TABLE = "describeTable";
  String SCAN_TABLE = "scanTable";
  String ADD_ITEM = "addItem";
  String DELETE_ITEM = "deleteItem";
  String GET_ITEM = "getItem";
  String UPDATE_ITEM = "updateItem";
}
