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
package io.camunda.connector.validator.core;

import java.nio.file.Path;
import java.util.Set;

/**
 * Connectors that are exempt from the operations-metadata rules (the {@code steps} / {@code
 * presets} contract). Other rules still run on these connectors.
 */
public final class OperationMetadataIgnoreList {

  private OperationMetadataIgnoreList() {}

  public static final Set<String> ENTRIES =
      Set.of(
          "agentic-ai",
          "asana",
          "automation-anywhere",
          "aws",
          "blue-prism",
          "box",
          "camunda-message",
          "csv",
          "easy-post",
          "email",
          "embeddings-vector-database",
          "github",
          "gitlab",
          "google",
          "google-maps-platform",
          "http",
          "hubspot",
          "hugging-face",
          "idp-extraction",
          "jdbc",
          "kafka",
          "microsoft",
          "openai",
          "operate",
          "power-automate",
          "rabbitmq",
          "rpa",
          "salesforce",
          "sendgrid",
          "servicenow",
          "slack",
          "soap",
          "twilio",
          "uipath",
          "webhook",
          "whatsapp");

  /**
   * Returns true if the given template file lives under any connector directory on the ignore list.
   * Matching is by directory name only (any path segment), the same convention as the existing
   * {@code --skip-connector} flag.
   */
  public static boolean isIgnored(Path templateFile) {
    if (templateFile == null) {
      return false;
    }
    for (Path segment : templateFile) {
      if (ENTRIES.contains(segment.toString())) {
        return true;
      }
    }
    return false;
  }
}
