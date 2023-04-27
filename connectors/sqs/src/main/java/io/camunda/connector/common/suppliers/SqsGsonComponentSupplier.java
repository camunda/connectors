/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class SqsGsonComponentSupplier {

  private static final Gson GSON = new GsonBuilder().create();

  private SqsGsonComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }
}
