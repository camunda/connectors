/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.jsonschema;

import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

/**
 * Jackson module for serializing and deserializing JsonSchemaElement and its subclasses. This
 * module registers the JsonSchemaElementSerializer and JsonSchemaElementDeserializer.
 */
public class JsonSchemaElementModule extends SimpleModule {

  public JsonSchemaElementModule() {
    super("JsonSchemaElementModule");

    // Register the serializer and deserializer
    addSerializer(JsonSchemaElement.class, new JsonSchemaElementSerializer());
    addDeserializer(JsonSchemaElement.class, new JsonSchemaElementDeserializer());
  }
}
