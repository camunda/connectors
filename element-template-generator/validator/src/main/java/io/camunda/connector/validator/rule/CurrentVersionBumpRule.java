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
import io.camunda.connector.validator.core.MultiFileRule;
import io.camunda.connector.validator.core.TemplateFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For each non-versioned template, locate the highest-versioned snapshot in the same connector's
 * {@code element-templates/} subtree that shares the same {@code id}. If one exists, the current
 * template's {@code version} field must be exactly that snapshot's version plus one.
 *
 * <p>If no versioned snapshot shares the same id, the rule is silent — this naturally handles
 * intentional id renames ({@code .v0} → {@code .v1}) where the current template starts a new
 * lineage with no historical sibling under the new id.
 */
public class CurrentVersionBumpRule implements MultiFileRule {

  public static final String ID = "current-version-bump";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Map<Path, JsonNode> templates) {
    Map<String, Integer> highestSnapshotByScope = new HashMap<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path path = entry.getKey();
      if (!TemplateFinder.isVersioned(path)) {
        continue;
      }
      String scopeKey = scopeKey(path, entry.getValue());
      if (scopeKey == null) {
        continue;
      }
      JsonNode versionNode = entry.getValue().path("version");
      if (!versionNode.isNumber()) {
        continue;
      }
      highestSnapshotByScope.merge(scopeKey, versionNode.asInt(), Math::max);
    }

    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path path = entry.getKey();
      if (TemplateFinder.isVersioned(path)) {
        continue;
      }
      String scopeKey = scopeKey(path, entry.getValue());
      if (scopeKey == null) {
        continue;
      }
      Integer maxSnapshot = highestSnapshotByScope.get(scopeKey);
      if (maxSnapshot == null) {
        continue;
      }
      int expected = maxSnapshot + 1;
      JsonNode versionNode = entry.getValue().path("version");
      String tmplId = entry.getValue().path("id").asText();
      if (!versionNode.isNumber()) {
        findings.add(
            Finding.error(
                path,
                "/version",
                ID,
                "Current template missing 'version' field; highest versioned snapshot with id \""
                    + tmplId
                    + "\" is "
                    + maxSnapshot
                    + ", expected "
                    + expected
                    + "."));
        continue;
      }
      int current = versionNode.asInt();
      if (current != expected) {
        findings.add(
            Finding.error(
                path,
                "/version",
                ID,
                "Current template version is "
                    + current
                    + " but highest versioned snapshot with id \""
                    + tmplId
                    + "\" is "
                    + maxSnapshot
                    + "; expected "
                    + expected
                    + "."));
      }
    }
    return findings;
  }

  private static String scopeKey(Path path, JsonNode template) {
    String tmplId = template.path("id").asText("");
    if (tmplId.isEmpty()) {
      return null;
    }
    Path elementTemplatesDir = elementTemplatesDir(path);
    if (elementTemplatesDir == null) {
      return null;
    }
    return elementTemplatesDir + "::" + tmplId;
  }

  private static Path elementTemplatesDir(Path path) {
    Path p = path.getParent();
    while (p != null) {
      Path name = p.getFileName();
      if (name != null && "element-templates".equals(name.toString())) {
        return p;
      }
      p = p.getParent();
    }
    return null;
  }
}
