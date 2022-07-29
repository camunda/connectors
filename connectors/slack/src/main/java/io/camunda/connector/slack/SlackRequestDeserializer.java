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
package io.camunda.connector.slack;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SlackRequestDeserializer
    implements JsonDeserializer<SlackRequest<? extends SlackRequestData>> {

  private String typeElementName;
  private Gson gson;
  private Map<String, Class<? extends SlackRequestData>> typeRegistry;

  public SlackRequestDeserializer(String typeElementName) {
    this.typeElementName = typeElementName;
    this.gson = new Gson();
    this.typeRegistry = new HashMap<>();
  }

  public SlackRequestDeserializer registerType(
      String typeName, Class<? extends SlackRequestData> requestType) {
    typeRegistry.put(typeName, requestType);
    return this;
  }

  @Override
  public SlackRequest<? extends SlackRequestData> deserialize(
      JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    return getTypeElementValue(jsonElement)
        .map(typeRegistry::get)
        .map(SlackRequestDeserializer::getTypeToken)
        .map(typeToken -> getSlackRequest(jsonElement, typeToken))
        .orElse(null);
  }

  private Optional<String> getTypeElementValue(JsonElement jsonElement) {
    JsonObject asJsonObject = jsonElement.getAsJsonObject();
    JsonElement element = asJsonObject.get(typeElementName);
    return Optional.ofNullable(element).map(JsonElement::getAsString);
  }

  private static TypeToken<SlackRequest<? extends SlackRequestData>> getTypeToken(
      Class<? extends SlackRequestData> requestDataClass) {
    return (TypeToken<SlackRequest<? extends SlackRequestData>>)
        TypeToken.getParameterized(SlackRequest.class, requestDataClass);
  }

  private SlackRequest<? extends SlackRequestData> getSlackRequest(
      JsonElement jsonElement, TypeToken<SlackRequest<? extends SlackRequestData>> typeToken) {
    return gson.fromJson(jsonElement, typeToken.getType());
  }
}
