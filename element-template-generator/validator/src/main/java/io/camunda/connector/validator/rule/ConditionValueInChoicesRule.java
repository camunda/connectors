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
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When a condition compares against a property that declares {@code choices}, the {@code equals} or
 * {@code oneOf} value(s) must be one of the declared choices — otherwise the condition can never
 * fire. Catches typos on the value side of conditions, the counterpart to {@link
 * ConditionTargetExistsRule}.
 *
 * <p>For properties whose {@code choices} differ across {@code condition} branches (e.g. {@code
 * eventOperationType} per {@code operationGroup}), the rule unions all choices for that id — a
 * lenient default that avoids false positives at the cost of missing some bugs.
 *
 * <p>An embedded configuration template ({@code configurationTemplates[*]}) is a self-contained
 * document: its {@code properties[]} is its own scope, disjoint from the host template's, so its
 * conditions are checked against its own choices rather than the host's.
 */
public class ConditionValueInChoicesRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    List<Finding> findings = new ArrayList<>();
    checkScope(template, "", file, findings);

    JsonNode configurationTemplates = template.path(ElementTemplate.CONFIGURATION_TEMPLATES);
    if (configurationTemplates.isArray()) {
      for (int i = 0; i < configurationTemplates.size(); i++) {
        JsonNode configurationTemplate = configurationTemplates.get(i);
        checkScope(
            configurationTemplate,
            "/" + ElementTemplate.CONFIGURATION_TEMPLATES + "/" + i,
            file,
            findings);
      }
    }
    return findings;
  }

  private void checkScope(JsonNode scope, String pointer, Path file, List<Finding> findings) {
    Map<String, Set<String>> choicesById = collectChoiceUnion(scope);
    if (!choicesById.isEmpty()) {
      walk(scope, pointer, file, choicesById, findings);
    }
  }

  private Map<String, Set<String>> collectChoiceUnion(JsonNode template) {
    Map<String, Set<String>> result = new HashMap<>();
    JsonNode props = template.path(ElementTemplate.PROPERTIES);
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path(ElementTemplate.ID);
      JsonNode choicesNode = prop.path(ElementTemplate.CHOICES);
      if (!idNode.isTextual() || !choicesNode.isArray()) {
        continue;
      }
      Set<String> choices = result.computeIfAbsent(idNode.asText(), k -> new HashSet<>());
      for (JsonNode c : choicesNode) {
        JsonNode v = c.path(ElementTemplate.VALUE);
        if (v.isTextual()) {
          choices.add(v.asText());
        }
      }
    }
    return result;
  }

  private void walk(
      JsonNode node,
      String pointer,
      Path file,
      Map<String, Set<String>> choicesById,
      List<Finding> findings) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        if (ElementTemplate.CONFIGURATION_TEMPLATES.equals(entry.getKey())) {
          continue; // validated in its own scope by apply(), not the host's
        }
        String childPointer = pointer + "/" + JsonPointers.escape(entry.getKey());
        if (ElementTemplate.CONDITION.equals(entry.getKey()) && entry.getValue().isObject()) {
          checkCondition(entry.getValue(), childPointer, file, choicesById, findings);
        }
        walk(entry.getValue(), childPointer, file, choicesById, findings);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), pointer + "/" + i, file, choicesById, findings);
      }
    }
  }

  private void checkCondition(
      JsonNode condition,
      String conditionPointer,
      Path file,
      Map<String, Set<String>> choicesById,
      List<Finding> findings) {
    JsonNode propertyRef = condition.path(ElementTemplate.PROPERTY);
    if (propertyRef.isTextual()) {
      String referencedId = propertyRef.asText();
      Set<String> choices = choicesById.get(referencedId);
      if (choices != null && !choices.isEmpty()) {
        JsonNode equals = condition.path(ElementTemplate.EQUALS);
        if (equals.isTextual() && !choices.contains(equals.asText())) {
          findings.add(
              Finding.error(
                  file,
                  conditionPointer + "/equals",
                  id(),
                  "Condition value \""
                      + equals.asText()
                      + "\" is not a declared choice of property \""
                      + referencedId
                      + "\"."));
        }
        JsonNode oneOf = condition.path(ElementTemplate.ONE_OF);
        if (oneOf.isArray()) {
          for (int i = 0; i < oneOf.size(); i++) {
            JsonNode v = oneOf.get(i);
            if (v.isTextual() && !choices.contains(v.asText())) {
              findings.add(
                  Finding.error(
                      file,
                      conditionPointer + "/oneOf/" + i,
                      id(),
                      "Condition value \""
                          + v.asText()
                          + "\" is not a declared choice of property \""
                          + referencedId
                          + "\"."));
            }
          }
        }
      }
    }

    JsonNode allMatch = condition.path(ElementTemplate.ALL_MATCH);
    if (allMatch.isArray()) {
      for (int i = 0; i < allMatch.size(); i++) {
        JsonNode sub = allMatch.get(i);
        if (sub.isObject()) {
          checkCondition(sub, conditionPointer + "/allMatch/" + i, file, choicesById, findings);
        }
      }
    }
  }
}
