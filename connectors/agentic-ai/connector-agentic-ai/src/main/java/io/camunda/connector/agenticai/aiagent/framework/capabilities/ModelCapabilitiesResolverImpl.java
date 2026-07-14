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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ApiFamily;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CapabilityMatrix.ModelEntry;
import java.util.List;
import java.util.Objects;
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

  private final CapabilityMatrix matrix;
  private final ObjectMapper mapper;
  private final JsonNode conservativeBase;
  private final Set<String> loggedKeys = ConcurrentHashMap.newKeySet();

  public ModelCapabilitiesResolverImpl(CapabilityMatrix matrix, ObjectMapper mapper) {
    this.matrix = matrix;
    this.mapper = mapper;
    this.conservativeBase = conservativeBaseTree(mapper);
  }

  private static JsonNode conservativeBaseTree(ObjectMapper mapper) {
    final ObjectNode root = mapper.createObjectNode();
    final ArrayNode text = mapper.createArrayNode().add("text");
    final ObjectNode input = root.putObject("input_modalities");
    input.set("user_message", text.deepCopy());
    input.set("tool_result", text.deepCopy());
    root.putObject("output_modalities").set("assistant_message", text.deepCopy());
    return root;
  }

  @Override
  public <T extends ModelCapabilities> T resolve(
      String apiFamily,
      String modelId,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override,
      Class<? extends ModelCapabilitiesData<T>> dataClass) {

    JsonNode merged = mergedBaseTree(apiFamily, modelId, backend);

    if (override.isPresent()) {
      merged = deepMerge(merged, override.get().toSparseJsonNode(mapper));
    }

    return materialise(merged, dataClass);
  }

  /**
   * Deep-merges {@code conservativeBase -> familyDefaults -> backend-agnostic entry -> backend-
   * specific entry} into a single tree (the pre-override base), logging pattern/default
   * fall-throughs once per (api family, model id).
   */
  private JsonNode mergedBaseTree(String apiFamily, String modelId, @Nullable String backend) {
    final ApiFamily family = matrix.families().get(apiFamily);
    if (family == null) {
      logOnce(
          "missing-family:" + apiFamily,
          "No capability matrix entry for api family '{}'; using conservative defaults",
          apiFamily);
      return conservativeBase;
    }

    final MatchedEntry agnostic = findBest(family.models(), modelId, null);
    final MatchedEntry specific =
        backend == null ? null : findBest(family.models(), modelId, backend);

    if (agnostic == null && specific == null) {
      logOnce(
          "default:" + apiFamily + ":" + modelId,
          "No capability matrix entry for model '{}' under api family '{}'; using family defaults",
          modelId,
          apiFamily);
      return deepMerge(conservativeBase, family.defaults());
    }

    if (agnostic != null && !agnostic.isExact()) {
      logOnce(
          "pattern:" + apiFamily + ":" + modelId,
          "Capability matrix pattern '{}' matched model '{}' (api family '{}')",
          agnostic.pattern(),
          modelId,
          apiFamily);
    }
    if (specific != null && !specific.isExact()) {
      logOnce(
          "pattern:" + apiFamily + ":" + backend + ":" + modelId,
          "Capability matrix pattern '{}' matched model '{}' for backend '{}' (api family '{}')",
          specific.pattern(),
          modelId,
          backend,
          apiFamily);
    }

    JsonNode merged = deepMerge(conservativeBase, family.defaults());
    if (agnostic != null) {
      merged = deepMerge(merged, agnostic.entry().capabilities());
    }
    if (specific != null) {
      merged = deepMerge(merged, specific.entry().capabilities());
    }
    return merged;
  }

  @Override
  public boolean matches(String apiFamily, String modelId, @Nullable String backend) {
    final ApiFamily family = matrix.families().get(apiFamily);
    if (family == null) {
      return false;
    }

    final MatchedEntry agnostic = findBest(family.models(), modelId, null);
    final MatchedEntry specific =
        backend == null ? null : findBest(family.models(), modelId, backend);
    return agnostic != null || specific != null;
  }

  private <T extends ModelCapabilities> T materialise(
      JsonNode merged, Class<? extends ModelCapabilitiesData<T>> dataClass) {
    try {
      return mapper.treeToValue(merged, dataClass).toModelCapabilities();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to materialise model capabilities", e);
    }
  }

  private static boolean matchesBackend(ModelEntry entry, @Nullable String backend) {
    return Objects.equals(entry.backend(), backend);
  }

  @Nullable
  private static MatchedEntry findBest(
      List<ModelEntry> models, String modelId, @Nullable String backend) {
    final ModelEntry exact = findExact(models, modelId, backend);
    if (exact != null) {
      return new MatchedEntry(exact, null);
    }

    final MatchedPattern pattern = findLongestPattern(models, modelId, backend);
    return pattern == null ? null : new MatchedEntry(pattern.entry(), pattern.pattern());
  }

  @Nullable
  private static ModelEntry findExact(
      List<ModelEntry> models, String modelId, @Nullable String backend) {
    for (ModelEntry entry : models) {
      if (matchesBackend(entry, backend)
          && (modelId.equals(entry.id()) || entry.aliases().contains(modelId))) {
        return entry;
      }
    }
    return null;
  }

  @Nullable
  private static MatchedPattern findLongestPattern(
      List<ModelEntry> models, String modelId, @Nullable String backend) {
    MatchedPattern best = null;
    for (ModelEntry entry : models) {
      if (!matchesBackend(entry, backend)) {
        continue;
      }
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

  /**
   * Best match within a single tier (backend-agnostic or backend-specific), together with the glob
   * pattern it matched via, if any. {@code pattern == null} means an exact id/alias match (silent);
   * a non-null pattern means a best-effort glob match (logged once).
   */
  private record MatchedEntry(ModelEntry entry, @Nullable String pattern) {
    boolean isExact() {
      return pattern == null;
    }
  }

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

  private void logOnce(String dedupKey, String format, @Nullable Object... args) {
    if (loggedKeys.add(dedupKey)) {
      LOG.info(format, args);
    }
  }
}
