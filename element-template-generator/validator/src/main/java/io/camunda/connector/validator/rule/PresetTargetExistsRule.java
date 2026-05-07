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
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.JsonPointers;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each entry in any {@code presets} object must reference a property {@code id} that exists in the
 * template's top-level {@code properties[]}, and the value must be one of the property's declared
 * {@code choices} (when choices are declared).
 *
 * <p>The {@code presets} field is an object keyed by property id, e.g.
 *
 * <pre>{@code
 * "presets": { "operationGroup": "actions", "eventOperationType": "createWorkflowDispatchEvent" }
 * }</pre>
 *
 * <p>The rule scans for {@code presets} objects anywhere in the tree, so it covers the typical
 * {@code template.steps[].steps[].presets} nesting as well as any other location.
 */
public class PresetTargetExistsRule implements Rule {

  public static final String ID = "preset-target-exists";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    Map<String, Set<String>> propertyChoices = collectPropertyChoices(template);
    List<Finding> findings = new ArrayList<>();
    walk(template, "", file, propertyChoices, findings);
    return findings;
  }

  private Map<String, Set<String>> collectPropertyChoices(JsonNode template) {
    Map<String, Set<String>> result = new HashMap<>();
    JsonNode props = template.path("properties");
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path("id");
      if (!idNode.isTextual()) {
        continue;
      }
      String propertyId = idNode.asText();
      Set<String> choices = new HashSet<>();
      JsonNode choicesNode = prop.path("choices");
      if (choicesNode.isArray()) {
        for (JsonNode choice : choicesNode) {
          JsonNode value = choice.path("value");
          if (value.isTextual()) {
            choices.add(value.asText());
          }
        }
      }
      result.put(propertyId, choices);
    }
    return result;
  }

  private void walk(
      JsonNode node,
      String pointer,
      Path file,
      Map<String, Set<String>> propertyChoices,
      List<Finding> findings) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String childPointer = pointer + "/" + JsonPointers.escape(entry.getKey());
        if ("presets".equals(entry.getKey()) && entry.getValue().isObject()) {
          checkPresets(entry.getValue(), childPointer, file, propertyChoices, findings);
        }
        walk(entry.getValue(), childPointer, file, propertyChoices, findings);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), pointer + "/" + i, file, propertyChoices, findings);
      }
    }
  }

  private void checkPresets(
      JsonNode presets,
      String presetsPointer,
      Path file,
      Map<String, Set<String>> propertyChoices,
      List<Finding> findings) {
    Iterator<Map.Entry<String, JsonNode>> entries = presets.fields();
    while (entries.hasNext()) {
      Map.Entry<String, JsonNode> entry = entries.next();
      String propertyId = entry.getKey();
      JsonNode value = entry.getValue();
      String entryPointer = presetsPointer + "/" + JsonPointers.escape(propertyId);

      if (!propertyChoices.containsKey(propertyId)) {
        findings.add(
            Finding.error(
                file,
                entryPointer,
                ID,
                "Preset references property \""
                    + propertyId
                    + "\" which does not exist in this template."));
        continue;
      }

      if (!value.isTextual()) {
        continue;
      }
      Set<String> choices = propertyChoices.get(propertyId);
      if (!choices.isEmpty() && !choices.contains(value.asText())) {
        findings.add(
            Finding.error(
                file,
                entryPointer,
                ID,
                "Preset value \""
                    + value.asText()
                    + "\" is not one of the declared choices for property \""
                    + propertyId
                    + "\"."));
      }
    }
  }
}
