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
import java.util.List;
import java.util.Map;

/**
 * Any property B whose subtree contains a {@code condition} referencing property A must appear
 * after A in the {@code properties[]} array. Includes conditions nested inside {@code choices[*]}
 * and inside {@code allMatch} compounds.
 *
 * <p>This catches forward-references that are technically not rejected by the schema but break the
 * natural fill-in order: the user can't pick a value for B before they've picked A. It also
 * naturally enforces "step 1 discriminator before step 2 discriminator" — the step-2 property's
 * condition references step 1, so the ordering check applies.
 *
 * <p>References to property ids that don't exist in the template are ignored here (they're caught
 * by {@code condition-target-exists}). When the referenced id has multiple occurrences (the
 * mutually-exclusive switching pattern), the rule uses the first occurrence's index.
 */
public class ConditionPropertyOrderRule implements Rule {

  public static final String ID = "condition-property-order";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    JsonNode properties = template.path("properties");
    if (!properties.isArray()) {
      return List.of();
    }
    Map<String, Integer> firstIndexById = new HashMap<>();
    for (int i = 0; i < properties.size(); i++) {
      JsonNode idNode = properties.get(i).path("id");
      if (idNode.isTextual()) {
        firstIndexById.putIfAbsent(idNode.asText(), i);
      }
    }
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < properties.size(); i++) {
      walk(properties.get(i), "/properties/" + i, i, firstIndexById, file, findings);
    }
    return findings;
  }

  private void walk(
      JsonNode node,
      String pointer,
      int ownerIndex,
      Map<String, Integer> firstIndexById,
      Path file,
      List<Finding> findings) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        String childPointer = pointer + "/" + JsonPointers.escape(entry.getKey());
        if ("condition".equals(entry.getKey()) && entry.getValue().isObject()) {
          checkCondition(
              entry.getValue(), childPointer, ownerIndex, firstIndexById, file, findings);
        }
        walk(entry.getValue(), childPointer, ownerIndex, firstIndexById, file, findings);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), pointer + "/" + i, ownerIndex, firstIndexById, file, findings);
      }
    }
  }

  private void checkCondition(
      JsonNode condition,
      String conditionPointer,
      int ownerIndex,
      Map<String, Integer> firstIndexById,
      Path file,
      List<Finding> findings) {
    JsonNode propertyRef = condition.path("property");
    if (propertyRef.isTextual()) {
      String referencedId = propertyRef.asText();
      Integer referencedIndex = firstIndexById.get(referencedId);
      if (referencedIndex != null && referencedIndex >= ownerIndex) {
        String reason =
            referencedIndex == ownerIndex
                ? "the property itself"
                : "a property at index " + referencedIndex + " (after this one)";
        findings.add(
            Finding.error(
                file,
                conditionPointer + "/property",
                ID,
                "Condition references \""
                    + referencedId
                    + "\" — "
                    + reason
                    + ". Conditions must reference properties that appear earlier in properties[]."));
      }
    }
    JsonNode allMatch = condition.path("allMatch");
    if (allMatch.isArray()) {
      for (int j = 0; j < allMatch.size(); j++) {
        JsonNode sub = allMatch.get(j);
        if (sub.isObject()) {
          checkCondition(
              sub, conditionPointer + "/allMatch/" + j, ownerIndex, firstIndexById, file, findings);
        }
      }
    }
  }
}
