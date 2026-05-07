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
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Within a single template, no two entries in {@code properties[]} may share the same {@code id} —
 * with one exception: duplicate-id properties whose visibility {@code condition}s are pairwise
 * mutually exclusive are tolerated. That pattern is used in templates where a parent dropdown (e.g.
 * {@code resourceType} / {@code operationType}) selects which of several variants is shown, and
 * only one is ever live at a time.
 *
 * <p>"Mutually exclusive" here means: each pair of conditions targets the same {@code property},
 * with disjoint {@code equals}/{@code oneOf} value sets — or any sub-condition pair inside their
 * {@code allMatch} compounds is mutually exclusive. Properties without a condition are always live,
 * so any duplicate involving an unconditional property is still flagged.
 */
public class UniquePropertyIdRule implements Rule {

  public static final String ID = "unique-property-id";

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
    Map<String, List<Integer>> indicesById = new LinkedHashMap<>();
    for (int i = 0; i < properties.size(); i++) {
      JsonNode idNode = properties.get(i).path("id");
      if (!idNode.isTextual()) {
        continue;
      }
      indicesById.computeIfAbsent(idNode.asText(), k -> new ArrayList<>()).add(i);
    }

    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> entry : indicesById.entrySet()) {
      List<Integer> indices = entry.getValue();
      if (indices.size() < 2) {
        continue;
      }
      if (allPairsMutuallyExclusive(indices, properties)) {
        continue;
      }
      String propertyId = entry.getKey();
      int firstIndex = indices.get(0);
      for (int k = 1; k < indices.size(); k++) {
        int duplicateIndex = indices.get(k);
        findings.add(
            Finding.error(
                file,
                "/properties/" + duplicateIndex + "/id",
                ID,
                "Property id \""
                    + propertyId
                    + "\" is already declared at /properties/"
                    + firstIndex
                    + "/id."));
      }
    }
    return findings;
  }

  private static boolean allPairsMutuallyExclusive(List<Integer> indices, JsonNode properties) {
    for (int i = 0; i < indices.size(); i++) {
      JsonNode condA = properties.get(indices.get(i)).path("condition");
      if (!condA.isObject()) {
        return false;
      }
      for (int j = i + 1; j < indices.size(); j++) {
        JsonNode condB = properties.get(indices.get(j)).path("condition");
        if (!condB.isObject()) {
          return false;
        }
        if (!mutuallyExclusive(condA, condB)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean mutuallyExclusive(JsonNode c1, JsonNode c2) {
    JsonNode p1 = c1.path("property");
    JsonNode p2 = c2.path("property");
    if (p1.isTextual() && p2.isTextual() && p1.asText().equals(p2.asText())) {
      Set<String> v1 = collectSimpleValues(c1);
      Set<String> v2 = collectSimpleValues(c2);
      if (!v1.isEmpty() && !v2.isEmpty() && Collections.disjoint(v1, v2)) {
        return true;
      }
    }
    JsonNode am1 = c1.path("allMatch");
    if (am1.isArray()) {
      for (JsonNode sub : am1) {
        if (sub.isObject() && mutuallyExclusive(sub, c2)) {
          return true;
        }
      }
    }
    JsonNode am2 = c2.path("allMatch");
    if (am2.isArray()) {
      for (JsonNode sub : am2) {
        if (sub.isObject() && mutuallyExclusive(c1, sub)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<String> collectSimpleValues(JsonNode condition) {
    Set<String> values = new HashSet<>();
    JsonNode equals = condition.path("equals");
    if (equals.isTextual()) {
      values.add(equals.asText());
    }
    JsonNode oneOf = condition.path("oneOf");
    if (oneOf.isArray()) {
      for (JsonNode v : oneOf) {
        if (v.isTextual()) {
          values.add(v.asText());
        }
      }
    }
    return values;
  }
}
