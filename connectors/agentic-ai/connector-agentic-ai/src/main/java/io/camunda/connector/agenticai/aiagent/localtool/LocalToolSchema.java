/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small fluent builder of the object schema of a local tool's arguments. It covers flat
 * parameters; pass a {@link Map} schema to {@link LocalTool.Builder#inputSchema(Map)} for anything
 * more elaborate.
 *
 * <p>The maps keep the order of declaration, so the schema serializes to the same bytes in every
 * JVM; the definition is part of the configuration fingerprint and of the request sent to the
 * model. Build a schema passed as a {@link Map} the same way.
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
    final var array = new LinkedHashMap<String, Object>();
    array.put("type", "array");
    array.put("description", description);
    array.put("items", Map.of("type", "string"));
    properties.put(name, Collections.unmodifiableMap(array));
    return markRequired(name, isRequired);
  }

  /**
   * A document parameter, a string schema with the format {@link
   * DocumentParameterSchema#FORMAT_DOCUMENT_ID}: the model passes the id of a known document, the
   * tool receives the id and looks the document up in the registry of its context.
   */
  public LocalToolSchema document(String name, String description, boolean isRequired) {
    properties.put(
        name, Collections.unmodifiableMap(DocumentParameterSchema.documentSchema(description)));
    return markRequired(name, isRequired);
  }

  /** The schema as a map: {@code {"type": "object", "properties": ..., "required": [...]}}. */
  public Map<String, Object> build() {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", Collections.unmodifiableMap(new LinkedHashMap<>(properties)));
    if (!required.isEmpty()) {
      schema.put("required", List.copyOf(required));
    }
    return Collections.unmodifiableMap(schema);
  }

  private LocalToolSchema property(
      String name, String type, String description, boolean isRequired) {
    final var property = new LinkedHashMap<String, Object>();
    property.put("type", type);
    property.put("description", description);
    properties.put(name, Collections.unmodifiableMap(property));
    return markRequired(name, isRequired);
  }

  private LocalToolSchema markRequired(String name, boolean isRequired) {
    if (isRequired) {
      required.add(name);
    }
    return this;
  }
}
