/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GsonSupplier {

  private final Gson gson;

  public GsonSupplier() {
    gson = new GsonBuilder().create();
  }

  public Gson gson() {
    return gson;
  }
}
