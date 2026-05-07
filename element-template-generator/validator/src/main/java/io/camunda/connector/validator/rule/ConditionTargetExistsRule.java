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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every property referenced from a {@code condition} must exist in the template's top-level {@code
 * properties[]}.
 *
 * <p>Supported condition shapes (matching the upstream Camunda element-template schema):
 *
 * <ul>
 *   <li>Simple: {@code { "property": "X", "equals": Y }} or {@code { "property": "X", "oneOf": [
 *       ... ] }}
 *   <li>Compound: {@code { "allMatch": [ <simple>, <simple>, ... ] }}
 * </ul>
 */
public class ConditionTargetExistsRule implements Rule {

  public static final String ID = "condition-target-exists";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    Set<String> propertyIds = collectPropertyIds(template);
    List<Finding> findings = new ArrayList<>();
    walk(template, "", file, propertyIds, findings);
    return findings;
  }

  private Set<String> collectPropertyIds(JsonNode template) {
    Set<String> ids = new HashSet<>();
    JsonNode props = template.path("properties");
    if (!props.isArray()) {
      return ids;
    }
    for (JsonNode prop : props) {
      JsonNode idNode = prop.path("id");
      if (idNode.isTextual()) {
        ids.add(idNode.asText());
      }
    }
    return ids;
  }

  private void walk(
      JsonNode node, String pointer, Path file, Set<String> propertyIds, List<Finding> findings) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String childPointer = pointer + "/" + JsonPointers.escape(entry.getKey());
        if ("condition".equals(entry.getKey()) && entry.getValue().isObject()) {
          checkCondition(entry.getValue(), childPointer, file, propertyIds, findings);
        }
        walk(entry.getValue(), childPointer, file, propertyIds, findings);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), pointer + "/" + i, file, propertyIds, findings);
      }
    }
  }

  private void checkCondition(
      JsonNode condition,
      String conditionPointer,
      Path file,
      Set<String> propertyIds,
      List<Finding> findings) {
    JsonNode propertyRef = condition.path("property");
    if (propertyRef.isTextual()) {
      String referencedId = propertyRef.asText();
      if (!propertyIds.contains(referencedId)) {
        findings.add(
            Finding.error(
                file,
                conditionPointer + "/property",
                ID,
                "Condition references property \""
                    + referencedId
                    + "\" which does not exist in this template."));
      }
    }

    JsonNode allMatch = condition.path("allMatch");
    if (allMatch.isArray()) {
      for (int i = 0; i < allMatch.size(); i++) {
        JsonNode sub = allMatch.get(i);
        if (sub.isObject()) {
          checkCondition(sub, conditionPointer + "/allMatch/" + i, file, propertyIds, findings);
        }
      }
    }
  }
}
