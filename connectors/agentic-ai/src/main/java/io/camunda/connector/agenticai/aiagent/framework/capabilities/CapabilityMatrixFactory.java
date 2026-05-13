/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiFrameworkProperties.ApiFamilyProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiFrameworkProperties.ModelEntryProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialises a {@link CapabilityMatrix} from {@link AgenticAiFrameworkProperties} bound by Spring
 * Boot. Per-entry capability sub-trees ({@code defaults} and {@code capabilities}) are converted to
 * {@link JsonNode} so the resolver can deep-merge them at lookup time.
 *
 * <p>Each {@code models} map entry must carry exactly one discriminator:
 *
 * <ul>
 *   <li>An explicit {@code pattern} field — a glob string (uses {@code *} only) or a list of globs.
 *       The entry matches when any glob in the list matches the requested model id; longest-match
 *       across entries wins. Aliases are not allowed for pattern entries.
 *   <li>An explicit {@code id} field — the entry matches that model id exactly.
 *   <li>Neither — the map key itself is treated as the model id.
 * </ul>
 *
 * <p>{@code *} and {@code .} cannot appear in the map key because Spring Boot's map binding strips
 * them; pattern entries must declare the glob in the {@code pattern} field while the map key stays
 * a stable, override-friendly identifier.
 */
public final class CapabilityMatrixFactory {

  private CapabilityMatrixFactory() {}

  public static CapabilityMatrix build(
      AgenticAiFrameworkProperties properties, ObjectMapper objectMapper) {
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
        family.defaults() == null ? null : objectMapper.valueToTree(family.defaults());
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
    final List<String> patterns = nonBlank(entry.pattern());
    final boolean patternExplicit = !patterns.isEmpty();

    if (idExplicit && patternExplicit) {
      throw new IllegalStateException(
          "Capability matrix entry '%s' under api family '%s' must specify at most one of `id` or `pattern`"
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
            : objectMapper.valueToTree(entry.capabilities());

    return new ModelEntry(id, entry.aliases(), resolvedPatterns, capabilities);
  }

  private static List<String> nonBlank(List<String> values) {
    return values.stream().filter(p -> p != null && !p.isBlank()).toList();
  }
}
