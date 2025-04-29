/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.feel;

import java.util.Map;

public record FeelInputParam(
    String name,
    String description,
    String type,
    Map<String, Object> schema,
    Map<String, Object> options) {

  public FeelInputParam(String name) {
    this(name, null, null, null, null);
  }

  public FeelInputParam(String name, String description) {
    this(name, description, null, null, null);
  }

  public FeelInputParam(String name, String description, String type) {
    this(name, description, type, null, null);
  }

  public FeelInputParam(String name, String description, String type, Map<String, Object> schema) {
    this(name, description, type, schema, null);
  }
}
