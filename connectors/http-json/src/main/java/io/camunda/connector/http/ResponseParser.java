/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http;

import com.google.api.client.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.constants.Constants;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ResponseParser {

  private static final Gson gson = GsonComponentSupplier.gsonInstance();

  private ResponseParser() {}

  public static String extractOAuthAccessToken(HttpResponse oauthResponse) throws IOException {
    return Optional.ofNullable(getAsJsonElement(oauthResponse.parseAsString()))
        .map(JsonElement::getAsJsonObject)
        .map(jsonObject -> jsonObject.get(Constants.ACCESS_TOKEN))
        .map(JsonElement::getAsString)
        .orElse(null);
  }

  public static Map<String, String> extractPropertiesFromBody(
      final Map<String, String> requestedProperties, final String strResponse) {
    if (requestedProperties == null || requestedProperties.isEmpty()) {
      return null;
    }

    final JsonElement asJsonElement =
        Optional.ofNullable(getAsJsonElement(strResponse))
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

  private static JsonElement getAsJsonElement(final String strResponse) {
    return Optional.ofNullable(strResponse)
        .filter(response -> !response.isBlank())
        .map(response -> gson.fromJson(response, JsonElement.class))
        .orElse(null);
  }
}
