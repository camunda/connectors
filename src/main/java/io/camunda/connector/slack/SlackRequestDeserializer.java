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
