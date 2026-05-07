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
 * Each property's {@code group} must reference an existing {@code groups[].id}. Catches typos that
 * silently hide a property's section header in the modeler.
 */
public class GroupTargetExistsRule implements Rule {

  public static final String ID = "group-target-exists";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    Set<String> groupIds = new HashSet<>();
    JsonNode groups = template.path("groups");
    if (groups.isArray()) {
      for (JsonNode g : groups) {
        JsonNode idNode = g.path("id");
        if (idNode.isTextual()) {
          groupIds.add(idNode.asText());
        }
      }
    }

    List<Finding> findings = new ArrayList<>();
    JsonNode properties = template.path("properties");
    if (!properties.isArray()) {
      return findings;
    }
    for (int i = 0; i < properties.size(); i++) {
      JsonNode property = properties.get(i);
      JsonNode groupRef = property.path("group");
      if (!groupRef.isTextual()) {
        continue;
      }
      String referenced = groupRef.asText();
      if (!groupIds.contains(referenced)) {
        findings.add(
            Finding.error(
                file,
                "/properties/" + i + "/group",
                ID,
                "Property references group \""
                    + referenced
                    + "\" which is not declared in groups[]."));
      }
    }
    return findings;
  }
}
