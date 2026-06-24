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
import io.camunda.connector.validator.core.ElementTemplate;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every leaf step's {@code presetId} must resolve to some {@code presets[].id} declared at the
 * template root.
 */
public class PresetIdResolvesRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file)) {
      return List.of();
    }
    JsonNode steps = template.path(ElementTemplate.STEPS);
    if (!steps.isArray()) {
      return List.of();
    }
    Set<String> declaredIds = collectPresetIds(template);
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      walk(steps.get(i), "/steps/" + i, file, declaredIds, findings);
    }
    return findings;
  }

  private Set<String> collectPresetIds(JsonNode template) {
    Set<String> ids = new HashSet<>();
    JsonNode presets = template.path(ElementTemplate.PRESETS);
    if (!presets.isArray()) {
      return ids;
    }
    for (JsonNode entry : presets) {
      JsonNode idNode = entry.path(ElementTemplate.ID);
      if (idNode.isTextual()) {
        ids.add(idNode.asText());
      }
    }
    return ids;
  }

  private void walk(
      JsonNode node, String pointer, Path file, Set<String> declaredIds, List<Finding> findings) {
    if (!node.isObject()) {
      return;
    }
    JsonNode children = node.path(ElementTemplate.STEPS);
    if (children.isArray()) {
      for (int i = 0; i < children.size(); i++) {
        walk(children.get(i), pointer + "/steps/" + i, file, declaredIds, findings);
      }
      return;
    }
    JsonNode presetId = node.path(ElementTemplate.PRESET_ID);
    if (!presetId.isTextual()) {
      return;
    }
    String value = presetId.asText();
    if (!declaredIds.contains(value)) {
      findings.add(
          Finding.error(
              file,
              pointer + "/presetId",
              id(),
              "Leaf step references presetId \""
                  + value
                  + "\" which is not declared in presets[]."));
    }
  }
}
