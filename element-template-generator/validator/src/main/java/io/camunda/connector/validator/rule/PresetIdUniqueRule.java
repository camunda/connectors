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
import java.util.List;
import java.util.Set;

/** No two entries in {@code presets[]} may share an {@code id}. */
public class PresetIdUniqueRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file)) {
      return List.of();
    }
    JsonNode presets = template.path(ElementTemplate.PRESETS);
    if (!presets.isArray()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < presets.size(); i++) {
      JsonNode entry = presets.get(i);
      JsonNode idNode = entry.path(ElementTemplate.ID);
      if (!idNode.isTextual()) {
        continue;
      }
      String idValue = idNode.asText();
      if (!seen.add(idValue)) {
        findings.add(
            Finding.error(
                file, "/presets/" + i + "/id", id(), "Duplicate preset id \"" + idValue + "\"."));
      }
    }
    return findings;
  }
}
