/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pattern and default fall-throughs emit one INFO log per (api family, model id) so operators
 * notice when they're running on best-effort capabilities. Exact / alias matches are silent —
 * they're verified declarations.
 */
public class ModelCapabilitiesResolverImpl implements ModelCapabilitiesResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ModelCapabilitiesResolverImpl.class);

  static final ModelCapabilities CONSERVATIVE_DEFAULTS =
      new ModelCapabilities(
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          false,
          false,
          false,
          false,
          null,
          null);

  private static final ModelCapabilitiesYaml CONSERVATIVE_DEFAULTS_YAML =
      new ModelCapabilitiesYaml(
          new ModelCapabilitiesYaml.InputModalities(List.of(Modality.TEXT), List.of(Modality.TEXT)),
          new ModelCapabilitiesYaml.OutputModalities(List.of(Modality.TEXT)),
          false,
          false,
          false,
          false,
          null,
          null);

  private final CapabilityMatrix matrix;
  private final ObjectMapper mapper;
  private final JsonNode conservativeBase;
  private final Set<String> loggedKeys = ConcurrentHashMap.newKeySet();

  public ModelCapabilitiesResolverImpl(CapabilityMatrix matrix, ObjectMapper mapper) {
    this.matrix = matrix;
    this.mapper = mapper;
    this.conservativeBase = mapper.valueToTree(CONSERVATIVE_DEFAULTS_YAML);
  }

  @Override
  public ModelCapabilities resolve(
      String apiFamily, String modelId, Optional<ModelCapabilities> override) {
    // Partial/deep-merge of a per-element override is deferred to the chunk that wires the FEEL
    // capability override (a sparse override type will be introduced there).
    if (override.isPresent()) {
      return override.get();
    }

    final ApiFamily family = matrix.families().get(apiFamily);
    if (family == null) {
      logOnce(
          "missing-family:" + apiFamily,
          "No capability matrix entry for api family '{}'; using conservative defaults",
          apiFamily);
      return CONSERVATIVE_DEFAULTS;
    }

    final ModelEntry exact = findExact(family.models(), modelId);
    if (exact != null) {
      return merge(family.defaults(), exact.capabilities());
    }

    final MatchedPattern matched = findLongestPattern(family.models(), modelId);
    if (matched != null) {
      logOnce(
          "pattern:" + apiFamily + ":" + modelId,
          "Capability matrix pattern '{}' matched model '{}' (api family '{}')",
          matched.pattern(),
          modelId,
          apiFamily);
      return merge(family.defaults(), matched.entry().capabilities());
    }

    logOnce(
        "default:" + apiFamily + ":" + modelId,
        "No capability matrix entry for model '{}' under api family '{}'; using conservative defaults",
        modelId,
        apiFamily);
    return CONSERVATIVE_DEFAULTS;
  }

  @Nullable
  private static ModelEntry findExact(List<ModelEntry> models, String modelId) {
    for (ModelEntry entry : models) {
      if (modelId.equals(entry.id()) || entry.aliases().contains(modelId)) {
        return entry;
      }
    }
    return null;
  }

  @Nullable
  private static MatchedPattern findLongestPattern(List<ModelEntry> models, String modelId) {
    MatchedPattern best = null;
    for (ModelEntry entry : models) {
      for (String pattern : entry.patterns()) {
        if (matchesGlob(pattern, modelId)
            && (best == null || pattern.length() > best.pattern().length())) {
          best = new MatchedPattern(entry, pattern);
        }
      }
    }
    return best;
  }

  private record MatchedPattern(ModelEntry entry, String pattern) {}

  static boolean matchesGlob(String glob, String value) {
    final String[] parts = glob.split("\\*", -1);
    final StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(parts[i]));
    }
    regex.append('$');
    return Pattern.matches(regex.toString(), value);
  }

  private ModelCapabilities merge(
      @Nullable JsonNode familyDefaults, @Nullable JsonNode modelOverrides) {
    final JsonNode merged = deepMerge(deepMerge(conservativeBase, familyDefaults), modelOverrides);
    try {
      return mapper.treeToValue(merged, ModelCapabilitiesYaml.class).toModelCapabilities();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to materialise model capabilities", e);
    }
  }

  /**
   * Spring-Boot-style deep merge: object maps merge recursively, list and scalar values from the
   * overlay replace the base verbatim.
   */
  static JsonNode deepMerge(@Nullable JsonNode base, @Nullable JsonNode overlay) {
    if (overlay == null || overlay.isNull() || overlay.isMissingNode()) {
      return base != null ? base : NullNode.getInstance();
    }
    if (base == null
        || base.isNull()
        || base.isMissingNode()
        || !base.isObject()
        || !overlay.isObject()) {
      return overlay;
    }
    final ObjectNode merged = base.deepCopy();
    overlay
        .fields()
        .forEachRemaining(
            entry ->
                merged.set(
                    entry.getKey(), deepMerge(merged.get(entry.getKey()), entry.getValue())));
    return merged;
  }

  private void logOnce(String dedupKey, String format, Object... args) {
    if (loggedKeys.add(dedupKey)) {
      LOG.info(format, args);
    }
  }
}
