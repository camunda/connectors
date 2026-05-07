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
 */
public class ConditionValueInChoicesRule implements Rule {

  public static final String ID = "condition-value-in-choices";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    Map<String, Set<String>> choicesById = collectChoiceUnion(template);
    if (choicesById.isEmpty()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    walk(template, "", file, choicesById, findings);
    return findings;
  }

  private Map<String, Set<String>> collectChoiceUnion(JsonNode template) {
    Map<String, Set<String>> result = new HashMap<>();
    JsonNode props = template.path("properties");
    if (!props.isArray()) {
      return result;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path("id");
      JsonNode choicesNode = prop.path("choices");
      if (!idNode.isTextual() || !choicesNode.isArray()) {
        continue;
      }
      Set<String> choices = result.computeIfAbsent(idNode.asText(), k -> new HashSet<>());
      for (JsonNode c : choicesNode) {
        JsonNode v = c.path("value");
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
        String childPointer = pointer + "/" + JsonPointers.escape(entry.getKey());
        if ("condition".equals(entry.getKey()) && entry.getValue().isObject()) {
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
    JsonNode propertyRef = condition.path("property");
    if (propertyRef.isTextual()) {
      String referencedId = propertyRef.asText();
      Set<String> choices = choicesById.get(referencedId);
      if (choices != null && !choices.isEmpty()) {
        JsonNode equals = condition.path("equals");
        if (equals.isTextual() && !choices.contains(equals.asText())) {
          findings.add(
              Finding.error(
                  file,
                  conditionPointer + "/equals",
                  ID,
                  "Condition value \""
                      + equals.asText()
                      + "\" is not a declared choice of property \""
                      + referencedId
                      + "\"."));
        }
        JsonNode oneOf = condition.path("oneOf");
        if (oneOf.isArray()) {
          for (int i = 0; i < oneOf.size(); i++) {
            JsonNode v = oneOf.get(i);
            if (v.isTextual() && !choices.contains(v.asText())) {
              findings.add(
                  Finding.error(
                      file,
                      conditionPointer + "/oneOf/" + i,
                      ID,
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

    JsonNode allMatch = condition.path("allMatch");
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
