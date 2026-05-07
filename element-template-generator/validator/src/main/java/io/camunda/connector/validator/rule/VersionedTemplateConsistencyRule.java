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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For each {@code .../element-templates/versioned/<base>-<N>.json}, the file's {@code version}
 * field must equal {@code N} (the suffix in the filename). Catches snapshots whose version field
 * drifted from or never matched the filename suffix.
 *
 * <p>Note: this rule does <em>not</em> require {@code id} stability across versioned snapshots. The
 * team intentionally bumps the id suffix ({@code .v0} → {@code .v1}) on breaking template changes
 * so old instances do not auto-upgrade.
 *
 * <p>Snapshots whose filename suffix starts with {@code 0} (e.g. {@code -0}, {@code -01}, {@code
 * -02}) are pre-versioning artifacts written before the {@code version} field was tracked. For
 * those, a missing {@code version} field is tolerated; if present, it must still match.
 */
public class VersionedTemplateConsistencyRule implements MultiFileRule {

  public static final String ID = "versioned-template-consistency";

  private static final Pattern VERSIONED_NAME = Pattern.compile("^(.+)-(\\d+)\\.json$");

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Map<Path, JsonNode> templates) {
    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path path = entry.getKey();
      if (!TemplateFinder.isVersioned(path)) {
        continue;
      }
      Matcher m = VERSIONED_NAME.matcher(path.getFileName().toString());
      if (!m.matches()) {
        findings.add(
            Finding.error(
                path,
                "/",
                ID,
                "Versioned template filename does not follow the {base}-{version}.json pattern."));
        continue;
      }
      String filenameVersionStr = m.group(2);
      int filenameVersion;
      try {
        filenameVersion = Integer.parseInt(filenameVersionStr);
      } catch (NumberFormatException e) {
        findings.add(
            Finding.error(
                path,
                "/",
                ID,
                "Versioned template filename suffix \""
                    + filenameVersionStr
                    + "\" is not a valid version number."));
        continue;
      }
      boolean preVersioningSnapshot = filenameVersionStr.startsWith("0");

      JsonNode template = entry.getValue();
      JsonNode versionNode = template.path("version");
      boolean versionMissing = !versionNode.isNumber();
      if (versionMissing && preVersioningSnapshot) {
        continue;
      }
      if (versionMissing || versionNode.asInt() != filenameVersion) {
        findings.add(
            Finding.error(
                path,
                "/version",
                ID,
                "Filename suffix says version "
                    + filenameVersion
                    + " but the template declares "
                    + versionNode.asText("<missing>")
                    + "."));
      }
    }
    return findings;
  }
}
