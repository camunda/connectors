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
import io.camunda.connector.validator.core.ConnectorNameOverrides;
import io.camunda.connector.validator.core.ElementTemplate;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * When a native operation (leaf step) declares a {@code description}, that description must
 * reference the connector name so users can tell apart operations with the same name across
 * different connectors (e.g. "Upload file").
 *
 * <p>The connector name is derived from the template's top-level {@code name} by stripping a
 * trailing {@code "Outbound Connector"}/{@code "Connector"} and any parenthetical suffix (e.g.
 * {@code "Asana Outbound Connector"} &rarr; {@code "Asana"}, {@code "CSV Connector"} &rarr; {@code
 * "CSV"}). Matching is lenient and case-insensitive: the description passes if it contains any
 * significant word of the derived name, so a multi-word name like {@code "WhatsApp Business"} is
 * satisfied by a description mentioning "WhatsApp".
 *
 * <p>{@code description} remains optional — steps without one are not flagged.
 */
public class OperationDescriptionConnectorNameRule implements Rule {

  private static final Pattern PARENTHETICAL_SUFFIX = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
  private static final Pattern CONNECTOR_SUFFIX =
      Pattern.compile("(?i)\\s*(outbound\\s+)?connector\\s*$");

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file, template)) {
      return List.of();
    }
    JsonNode steps = template.path(ElementTemplate.STEPS);
    if (!steps.isArray()) {
      return List.of();
    }
    String connectorName = connectorName(file, template);
    List<String> nameWords = significantWords(connectorName);
    if (nameWords.isEmpty()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      walk(steps.get(i), "/steps/" + i, file, nameWords, connectorName, findings);
    }
    return findings;
  }

  private void walk(
      JsonNode node,
      String pointer,
      Path file,
      List<String> nameWords,
      String connectorName,
      List<Finding> findings) {
    if (!node.isObject()) {
      return;
    }
    JsonNode children = node.path(ElementTemplate.STEPS);
    if (children.isArray()) {
      for (int i = 0; i < children.size(); i++) {
        walk(children.get(i), pointer + "/steps/" + i, file, nameWords, connectorName, findings);
      }
      return;
    }
    JsonNode description = node.path(ElementTemplate.DESCRIPTION);
    if (!description.isTextual() || description.asText().isBlank()) {
      return;
    }
    String descriptionLower = description.asText().toLowerCase();
    boolean referencesName = nameWords.stream().anyMatch(descriptionLower::contains);
    if (!referencesName) {
      findings.add(
          Finding.error(
              file,
              pointer + "/" + ElementTemplate.DESCRIPTION,
              id(),
              "Leaf step description must reference the connector name \""
                  + connectorName
                  + "\"."));
    }
  }

  private static String connectorName(Path file, JsonNode template) {
    Optional<String> override = ConnectorNameOverrides.forFile(file);
    if (override.isPresent()) {
      return override.get();
    }
    JsonNode name = template.path(ElementTemplate.NAME);
    if (!name.isTextual()) {
      return "";
    }
    String stripped = PARENTHETICAL_SUFFIX.matcher(name.asText()).replaceAll("");
    return CONNECTOR_SUFFIX.matcher(stripped).replaceAll("").trim();
  }

  private static List<String> significantWords(String connectorName) {
    if (connectorName.isBlank()) {
      return List.of();
    }
    return Arrays.stream(connectorName.toLowerCase().split("\\s+"))
        .filter(word -> word.length() >= 2)
        .toList();
  }
}
