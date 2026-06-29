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
import io.camunda.connector.validator.core.JsonPointers;
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For every entry of the top-level {@code presets[]} array, each key inside {@code properties} must
 * reference a property declared in the template's top-level {@code properties[]}, and the value
 * must be one of the property's declared {@code choices} (when choices are declared).
 */
public class PresetTargetExistsRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file)) {
      return List.of();
    }
    JsonNode presets = template.path(ElementTemplate.PRESETS);
    if (!presets.isArray()) {
      return List.of();
    }
    Map<String, Set<String>> propertyChoices = collectPropertyChoices(template);
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < presets.size(); i++) {
      JsonNode preset = presets.get(i);
      JsonNode properties = preset.path(ElementTemplate.PROPERTIES);
      if (!properties.isObject()) {
        continue;
      }
      String pointer = "/presets/" + i + "/properties";
      for (Map.Entry<String, JsonNode> entry : properties.properties()) {
        String key = entry.getKey();
        JsonNode value = entry.getValue();
        String entryPointer = pointer + "/" + JsonPointers.escape(key);

        if (!propertyChoices.containsKey(key)) {
          findings.add(
              Finding.error(
                  file,
                  entryPointer,
                  id(),
                  "Preset references property \""
                      + key
                      + "\" which does not exist in this template."));
          continue;
        }
        if (!value.isTextual()) {
          findings.add(
              Finding.error(
                  file,
                  entryPointer,
                  id(),
                  "Preset value for property \""
                      + key
                      + "\" must be a string (got "
                      + value.getNodeType().toString().toLowerCase()
                      + ")."));
          continue;
        }
        Set<String> choices = propertyChoices.get(key);
        if (!choices.isEmpty() && !choices.contains(value.asText())) {
          findings.add(
              Finding.error(
                  file,
                  entryPointer,
                  id(),
                  "Preset value \""
                      + value.asText()
                      + "\" is not one of the declared choices for property \""
                      + key
                      + "\"."));
        }
      }
    }
    return findings;
  }

  private Map<String, Set<String>> collectPropertyChoices(JsonNode template) {
    Map<String, Set<String>> result = new HashMap<>();
    JsonNode props = template.path(ElementTemplate.PROPERTIES);
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path(ElementTemplate.ID);
      if (!idNode.isTextual()) {
        continue;
      }
      Set<String> choices = result.computeIfAbsent(idNode.asText(), k -> new HashSet<>());
      JsonNode choicesNode = prop.path(ElementTemplate.CHOICES);
      if (choicesNode.isArray()) {
        for (JsonNode choice : choicesNode) {
          JsonNode value = choice.path(ElementTemplate.VALUE);
          if (value.isTextual()) {
            choices.add(value.asText());
          }
        }
      }
    }
    return result;
  }
}
