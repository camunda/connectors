/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiCapabilitiesProperties.ApiFamilyProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiCapabilitiesProperties.ModelEntryProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialises a {@link CapabilityMatrix} from {@link AgenticAiCapabilitiesProperties} bound by
 * Spring Boot. Per-entry capability sub-trees ({@code defaults} and {@code capabilities}) are
 * converted to {@link JsonNode} so the resolver can deep-merge them at lookup time.
 *
 * <p>Each {@code models} map entry must carry exactly one discriminator:
 *
 * <ul>
 *   <li>An explicit {@code patterns} field — a glob string (uses {@code *} only) or a list of
 *       globs. The entry matches when any glob in the list matches the requested model id;
 *       longest-match across entries wins. Aliases are not allowed for pattern entries.
 *   <li>An explicit {@code id} field — the entry matches that model id exactly.
 *   <li>Neither — the map key itself is treated as the model id.
 * </ul>
 *
 * <p>{@code *} and {@code .} cannot appear in the map key because Spring Boot's map binding strips
 * them; pattern entries must declare the glob in the {@code patterns} field while the map key stays
 * a stable, override-friendly identifier.
 */
public final class CapabilityMatrixFactory {

  private CapabilityMatrixFactory() {}

  public static CapabilityMatrix build(
      AgenticAiCapabilitiesProperties properties, ObjectMapper objectMapper) {
    final Map<String, ApiFamily> families = new LinkedHashMap<>();
    properties
        .capabilities()
        .forEach(
            (familyName, family) ->
                families.put(familyName, buildFamily(familyName, family, objectMapper)));
    return new CapabilityMatrix(families);
  }

  private static ApiFamily buildFamily(
      String familyName, ApiFamilyProperties family, ObjectMapper objectMapper) {
    final JsonNode defaults =
        family.defaults() == null
            ? null
            : normalizeIndexedMaps(objectMapper.valueToTree(family.defaults()));
    final List<ModelEntry> entries = new ArrayList<>();
    family
        .models()
        .forEach(
            (mapKey, entry) -> entries.add(buildEntry(familyName, mapKey, entry, objectMapper)));
    return new ApiFamily(defaults, entries);
  }

  private static ModelEntry buildEntry(
      String familyName, String mapKey, ModelEntryProperties entry, ObjectMapper objectMapper) {
    final boolean idExplicit = entry.id() != null && !entry.id().isBlank();
    final List<String> patterns = nonBlank(entry.patterns());
    final boolean patternExplicit = !patterns.isEmpty();

    if (idExplicit && patternExplicit) {
      throw new IllegalStateException(
          "Capability matrix entry '%s' under api family '%s' must specify at most one of `id` or `patterns`"
              .formatted(mapKey, familyName));
    }

    final String id;
    final List<String> resolvedPatterns;
    if (patternExplicit) {
      id = null;
      resolvedPatterns = patterns;
    } else {
      // id explicit, or id derived from the map key.
      id = idExplicit ? entry.id() : mapKey;
      resolvedPatterns = List.of();
    }

    if (!resolvedPatterns.isEmpty() && !entry.aliases().isEmpty()) {
      throw new IllegalStateException(
          "Capability matrix pattern entry '%s' under api family '%s' cannot declare aliases"
              .formatted(mapKey, familyName));
    }

    final JsonNode capabilities =
        entry.capabilities() == null
            ? objectMapper.createObjectNode()
            : normalizeIndexedMaps(objectMapper.valueToTree(entry.capabilities()));

    return new ModelEntry(id, entry.aliases(), resolvedPatterns, entry.backend(), capabilities);
  }

  private static List<String> nonBlank(List<String> values) {
    return values.stream().filter(p -> p != null && !p.isBlank()).toList();
  }

  /**
   * Spring Boot's relaxed binder has no static type information for the opaque {@code Map<String,
   * Object>} provider capability bag, so it binds YAML/property-source lists inside it as
   * index-keyed maps (e.g. {@code {"0": "adaptive", "1": "disabled"}}) rather than native {@code
   * List}s. {@link ObjectMapper#valueToTree} then serialises those as JSON objects, which the
   * provider's typed DTO (e.g. {@code AnthropicReasoningCapabilities}, expecting a JSON array)
   * fails to deserialise. This recursively rewrites every such index-keyed object node — anywhere
   * in the tree — back into a proper {@link ArrayNode}, restoring the shape a directly-authored
   * YAML list (or a strongly-typed {@code List<T>} field, which Spring binds correctly already and
   * this leaves untouched) would have produced.
   */
  private static JsonNode normalizeIndexedMaps(JsonNode node) {
    if (node.isObject()) {
      if (isIndexedMap(node)) {
        final ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < node.size(); i++) {
          array.add(normalizeIndexedMaps(node.get(Integer.toString(i))));
        }
        return array;
      }
      final ObjectNode result = JsonNodeFactory.instance.objectNode();
      node.fields()
          .forEachRemaining(
              entry -> result.set(entry.getKey(), normalizeIndexedMaps(entry.getValue())));
      return result;
    }
    if (node.isArray()) {
      final ArrayNode result = JsonNodeFactory.instance.arrayNode();
      node.forEach(element -> result.add(normalizeIndexedMaps(element)));
      return result;
    }
    return node;
  }

  /**
   * True when every field name is a distinct non-negative integer covering exactly {@code [0,
   * node.size())} — the shape Spring Boot's relaxed binder produces for a list nested inside a
   * {@code Map<String, Object>} target.
   */
  private static boolean isIndexedMap(JsonNode node) {
    final int size = node.size();
    if (size == 0) {
      return false;
    }
    final boolean[] seen = new boolean[size];
    final Iterator<String> fieldNames = node.fieldNames();
    while (fieldNames.hasNext()) {
      final String name = fieldNames.next();
      if (!name.matches("\\d+")) {
        return false;
      }
      final int index = Integer.parseInt(name);
      if (index >= size || seen[index]) {
        return false;
      }
      seen[index] = true;
    }
    return true;
  }
}
