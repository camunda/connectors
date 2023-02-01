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
import com.google.gson.JsonObject;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.constants.Constants;
import java.io.IOException;

public final class ResponseParser {

  private static final Gson gson = GsonComponentSupplier.gsonInstance();

  private ResponseParser() {}

  public static String extractOAuthAccessToken(HttpResponse oauthResponse) throws IOException {
    JsonObject jsonObject = getAsJsonElement(oauthResponse).getAsJsonObject();
    if (jsonObject.get(Constants.ACCESS_TOKEN) != null) {
      return jsonObject.get(Constants.ACCESS_TOKEN).getAsString();
    }
    return null;
  }

  private static JsonElement getAsJsonElement(final HttpResponse httpResponse) throws IOException {
    String responseStr = httpResponse.parseAsString();
    if (responseStr == null || responseStr.isBlank()) {
      return new JsonObject();
    } else {
      return gson.fromJson(responseStr, JsonElement.class);
    }
  }
}
