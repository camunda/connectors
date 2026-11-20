/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.rest.templates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Applies the {@code zeebe:input} bindings of a hand-maintained element template the way the
 * Modeler writes them and Zeebe evaluates them.
 *
 * <p>The Modeler writes one input mapping per property, in template order, and skips properties
 * whose condition does not hold. An optional property with an empty value gets no mapping; any
 * other property without a value gets a mapping without a source, which Zeebe evaluates to null.
 *
 * <p>Zeebe resolves the mappings in one of two modes (the engine setting {@code
 * camunda.processing.engine.mappings.input-mode}), see {@link Mode}. The two differ when a mapping
 * reads a variable that another mapping of the same element writes.
 */
final class TemplateInputMappings {

  /** The input mapping modes of Zeebe 8.10 ({@code EngineConfiguration.InputMappingMode}). */
  enum Mode {
    /**
     * Zeebe's default ({@code CombinedInputMappingResolver}). All mappings become a single FEEL
     * context expression, in which every dotted target {@code a.x} becomes an entry of a nested
     * context {@code a}, placed where the first {@code a.*} target appears. A FEEL entry sees only
     * the entries before it, so an expression inside {@code a} cannot read a plain input that is
     * mapped after the first {@code a.*} input; it reads the process variable of that name instead.
     * It can read an earlier sibling inside {@code a} by its bare name.
     */
    COMBINED,
    /**
     * The opt-in mode ({@code OrderedInputMappingResolver}). Each mapping is evaluated on its own,
     * in BPMN order. A name resolves to the result of an earlier mapping with that top-level target
     * (for a nested target, its fields over the process variable of that name), otherwise to the
     * process variable. Fields of a nested target are not visible by their bare name.
     */
    ORDERED
  }

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();
  private static final FeelExpressionEvaluator FEEL =
      FeelExpressionEvaluatorBuilder.local().build();

  private final JsonNode properties;
  private final Mode mode;

  private TemplateInputMappings(JsonNode properties, Mode mode) {
    this.properties = properties;
    this.mode = mode;
  }

  /**
   * Loads a template whose mappings are evaluated in Zeebe's default {@link Mode#COMBINED} mode.
   */
  static TemplateInputMappings load(String templateFile) throws IOException {
    return load(templateFile, Mode.COMBINED);
  }

  static TemplateInputMappings load(String templateFile, Mode mode) throws IOException {
    return new TemplateInputMappings(
        MAPPER.readTree(new File(templateFile)).get("properties"), mode);
  }

  /** The same template, evaluated in the given mode. */
  TemplateInputMappings inMode(Mode mode) {
    return new TemplateInputMappings(properties, mode);
  }

  /**
   * @param userValues values the user entered, keyed by the property's {@code id} or by the input's
   *     binding name (the id wins); inputs without a user value use the template's {@code value}
   * @return the local variables of the job, as the connector receives them
   */
  Map<String, Object> apply(Map<String, String> userValues) {
    return apply(userValues, Map.of());
  }

  /**
   * @param processVariables variables of the process instance; like in Zeebe, an input mapping
   *     reads them for every name that it cannot resolve from the other mappings
   */
  Map<String, Object> apply(Map<String, String> userValues, Map<String, Object> processVariables) {
    return evaluate(mode, mappings(userValues), processVariables);
  }

  /**
   * The input mappings the Modeler writes, as (target, source) pairs in template order. A null
   * source stands for a mapping without a {@code source} attribute.
   */
  List<Map.Entry<String, String>> mappings(Map<String, String> userValues) {
    List<Map.Entry<String, String>> mappings = new ArrayList<>();
    for (JsonNode property : properties) {
      JsonNode binding = property.path("binding");
      if (!"zeebe:input".equals(binding.path("type").asText())
          || !conditionHolds(property.path("condition"), userValues)) {
        continue;
      }
      String source = effectiveValue(property, userValues);
      boolean empty = source == null || source.isEmpty();
      if (empty && property.path("optional").asBoolean(false)) {
        continue; // the Modeler persists no mapping for an empty optional value
      }
      // the Modeler maps any other property, with its value as the source; an empty string is
      // treated like no value here
      mappings.add(new SimpleEntry<>(binding.path("name").asText(), empty ? null : source));
    }
    return mappings;
  }

  /** Evaluates input mappings in Zeebe's default {@link Mode#COMBINED} mode. */
  static Map<String, Object> evaluate(
      List<Map.Entry<String, String>> mappings, Map<String, Object> processVariables) {
    return evaluate(Mode.COMBINED, mappings, processVariables);
  }

  /**
   * Evaluates input mappings like Zeebe does in the given mode, against the process variables. A
   * static source becomes a string, a missing source null.
   */
  static Map<String, Object> evaluate(
      Mode mode, List<Map.Entry<String, String>> mappings, Map<String, Object> processVariables) {
    return switch (mode) {
      case COMBINED -> evaluateCombined(mappings, processVariables);
      case ORDERED -> evaluateOrdered(mappings, processVariables);
    };
  }

  /**
   * Like Zeebe's {@code VariableMappingTransformer}: the targets are merged into one FEEL context
   * ({@code {a:{x:..,y:..},b:..}}), a nested context keeps the position of its first target, and
   * the whole context is evaluated at once.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> evaluateCombined(
      List<Map.Entry<String, String>> mappings, Map<String, Object> processVariables) {
    Map<String, Object> context = new LinkedHashMap<>();
    for (Map.Entry<String, String> mapping : mappings) {
      putNested(context, mapping.getKey(), mapping.getValue());
    }
    return FEEL.evaluate("=" + asFeelContext(context), Map.class, processVariables);
  }

  @SuppressWarnings("unchecked")
  private static void putNested(Map<String, Object> context, String target, String source) {
    String[] path = target.split("\\.");
    Map<String, Object> current = context;
    for (int i = 0; i < path.length - 1; i++) {
      // like Zeebe, a key that exists keeps its position when it becomes a nested context
      if (!(current.get(path[i]) instanceof Map)) {
        current.put(path[i], new LinkedHashMap<String, Object>());
      }
      current = (Map<String, Object>) current.get(path[i]);
    }
    current.put(path[path.length - 1], source);
  }

  @SuppressWarnings("unchecked")
  private static String asFeelContext(Map<String, Object> context) {
    return context.entrySet().stream()
        .map(entry ->
            entry.getKey()
                + ":"
                + (entry.getValue() instanceof Map
                    ? asFeelContext((Map<String, Object>) entry.getValue())
                    : asFeelValue((String) entry.getValue())))
        .collect(Collectors.joining(",", "{", "}"));
  }

  /**
   * The FEEL expression Zeebe evaluates for a mapping source. Both modes quote a static source
   * alike and escape only double quotes, as {@code VariableMappingTransformer} does in 8.10.9.
   */
  private static String asFeelValue(String source) {
    if (source == null) {
      return "null";
    }
    return source.startsWith("=")
        ? source.substring(1)
        : "\"" + source.replace("\"", "\\\"") + "\"";
  }

  /** A field that a nested target creates, like Zeebe's {@code MappingLevels.Level}. */
  private record Level(Map<String, Object> children, boolean replacedExistingValue) {}

  /** The result of one mapping; it also wraps null, which still hides a process variable. */
  private record Value(Object value) {}

  /**
   * Like Zeebe's {@code OrderedInputMappingResolver} and {@code InputMappingResultBuilder}: each
   * source is evaluated on its own, in BPMN order, and its result is put at its target path. Names
   * resolve first to the top-level results so far, then to the process variables.
   */
  private static Map<String, Object> evaluateOrdered(
      List<Map.Entry<String, String>> mappings, Map<String, Object> processVariables) {
    Map<String, Object> entries = new LinkedHashMap<>();
    for (Map.Entry<String, String> mapping : mappings) {
      Map<String, Object> variables = new HashMap<>(processVariables);
      for (String name : entries.keySet()) {
        variables.put(name, resolve(entries, name, processVariables));
      }
      Object value = FEEL.evaluate("=" + asFeelValue(mapping.getValue()), Object.class, variables);
      put(entries, mapping.getKey().split("\\."), value);
    }
    return document(entries, null);
  }

  /** {@code InputMappingResultBuilder.put}. */
  private static void put(Map<String, Object> entries, String[] path, Object value) {
    Map<String, Object> current = entries;
    for (int i = 0; i < path.length - 1; i++) {
      Object existing = current.get(path[i]);
      Level level;
      if (existing instanceof Level found) {
        level = found;
      } else {
        // replaces an earlier plain value of that name, keeping its position
        level = new Level(new LinkedHashMap<>(), existing != null);
        current.put(path[i], level);
      }
      current = level.children();
    }
    current.put(path[path.length - 1], new Value(value));
  }

  /**
   * {@code InputMappingResultBuilder.get}: a plain result as it is; a nested target as its fields
   * over the process variable of the same name, unless that nested target replaced an earlier plain
   * mapping.
   */
  private static Object resolve(
      Map<String, Object> entries, String name, Map<String, Object> processVariables) {
    Object entry = entries.get(name);
    if (entry instanceof Level level) {
      return document(
          level.children(), level.replacedExistingValue() ? null : processVariables.get(name));
    }
    return ((Value) entry).value();
  }

  /**
   * {@code MappingLevels.serialize}: the fields of a level over the top-level fields of the value
   * it shadows (when that value is a map), nested levels likewise.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> document(Map<String, Object> children, Object shadowed) {
    Map<String, Object> shadowedFields =
        shadowed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    Map<String, Object> merged = new LinkedHashMap<>(shadowedFields);
    merged.putAll(children);
    Map<String, Object> document = new LinkedHashMap<>();
    for (Map.Entry<String, Object> field : merged.entrySet()) {
      Object value = field.getValue();
      if (value instanceof Level level) {
        Object shadowedChild =
            level.replacedExistingValue() ? null : shadowedFields.get(field.getKey());
        document.put(field.getKey(), document(level.children(), shadowedChild));
      } else if (value instanceof Value result) {
        document.put(field.getKey(), result.value());
      } else {
        document.put(field.getKey(), value); // a field of the shadowed value
      }
    }
    return document;
  }

  private boolean conditionHolds(JsonNode condition, Map<String, String> userValues) {
    if (condition.isMissingNode()) {
      return true;
    }
    if (condition.has("allMatch")) {
      return StreamSupport.stream(condition.get("allMatch").spliterator(), false)
          .allMatch(c -> conditionHolds(c, userValues));
    }
    String actual =
        StreamSupport.stream(properties.spliterator(), false)
            .filter(p -> condition.path("property").asText().equals(p.path("id").asText()))
            .findFirst()
            .map(p -> effectiveValue(p, userValues))
            .orElse(null);
    if (condition.has("equals")) {
      return condition.get("equals").asText().equals(actual);
    }
    if (condition.has("oneOf")) {
      return StreamSupport.stream(condition.get("oneOf").spliterator(), false)
          .anyMatch(v -> v.asText().equals(actual));
    }
    if (condition.has("isEmpty")) {
      return condition.get("isEmpty").asBoolean() == (actual == null || actual.isEmpty());
    }
    throw new IllegalArgumentException("Unsupported condition: " + condition);
  }

  private static String effectiveValue(JsonNode property, Map<String, String> userValues) {
    String id = property.path("id").asText(null);
    if (id != null && userValues.containsKey(id)) {
      return userValues.get(id);
    }
    String name = property.path("binding").path("name").asText(null);
    if (name != null && userValues.containsKey(name)) {
      return userValues.get(name);
    }
    return property.has("value") ? property.get("value").asText() : null;
  }
}
