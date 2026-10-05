/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small fluent builder of the object schema of a local tool's arguments. It covers flat
 * parameters; pass a {@link Map} schema to {@link LocalTool.Builder#inputSchema(Map)} for anything
 * more elaborate.
 *
 * <pre>{@code
 * LocalToolSchema.object()
 *     .string("title", "The title of the note", true)
 *     .document("file", "The document to attach", false)
 * }</pre>
 */
public final class LocalToolSchema {

  private final Map<String, Object> properties = new LinkedHashMap<>();
  private final List<String> required = new ArrayList<>();
  private final List<String> documentParameters = new ArrayList<>();

  private LocalToolSchema() {}

  public static LocalToolSchema object() {
    return new LocalToolSchema();
  }

  public LocalToolSchema string(String name, String description, boolean isRequired) {
    return property(name, "string", description, isRequired);
  }

  public LocalToolSchema integer(String name, String description, boolean isRequired) {
    return property(name, "integer", description, isRequired);
  }

  public LocalToolSchema number(String name, String description, boolean isRequired) {
    return property(name, "number", description, isRequired);
  }

  public LocalToolSchema bool(String name, String description, boolean isRequired) {
    return property(name, "boolean", description, isRequired);
  }

  /** An array of strings. */
  public LocalToolSchema stringArray(String name, String description, boolean isRequired) {
    properties.put(
        name,
        Map.of("type", "array", "description", description, "items", Map.of("type", "string")));
    return markRequired(name, isRequired);
  }

  /**
   * A document parameter: the model passes the id of a known {@code <doc/>} tag, the tool receives
   * the document.
   */
  public LocalToolSchema document(String name, String description, boolean isRequired) {
    property(
        name,
        "string",
        description + " Pass the id of a <doc/> tag of a known document.",
        isRequired);
    documentParameters.add("/properties/" + escape(name));
    return this;
  }

  /** The schema as a map: {@code {"type": "object", "properties": ..., "required": [...]}}. */
  public Map<String, Object> build() {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", Map.copyOf(properties));
    if (!required.isEmpty()) {
      schema.put("required", List.copyOf(required));
    }
    return schema;
  }

  /** The JSON pointers of the document parameters declared so far. */
  public List<String> documentParameters() {
    return List.copyOf(documentParameters);
  }

  private LocalToolSchema property(
      String name, String type, String description, boolean isRequired) {
    properties.put(name, Map.of("type", type, "description", description));
    return markRequired(name, isRequired);
  }

  private LocalToolSchema markRequired(String name, boolean isRequired) {
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  private static String escape(String name) {
    return name.replace("~", "~0").replace("/", "~1");
  }
}
