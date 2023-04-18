/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import io.camunda.connector.aws.dynamodb.AwsDynamoDbService;
import io.camunda.connector.aws.model.AwsService;

public final class GsonComponentSupplier {

  private static final Gson GSON =
      new GsonBuilder()
          .serializeNulls()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(AwsService.class, "type")
                  .registerSubtype(AwsDynamoDbService.class, "dynamoDb"))
          .create();

  private GsonComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }
}
