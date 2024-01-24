/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Custom deserializer for AwsInput. This deserializer is designed for use with newer template
 * versions incorporating template generation (version 7 onwards). It also maintains compatibility
 * with older template versions (such as version 6 and below) that do not use template generation.
 * This dual compatibility is achieved by handling various JSON structures representing different
 * operation types.
 */
public class AwsInputDeserializer extends JsonDeserializer<AwsInput> {

  /**
   * Deserializes JSON to the appropriate AwsInput subtype. Identifies the specific action type
   * (e.g., createTable, deleteTable) based on the JSON content and deserializes it into the
   * corresponding subtype object.
   *
   * @param jp JsonParser for reading JSON content.
   * @param ctxt Deserialization context.
   * @return Deserialized AwsInput subtype object.
   * @throws IOException If an issue occurs while reading JSON content.
   * @throws JsonProcessingException If an error occurs during JSON processing.
   */
  @Override
  public AwsInput deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    JsonNode node = jp.getCodec().readTree(jp);

    var operationType = determineActionType(node);
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    return switch (operationType) {
      case OperationTypes.CREATE_TABLE -> mapper.convertValue(node, CreateTable.class);
      case OperationTypes.DELETE_TABLE -> mapper.convertValue(node, DeleteTable.class);
      case OperationTypes.DESCRIBE_TABLE -> mapper.convertValue(node, DescribeTable.class);
      case OperationTypes.SCAN_TABLE -> mapper.convertValue(node, ScanTable.class);
      case OperationTypes.ADD_ITEM -> mapper.convertValue(node, AddItem.class);
      case OperationTypes.DELETE_ITEM -> mapper.convertValue(node, DeleteItem.class);
      case OperationTypes.GET_ITEM -> mapper.convertValue(node, GetItem.class);
      case OperationTypes.UPDATE_ITEM -> mapper.convertValue(node, UpdateItem.class);
      default -> throw new UnsupportedOperationException(
          "Unsupported action type: " + operationType);
    };
  }

  /**
   * Determines the operation type by inspecting the JSON node. This method supports templates with
   * and without template generation. Newer templates specify operation types such as
   * 'tableOperation' or 'itemOperation', while older templates use a generic 'type' field for
   * operation binding.
   *
   * @param node JSON node containing operation type information.
   * @return A string representing the operation type.
   * @throws UnsupportedOperationException if the operation type is not supported.
   */
  private String determineActionType(JsonNode node) {
    if (node.has("tableOperation")) {
      return node.get("tableOperation").asText();
    } else if (node.has("itemOperation")) {
      return node.get("itemOperation").asText();
    } else if (node.has("type")) {
      return node.get("type").asText();
    }
    throw new UnsupportedOperationException("Unsupported operation type");
  }
}
