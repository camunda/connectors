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
import io.camunda.connector.validator.core.JsonPointers;
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * For each preset, every pinned property's {@code condition} must evaluate to true under the
 * preset's own assignment. Catches presets whose pinned values are mutually exclusive under the
 * template's conditions (e.g. setting {@code operationGroup=A} alongside a property gated on {@code
 * operationGroup=B}).
 */
public class PresetConditionsSatisfiedRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file, template)) {
      return List.of();
    }
    JsonNode presets = template.path(ElementTemplate.PRESETS);
    if (!presets.isArray()) {
      return List.of();
    }
    Map<String, List<JsonNode>> conditionsByPropertyId = collectConditions(template);
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < presets.size(); i++) {
      JsonNode preset = presets.get(i);
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!properties.isObject()) {
        continue;
      }
      Map<String, String> assignment = new LinkedHashMap<>();
      properties
          .properties()
          .forEach(
              e -> {
                if (e.getValue().isTextual()) {
                  assignment.put(e.getKey(), e.getValue().asText());
                }
              });
      for (Map.Entry<String, String> entry : assignment.entrySet()) {
        String key = entry.getKey();
        List<JsonNode> conditions = conditionsByPropertyId.get(key);
        if (conditions == null || conditions.isEmpty()) {
          continue;
        }
        boolean anyHolds = false;
        for (JsonNode cond : conditions) {
          if (ConditionEvaluator.evaluate(cond, assignment)) {
            anyHolds = true;
            break;
          }
        }
        if (!anyHolds) {
          findings.add(
              Finding.error(
                  file,
                  "/presets/" + i + "/properties/" + JsonPointers.escape(key),
                  id(),
                  "Preset pins property \""
                      + key
                      + "\" but no declaration of that property has a condition that holds under "
                      + "the preset's assignment."));
        }
      }
    }
    return findings;
  }

  private Map<String, List<JsonNode>> collectConditions(JsonNode template) {
    Map<String, List<JsonNode>> result = new HashMap<>();
    JsonNode props = template.path(ElementTemplate.PROPERTIES);
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path(ElementTemplate.ID);
      if (!idNode.isTextual()) {
        continue;
      }
      JsonNode condition = prop.path(ElementTemplate.CONDITION);
      result.computeIfAbsent(idNode.asText(), k -> new ArrayList<>()).add(condition);
    }
    return result;
  }
}
