/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import io.camunda.connector.aws.dynamodb.model.item.AddItem;
import io.camunda.connector.aws.dynamodb.model.item.DeleteItem;
import io.camunda.connector.aws.dynamodb.model.item.GetItem;
import io.camunda.connector.aws.dynamodb.model.item.UpdateItem;
import io.camunda.connector.aws.dynamodb.model.table.CreateTable;
import io.camunda.connector.aws.dynamodb.model.table.DeleteTable;
import io.camunda.connector.aws.dynamodb.model.table.DescribeTable;
import io.camunda.connector.aws.dynamodb.model.table.ScanTable;

public class GsonDynamoDbComponentSupplier {

  private static final Gson GSON =
      new GsonBuilder()
          .serializeNulls()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(AwsInput.class, "type")
                  .registerSubtype(CreateTable.class, "createTable")
                  .registerSubtype(DeleteTable.class, "deleteTable")
                  .registerSubtype(DescribeTable.class, "describeTable")
                  .registerSubtype(ScanTable.class, "scanTable")
                  .registerSubtype(AddItem.class, "addItem")
                  .registerSubtype(DeleteItem.class, "deleteItem")
                  .registerSubtype(GetItem.class, "getItem")
                  .registerSubtype(UpdateItem.class, "updateItem"))
          .create();

  private GsonDynamoDbComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }
}
