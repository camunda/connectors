/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import java.util.Map;

public class ObjectNodeConverter {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ObjectNode toObjectNode(String schema, Map<String, Object> payload) {
    JsonSchema jsonSchema = new JsonSchema(schema);
    JsonNode jsonPayload = objectMapper.convertValue(payload, ObjectNode.class);

    return JsonSchemaUtils.envelope(jsonSchema, jsonPayload);
  }
}
