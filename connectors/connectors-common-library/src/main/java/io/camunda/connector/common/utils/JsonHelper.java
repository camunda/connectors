/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Optional;

public class JsonHelper {

  public static JsonElement getAsJsonElement(final String strResponse, final Gson gson) {
    return Optional.ofNullable(strResponse)
        .filter(response -> !response.isBlank())
        .map(response -> gson.fromJson(response, JsonElement.class))
        .orElse(null);
  }
}
