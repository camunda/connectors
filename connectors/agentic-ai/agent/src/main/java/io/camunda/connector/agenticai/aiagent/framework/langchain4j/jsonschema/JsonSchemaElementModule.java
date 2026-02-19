/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema;

import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

public class JsonSchemaElementModule extends SimpleModule {

  public JsonSchemaElementModule() {
    super("JsonSchemaElementModule");
  }

  @Override
  public void setupModule(SetupContext context) {
    addSerializer(JsonSchemaElement.class, new JsonSchemaElementSerializer());
    addDeserializer(JsonSchemaElement.class, new JsonSchemaElementDeserializer());
    super.setupModule(context);
  }
}
