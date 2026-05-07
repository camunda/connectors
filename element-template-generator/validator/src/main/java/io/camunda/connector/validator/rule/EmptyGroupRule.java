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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Each declared {@code groups[].id} must be referenced by at least one property's {@code group}.
 */
public class EmptyGroupRule implements Rule {

  public static final String ID = "empty-group";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    JsonNode groups = template.path("groups");
    if (!groups.isArray()) {
      return List.of();
    }
    Set<String> referenced = new HashSet<>();
    JsonNode properties = template.path("properties");
    if (properties.isArray()) {
      for (JsonNode prop : properties) {
        JsonNode g = prop.path("group");
        if (g.isTextual()) {
          referenced.add(g.asText());
        }
      }
    }

    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < groups.size(); i++) {
      JsonNode idNode = groups.get(i).path("id");
      if (!idNode.isTextual()) {
        continue;
      }
      String groupId = idNode.asText();
      if (!referenced.contains(groupId)) {
        findings.add(
            Finding.error(
                file,
                "/groups/" + i + "/id",
                ID,
                "Group \"" + groupId + "\" is declared but no property references it."));
      }
    }
    return findings;
  }
}
