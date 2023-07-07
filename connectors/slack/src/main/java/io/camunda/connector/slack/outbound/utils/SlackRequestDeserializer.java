/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.camunda.connector.slack.outbound.SlackRequest;
import io.camunda.connector.slack.outbound.SlackRequestData;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SlackRequestDeserializer
    implements JsonDeserializer<SlackRequest<? extends SlackRequestData>> {

  private final String typeElementName;
  private final Gson gson;
  private final Map<String, Class<? extends SlackRequestData>> typeRegistry;

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
