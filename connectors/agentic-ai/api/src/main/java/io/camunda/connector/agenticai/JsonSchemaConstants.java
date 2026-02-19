/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai;

public abstract class JsonSchemaConstants {
  private JsonSchemaConstants() {}

  public static final String TYPE_OBJECT = "object";
  public static final String TYPE_STRING = "string";
  public static final String TYPE_NUMBER = "number";
  public static final String TYPE_INTEGER = "integer";
  public static final String TYPE_BOOLEAN = "boolean";
  public static final String TYPE_ARRAY = "array";
  public static final String TYPE_NULL = "null";

  public static final String PROPERTY_TYPE = "type";
  public static final String PROPERTY_DESCRIPTION = "description";
  public static final String PROPERTY_REQUIRED = "required";
  public static final String PROPERTY_ADDITIONAL_PROPERTIES = "additionalProperties";
  public static final String PROPERTY_PROPERTIES = "properties";
  public static final String PROPERTY_ENUM = "enum";
  public static final String PROPERTY_ANYOF = "anyOf";
  public static final String PROPERTY_ITEMS = "items";
  public static final String PROPERTY_DEFINITIONS = "$defs";
  public static final String PROPERTY_REF = "$ref";
}
