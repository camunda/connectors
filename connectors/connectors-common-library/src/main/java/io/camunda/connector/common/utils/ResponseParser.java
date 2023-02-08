/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ResponseParser {

  public static Map<String, String> extractPropertiesFromBody(
      final Map<String, String> requestedProperties, final String strResponse, final Gson gson) {
    if (requestedProperties == null || requestedProperties.isEmpty()) {
      return null;
    }

    final JsonElement asJsonElement =
        Optional.ofNullable(JsonHelper.getAsJsonElement(strResponse, gson))
            .orElseThrow(
                () -> new IllegalArgumentException("Authentication response body is empty"));

    final Map<String, String> extractedProperties = new HashMap<>();

    requestedProperties.forEach(
        (k, v) -> {
          JsonElement responseCopy = asJsonElement.deepCopy();
          String[] fromRequests = v.trim().split("\\.");
          String result;

          for (final String fromRequest : fromRequests) {
            final String[] split = fromRequest.split(":");
            final String actionString = split[0].trim();
            if (actionString.equalsIgnoreCase("asObject")) {
              responseCopy =
                  Optional.ofNullable(responseCopy.getAsJsonObject())
                      .map(obj -> obj.get(split[1].trim()))
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  "Failed to getting ["
                                      + split[1].trim()
                                      + "] from authentication response"));
            } else if (actionString.equalsIgnoreCase("asArray")) {
              responseCopy =
                  Optional.ofNullable(responseCopy.getAsJsonArray())
                      .map(s -> s.get(Integer.parseInt(split[1].trim())))
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  "Failed to get element # ["
                                      + split[1].trim()
                                      + "] from authentication response"));
            } else if (actionString.equalsIgnoreCase("asString")) {
              result = responseCopy.getAsString();
              if (result == null) {
                throw new RuntimeException(
                    "Fail to parse to String [" + responseCopy + "] from authentication response");
              }
              extractedProperties.put(k, result);
            } else {
              throw new IllegalArgumentException(
                  "Unsupported action [" + actionString + "] in parsing pattern");
            }
          }
        });
    return extractedProperties.isEmpty() ? null : extractedProperties;
  }
}
