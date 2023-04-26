/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import com.google.gson.JsonElement;
import com.rabbitmq.client.AMQP;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public final class ValidationPropertiesUtil {

  private ValidationPropertiesUtil() {}

  // return the input object without changing, only validation
  public static JsonElement validateAmqpBasicPropertiesOrThrowException(JsonElement jsonElement) {
    Set<Map.Entry<String, JsonElement>> entries = jsonElement.getAsJsonObject().entrySet();
    for (Map.Entry<String, JsonElement> entry : entries) {
      boolean fieldExist =
          Arrays.stream(AMQP.BasicProperties.class.getDeclaredFields())
              .anyMatch(f -> f.getName().equals(entry.getKey()));
      if (!fieldExist) {
        throw new IllegalArgumentException(
            "Unsupported field [" + entry.getKey() + "] for properties");
      }
    }
    return jsonElement;
  }
}
