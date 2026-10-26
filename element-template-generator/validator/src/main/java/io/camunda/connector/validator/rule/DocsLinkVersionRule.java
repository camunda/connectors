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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links to docs.camunda.io must pin a released {@code <major>.<minor>}. The release bumper only
 * rewrites links that already carry a version, so an unversioned or {@code /next/} link is never
 * corrected once introduced.
 */
public class DocsLinkVersionRule implements Rule {

  private static final Pattern UNVERSIONED_LINK =
      Pattern.compile("https://docs\\.camunda\\.io/docs/(?![0-9]+\\.[0-9]+/)[^\\s\"'<>]*");

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    List<Finding> findings = new ArrayList<>();
    walk(template, "", file, findings);
    return findings;
  }

  private void walk(JsonNode node, String pointer, Path file, List<Finding> findings) {
    if (node.isTextual()) {
      Matcher matcher = UNVERSIONED_LINK.matcher(node.asText());
      if (matcher.find()) {
        findings.add(
            Finding.error(
                file,
                pointer,
                id(),
                "Pin the current released minor in \""
                    + matcher.group()
                    + "\", for example https://docs.camunda.io/docs/8.8/..."));
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), pointer + "/" + i, file, findings);
      }
    } else if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        walk(field.getValue(), pointer + "/" + JsonPointers.escape(field.getKey()), file, findings);
      }
    }
  }
}
