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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The set of "operation-group dropdowns" is discovered as the union of keys across {@code
 * presets[].properties}. For each such key, this rule enforces:
 *
 * <ul>
 *   <li>The matching entry in {@code properties[]} has {@code group == "operation"}.
 *   <li>An {@code "operation"} group is declared in {@code groups[]} and is the first entry.
 * </ul>
 */
public class PresetOperationGroupConsistencyRule implements Rule {

  public static final String OPERATION_GROUP_ID = "operation";

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file)) {
      return List.of();
    }
    Set<String> opKeys = discoverOperationKeys(template);
    List<Finding> findings = new ArrayList<>();
    if (!opKeys.isEmpty()) {
      checkPropertyGroups(file, template, opKeys, findings);
      checkOperationGroupFirst(file, template, findings);
    }
    return findings;
  }

  private Set<String> discoverOperationKeys(JsonNode template) {
    Set<String> keys = new LinkedHashSet<>();
    JsonNode presets = template.path(ElementTemplate.PRESETS);
    if (!presets.isArray()) {
      return keys;
    }
    for (JsonNode preset : presets) {
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!properties.isObject()) {
        continue;
      }
      properties.fieldNames().forEachRemaining(keys::add);
    }
    return keys;
  }

  private void checkPropertyGroups(
      Path file, JsonNode template, Set<String> opKeys, List<Finding> findings) {
    JsonNode props = template.path(ElementTemplate.PROPERTIES);
    if (!props.isArray()) {
      return;
    }
    Set<String> flagged = new HashSet<>();
    for (int i = 0; i < props.size(); i++) {
      JsonNode prop = props.get(i);
      JsonNode idNode = prop.path(ElementTemplate.ID);
      if (!idNode.isTextual()) {
        continue;
      }
      String propId = idNode.asText();
      if (!opKeys.contains(propId) || flagged.contains(propId)) {
        continue;
      }
      JsonNode groupNode = prop.path(ElementTemplate.GROUP);
      if (!groupNode.isTextual() || !OPERATION_GROUP_ID.equals(groupNode.asText())) {
        findings.add(
            Finding.error(
                file,
                "/properties/" + i + "/group",
                id(),
                "Property \""
                    + propId
                    + "\" is referenced by a preset but its group is "
                    + (groupNode.isTextual() ? "\"" + groupNode.asText() + "\"" : "missing")
                    + " — expected \"operation\"."));
        flagged.add(propId);
      }
    }
  }

  private void checkOperationGroupFirst(Path file, JsonNode template, List<Finding> findings) {
    JsonNode groups = template.path(ElementTemplate.GROUPS);
    if (!groups.isArray() || groups.isEmpty()) {
      findings.add(
          Finding.error(
              file,
              "/groups",
              id(),
              "Template uses operation presets but declares no \"operation\" group."));
      return;
    }
    JsonNode first = groups.get(0);
    JsonNode firstId = first.path(ElementTemplate.ID);
    if (!firstId.isTextual() || !OPERATION_GROUP_ID.equals(firstId.asText())) {
      // Look up the index of the operation group, if any, for a more useful message.
      int opIndex = -1;
      for (int i = 0; i < groups.size(); i++) {
        JsonNode gid = groups.get(i).path(ElementTemplate.ID);
        if (gid.isTextual() && OPERATION_GROUP_ID.equals(gid.asText())) {
          opIndex = i;
          break;
        }
      }
      String pointer = opIndex >= 0 ? "/groups/" + opIndex : "/groups";
      String message =
          opIndex >= 0
              ? "Operation group must be the first entry in groups[] (currently at index "
                  + opIndex
                  + ")."
              : "Template uses operation presets but declares no \"operation\" group.";
      findings.add(Finding.error(file, pointer, id(), message));
    }
  }
}
