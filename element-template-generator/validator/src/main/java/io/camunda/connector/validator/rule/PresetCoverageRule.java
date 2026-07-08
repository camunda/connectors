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
package io.camunda.connector.validator.rule;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.validator.core.ConditionEvaluator;
import io.camunda.connector.validator.core.ElementTemplate;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Verifies that the set of {@code presets[]} and the set of leaf {@code steps} both enumerate
 * exactly the reachable leaves of the discriminator tree.
 *
 * <p>A "reachable leaf" is a stable assignment to the discovered operation-group dropdowns: every
 * key in the assignment must have a property declaration whose {@code condition} is satisfied under
 * the assignment (and whose value is among declared choices), and every op-dropdown key NOT in the
 * assignment must have no satisfied declaration under that assignment.
 *
 * <p>For a two-level connector with group "Table" (3 ops) and group "Item" (4 ops), this produces 7
 * reachable leaves — counts add across conditional branches, not multiply.
 */
public class PresetCoverageRule implements Rule {

  static final int MAX_CANDIDATES = 10_000_000;

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file, template)) {
      return List.of();
    }
    JsonNode presetsNode = template.path(ElementTemplate.PRESETS);
    if (!presetsNode.isArray() || presetsNode.isEmpty()) {
      return List.of();
    }
    Set<String> opKeys = discoverOpKeys(presetsNode);
    if (opKeys.isEmpty()) {
      return List.of();
    }
    Map<String, List<Declaration>> declsByKey = collectDeclarations(template, opKeys);

    long searchSpace = candidateSearchSpace(opKeys, declsByKey);
    if (searchSpace > MAX_CANDIDATES) {
      return List.of(
          Finding.error(
              file,
              "/presets",
              id(),
              "Operation-metadata search space exceeds the cap ("
                  + MAX_CANDIDATES
                  + " candidate assignments) and is too large to exhaustively check. Reduce the number of operation discriminators or choices, or split the template."));
    }

    Set<Map<String, String>> reachable = enumerateReachableLeaves(opKeys, declsByKey);
    Set<Map<String, String>> presetAssignments = extractPresetAssignments(presetsNode);
    List<Map<String, String>> stepLeafAssignments =
        extractStepLeafAssignments(template, presetsNode);

    List<Finding> findings = new ArrayList<>();
    findings.addAll(reportMissingFromPresets(file, reachable, presetAssignments));
    findings.addAll(reportOrphanAndDuplicatePresets(file, presetsNode, reachable));
    findings.addAll(reportStepLeafCoverage(file, reachable, stepLeafAssignments));
    return findings;
  }

  private long candidateSearchSpace(Set<String> opKeys, Map<String, List<Declaration>> declsByKey) {
    long product = 1L;
    for (String k : opKeys) {
      Set<String> union = new LinkedHashSet<>();
      for (Declaration d : declsByKey.get(k)) {
        union.addAll(d.choices);
      }
      long factor = 1L + union.size();
      if (factor > MAX_CANDIDATES) {
        return Long.MAX_VALUE;
      }
      product = Math.multiplyExact(product, factor);
      if (product > MAX_CANDIDATES) {
        return product;
      }
    }
    return product;
  }

  private Set<String> discoverOpKeys(JsonNode presets) {
    Set<String> keys = new LinkedHashSet<>();
    for (JsonNode preset : presets) {
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (properties.isObject()) {
        properties.fieldNames().forEachRemaining(keys::add);
      }
    }
    return keys;
  }

  private Map<String, List<Declaration>> collectDeclarations(
      JsonNode template, Set<String> opKeys) {
    Map<String, List<Declaration>> result = new HashMap<>();
    for (String k : opKeys) {
      result.put(k, new ArrayList<>());
    }
    JsonNode props = template.path(ElementTemplate.PROPERTIES);
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path(ElementTemplate.ID);
      if (!idNode.isTextual()) {
        continue;
      }
      String id = idNode.asText();
      if (!opKeys.contains(id)) {
        continue;
      }
      Set<String> choices = new LinkedHashSet<>();
      JsonNode choicesNode = prop.path(ElementTemplate.CHOICES);
      if (choicesNode.isArray()) {
        for (JsonNode c : choicesNode) {
          JsonNode v = c.path(ElementTemplate.VALUE);
          if (v.isTextual()) {
            choices.add(v.asText());
          }
        }
      }
      JsonNode condition = prop.path(ElementTemplate.CONDITION);
      result.get(id).add(new Declaration(condition, choices));
    }
    return result;
  }

  private Set<Map<String, String>> enumerateReachableLeaves(
      Set<String> opKeys, Map<String, List<Declaration>> declsByKey) {
    List<String> orderedKeys = new ArrayList<>(opKeys);
    Map<String, Set<String>> choicesUnion = new HashMap<>();
    for (String k : orderedKeys) {
      Set<String> union = new LinkedHashSet<>();
      for (Declaration d : declsByKey.get(k)) {
        union.addAll(d.choices);
      }
      choicesUnion.put(k, union);
    }
    List<Map<String, String>> candidates = new ArrayList<>();
    enumerate(orderedKeys, 0, new LinkedHashMap<>(), choicesUnion, candidates);
    Set<Map<String, String>> reachable = new LinkedHashSet<>();
    for (Map<String, String> candidate : candidates) {
      if (isStable(candidate, opKeys, declsByKey)) {
        reachable.add(normalize(candidate));
      }
    }
    return reachable;
  }

  private void enumerate(
      List<String> keys,
      int idx,
      Map<String, String> current,
      Map<String, Set<String>> choices,
      List<Map<String, String>> output) {
    if (idx == keys.size()) {
      output.add(new LinkedHashMap<>(current));
      return;
    }
    String key = keys.get(idx);
    enumerate(keys, idx + 1, current, choices, output);
    for (String c : choices.get(key)) {
      current.put(key, c);
      enumerate(keys, idx + 1, current, choices, output);
      current.remove(key);
    }
  }

  private boolean isStable(
      Map<String, String> assignment,
      Set<String> opKeys,
      Map<String, List<Declaration>> declsByKey) {
    for (String key : opKeys) {
      List<Declaration> decls = declsByKey.get(key);
      boolean anyVisible = false;
      boolean anyValid = false;
      for (Declaration d : decls) {
        if (ConditionEvaluator.evaluate(d.condition, assignment)) {
          anyVisible = true;
          if (assignment.containsKey(key)
              && (d.choices.isEmpty() || d.choices.contains(assignment.get(key)))) {
            anyValid = true;
          }
        }
      }
      boolean assigned = assignment.containsKey(key);
      if (assigned != anyVisible) {
        return false;
      }
      if (assigned && !anyValid) {
        return false;
      }
    }
    return true;
  }

  private Set<Map<String, String>> extractPresetAssignments(JsonNode presets) {
    Set<Map<String, String>> result = new LinkedHashSet<>();
    for (JsonNode preset : presets) {
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!properties.isObject()) {
        continue;
      }
      result.add(normalize(extractTextAssignment(properties)));
    }
    return result;
  }

  private List<Map<String, String>> extractStepLeafAssignments(
      JsonNode template, JsonNode presetsNode) {
    Map<String, Map<String, String>> presetsById = new HashMap<>();
    for (JsonNode preset : presetsNode) {
      JsonNode idNode = preset.path(ElementTemplate.ID);
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!idNode.isTextual() || !properties.isObject()) {
        continue;
      }
      presetsById.put(idNode.asText(), normalize(extractTextAssignment(properties)));
    }
    List<Map<String, String>> result = new ArrayList<>();
    JsonNode steps = template.path(ElementTemplate.STEPS);
    if (steps.isArray()) {
      collectLeaves(steps, presetsById, result);
    }
    return result;
  }

  private static Map<String, String> extractTextAssignment(JsonNode properties) {
    Map<String, String> assignment = new LinkedHashMap<>();
    properties
        .properties()
        .forEach(
            e -> {
              if (e.getValue().isTextual()) {
                assignment.put(e.getKey(), e.getValue().asText());
              }
            });
    return assignment;
  }

  private void collectLeaves(
      JsonNode steps, Map<String, Map<String, String>> presetsById, List<Map<String, String>> out) {
    for (JsonNode step : steps) {
      JsonNode children = step.path(ElementTemplate.STEPS);
      if (children.isArray()) {
        collectLeaves(children, presetsById, out);
        continue;
      }
      JsonNode presetIdNode = step.path(ElementTemplate.PRESET_ID);
      if (presetIdNode.isTextual()) {
        Map<String, String> assignment = presetsById.get(presetIdNode.asText());
        if (assignment != null) {
          out.add(assignment);
        }
      }
    }
  }

  private List<Finding> reportMissingFromPresets(
      Path file, Set<Map<String, String>> reachable, Set<Map<String, String>> presetAssignments) {
    List<Finding> findings = new ArrayList<>();
    Set<Map<String, String>> missing = new LinkedHashSet<>(reachable);
    missing.removeAll(presetAssignments);
    for (Map<String, String> assignment : missing) {
      findings.add(
          Finding.error(
              file,
              "/presets",
              id(),
              "Reachable operation \"" + format(assignment) + "\" has no matching preset."));
    }
    return findings;
  }

  private List<Finding> reportOrphanAndDuplicatePresets(
      Path file, JsonNode presetsNode, Set<Map<String, String>> reachable) {
    List<Finding> findings = new ArrayList<>();
    Set<Map<String, String>> seen = new HashSet<>();
    for (int i = 0; i < presetsNode.size(); i++) {
      JsonNode preset = presetsNode.get(i);
      JsonNode idNode = preset.path(ElementTemplate.ID);
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!idNode.isTextual() || !properties.isObject()) {
        continue;
      }
      Map<String, String> normalized = normalize(extractTextAssignment(properties));
      if (!reachable.contains(normalized)) {
        findings.add(
            Finding.error(
                file,
                "/presets/" + i,
                id(),
                "Preset \""
                    + idNode.asText()
                    + "\" does not correspond to any reachable operation."));
      }
      if (!seen.add(normalized)) {
        findings.add(
            Finding.error(
                file,
                "/presets/" + i,
                id(),
                "Preset \""
                    + idNode.asText()
                    + "\" duplicates an earlier preset's operation assignment."));
      }
    }
    return findings;
  }

  private List<Finding> reportStepLeafCoverage(
      Path file, Set<Map<String, String>> reachable, List<Map<String, String>> stepLeaves) {
    List<Finding> findings = new ArrayList<>();
    Map<Map<String, String>, Integer> stepCounts = new HashMap<>();
    for (Map<String, String> leaf : stepLeaves) {
      stepCounts.merge(leaf, 1, Integer::sum);
    }
    Set<Map<String, String>> missing = new LinkedHashSet<>(reachable);
    missing.removeAll(stepCounts.keySet());
    for (Map<String, String> assignment : missing) {
      findings.add(
          Finding.error(
              file,
              "/steps",
              id(),
              "Reachable operation \"" + format(assignment) + "\" has no matching leaf step."));
    }
    Set<Map<String, String>> orphan = new LinkedHashSet<>(stepCounts.keySet());
    orphan.removeAll(reachable);
    for (Map<String, String> assignment : orphan) {
      findings.add(
          Finding.error(
              file,
              "/steps",
              id(),
              "Step leaf \""
                  + format(assignment)
                  + "\" does not correspond to any reachable operation."));
    }
    for (Map.Entry<Map<String, String>, Integer> entry : stepCounts.entrySet()) {
      if (entry.getValue() > 1 && reachable.contains(entry.getKey())) {
        findings.add(
            Finding.error(
                file,
                "/steps",
                id(),
                "Operation \""
                    + format(entry.getKey())
                    + "\" appears "
                    + entry.getValue()
                    + " times in the step tree — expected exactly once."));
      }
    }
    return findings;
  }

  private Map<String, String> normalize(Map<String, String> assignment) {
    return Collections.unmodifiableMap(new TreeMap<>(assignment));
  }

  private String format(Map<String, String> assignment) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> e : assignment.entrySet()) {
      if (!first) sb.append(", ");
      sb.append(e.getKey()).append("=").append(e.getValue());
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }

  private record Declaration(JsonNode condition, Set<String> choices) {}
}
