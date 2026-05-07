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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For every {@code .../element-templates/hybrid/<base>-hybrid.json}, the non-hybrid sibling at
 * {@code .../element-templates/<base>.json} (or, where the file naming uses a {@code hybrid-}
 * prefix as well, with that prefix stripped) must exist and declare the same set of {@code
 * properties[].id} and {@code groups[].id}. Catches drift where one side gains/loses a property the
 * other doesn't.
 */
public class HybridParityRule implements MultiFileRule {

  public static final String ID = "hybrid-parity";

  /** Properties / groups that are by design present only in the hybrid sibling (self-managed). */
  private static final Set<String> HYBRID_ONLY_ALLOWED =
      Set.of("taskDefinitionType", "connectorType");

  /** Properties / groups that are by design present only in the non-hybrid sibling (SaaS). */
  private static final Set<String> NON_HYBRID_ONLY_ALLOWED =
      Set.of(
          "deduplication",
          "deduplicationId",
          "deduplicationModeAuto",
          "deduplicationModeManual",
          "deduplicationModeManualFlag",
          "consumeUnmatchedEvents",
          "messageTtl");

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Map<Path, JsonNode> templates) {
    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path hybrid = entry.getKey();
      if (!isHybridTemplate(hybrid) || TemplateFinder.isVersioned(hybrid)) {
        continue;
      }
      Path sibling = findSibling(hybrid, templates.keySet());
      if (sibling == null) {
        findings.add(
            Finding.error(
                hybrid,
                "/",
                ID,
                "Hybrid template has no matching non-hybrid sibling at "
                    + expectedSiblingHint(hybrid)
                    + "."));
        continue;
      }
      JsonNode hybridTemplate = entry.getValue();
      JsonNode mainTemplate = templates.get(sibling);
      compareIdSet(hybrid, hybridTemplate, mainTemplate, sibling, "properties", findings);
      compareIdSet(hybrid, hybridTemplate, mainTemplate, sibling, "groups", findings);
    }
    return findings;
  }

  private void compareIdSet(
      Path hybrid,
      JsonNode hybridTemplate,
      JsonNode mainTemplate,
      Path mainPath,
      String collectionField,
      List<Finding> findings) {
    Set<String> hybridIds = collectIds(hybridTemplate, collectionField);
    Set<String> mainIds = collectIds(mainTemplate, collectionField);

    Set<String> onlyInHybrid = new LinkedHashSet<>(hybridIds);
    onlyInHybrid.removeAll(mainIds);
    onlyInHybrid.removeIf(HYBRID_ONLY_ALLOWED::contains);
    Set<String> onlyInMain = new LinkedHashSet<>(mainIds);
    onlyInMain.removeAll(hybridIds);
    onlyInMain.removeIf(NON_HYBRID_ONLY_ALLOWED::contains);

    if (!onlyInHybrid.isEmpty()) {
      findings.add(
          Finding.error(
              hybrid,
              "/" + collectionField,
              ID,
              "Hybrid declares "
                  + collectionField
                  + " not present in non-hybrid sibling ("
                  + mainPath.getFileName()
                  + "): "
                  + onlyInHybrid
                  + "."));
    }
    if (!onlyInMain.isEmpty()) {
      findings.add(
          Finding.error(
              hybrid,
              "/" + collectionField,
              ID,
              "Non-hybrid sibling ("
                  + mainPath.getFileName()
                  + ") declares "
                  + collectionField
                  + " not present in hybrid: "
                  + onlyInMain
                  + "."));
    }
  }

  private Set<String> collectIds(JsonNode template, String field) {
    Set<String> ids = new LinkedHashSet<>();
    JsonNode arr = template.path(field);
    if (arr.isArray()) {
      for (JsonNode item : arr) {
        JsonNode id = item.path("id");
        if (id.isTextual()) {
          ids.add(id.asText());
        }
      }
    }
    return ids;
  }

  private static boolean isHybridTemplate(Path path) {
    String name = path.getFileName().toString();
    if (!name.endsWith("-hybrid.json")) {
      return false;
    }
    for (Path segment : path) {
      if ("hybrid".equals(segment.toString())) {
        return true;
      }
    }
    return false;
  }

  private static Path findSibling(Path hybrid, Set<Path> known) {
    Path elementTemplatesDir = hybrid.getParent().getParent();
    String name = hybrid.getFileName().toString();
    String base = name.substring(0, name.length() - "-hybrid.json".length());
    Path direct = elementTemplatesDir.resolve(base + ".json");
    if (known.contains(direct)) {
      return direct;
    }
    if (base.startsWith("hybrid-")) {
      Path stripped = elementTemplatesDir.resolve(base.substring("hybrid-".length()) + ".json");
      if (known.contains(stripped)) {
        return stripped;
      }
    }
    return null;
  }

  private static String expectedSiblingHint(Path hybrid) {
    Path elementTemplatesDir = hybrid.getParent().getParent();
    String name = hybrid.getFileName().toString();
    String base = name.substring(0, name.length() - "-hybrid.json".length());
    return elementTemplatesDir.resolve(base + ".json").toString();
  }
}
