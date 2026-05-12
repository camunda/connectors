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
import io.camunda.connector.validator.core.MultiFileRule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * No two element templates — current or versioned snapshot — may share both the same {@code id}
 * <em>and</em> the same {@code version}. The Camunda Web Modeler dedupes templates by id+version
 * when importing (highest-version-wins), so duplicates collide silently — masking accidental
 * copies, snapshots that weren't promoted to a new version, or content changes that didn't bump the
 * version field.
 *
 * <p>Templates missing {@code id} or {@code version} are skipped (other rules flag those).
 */
public class UniqueIdVersionRule implements MultiFileRule {

  @Override
  public List<Finding> apply(Map<Path, JsonNode> templates) {
    Map<String, List<Path>> byKey = new LinkedHashMap<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      JsonNode template = entry.getValue();
      JsonNode idNode = template.path(ElementTemplate.ID);
      JsonNode versionNode = template.path(ElementTemplate.VERSION);
      if (!idNode.isTextual() || !versionNode.isNumber()) {
        continue;
      }
      String key = idNode.asText() + "@" + versionNode.asInt();
      byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
    }
    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<String, List<Path>> entry : byKey.entrySet()) {
      List<Path> paths = entry.getValue();
      if (paths.size() < 2) {
        continue;
      }
      String[] parts = entry.getKey().split("@", 2);
      String tmplId = parts[0];
      String version = parts[1];
      for (Path p : paths) {
        String others =
            paths.stream()
                .filter(q -> !q.equals(p))
                .map(Path::toString)
                .collect(Collectors.joining(", "));
        findings.add(
            Finding.error(
                p,
                "/",
                id(),
                "Duplicate id+version: id=\""
                    + tmplId
                    + "\", version="
                    + version
                    + " also declared in: "
                    + others));
      }
    }
    return findings;
  }
}
