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
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Caps the visible length of a property's or group's {@code description}, which is always shown
 * beneath the field in the Modeler. {@code tooltip} is exempt: it appears only on hover, so its
 * length is a prose guideline rather than an enforced limit. HTML tags are removed without leaving
 * a space, so markup such as {@code <a>} links and {@code <br>} does not count against the limit,
 * and each entity such as {@code &amp;} counts as the one character the Modeler shows. Runs only on
 * the connectors in {@link HelpTextRule#CONNECTORS}.
 */
public class MaxDescriptionLengthRule implements Rule {

  static final int MAX_LENGTH = 100;

  private static final List<String> SECTIONS =
      List.of(ElementTemplate.PROPERTIES, ElementTemplate.GROUPS);
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern ENTITY = Pattern.compile("&[a-zA-Z]+;|&#\\d+;");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (!HelpTextRule.appliesTo(file)) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (String section : SECTIONS) {
      JsonNode items = template.path(section);
      for (int i = 0; i < items.size(); i++) {
        JsonNode text = items.get(i).path(ElementTemplate.DESCRIPTION);
        if (!text.isTextual()) {
          continue;
        }
        int length = visibleLength(text.asText());
        if (length > MAX_LENGTH) {
          String pointer = "/" + section + "/" + i + "/" + ElementTemplate.DESCRIPTION;
          findings.add(
              Finding.error(
                  file,
                  pointer,
                  id(),
                  "Description is "
                      + length
                      + " characters; keep it at most "
                      + MAX_LENGTH
                      + ". Move detail into the tooltip or the linked documentation."));
        }
      }
    }
    return findings;
  }

  private static int visibleLength(String text) {
    String stripped = TAG.matcher(text).replaceAll("");
    stripped = ENTITY.matcher(stripped).replaceAll("_");
    return WHITESPACE.matcher(stripped).replaceAll(" ").strip().length();
  }
}
