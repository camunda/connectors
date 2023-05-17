/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.google.supplier;

import com.google.api.client.json.gson.GsonFactory;

public final class GsonComponentSupplier {

  private static final GsonFactory GSON_FACTORY = GsonFactory.getDefaultInstance();

  private GsonComponentSupplier() {}

  public static GsonFactory gsonFactoryInstance() {
    return GSON_FACTORY;
  }
}
