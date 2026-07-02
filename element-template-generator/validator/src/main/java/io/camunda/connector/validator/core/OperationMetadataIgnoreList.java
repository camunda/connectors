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
          "aws-base",
          "aws-bedrock-agentcore-runtime",
          "aws-bedrock-codeinterpreter",
          "aws-bedrock-knowledgebase",
          "aws-eventbridge",
          "aws-lambda",
          "aws-sagemaker",
          "aws-sns",
          "aws-sqs",
          "aws-textract",
          "azure-open-ai",
          "blue-prism",
          "easy-post",
          "email-inbound",
          "email-message-start-event",
          "github",
          "gitlab",
          "google-drive",
          "google-gemini",
          "google-maps-platform",
          "http",
          "hubspot",
          "hugging-face",
          "hybrid-email-message-start-event",
          "idp-extraction",
          "jdbc",
          "kafka",
          "mail",
          "openai",
          "operate",
          "orchestration",
          "power-automate",
          "rabbitmq",
          "rpa",
          "salesforce",
          "sendgrid",
          "servicenow",
          "slack-inbound",
          "soap",
          "twilio",
          "uipath",
          "webhook",
          "whatsapp");

  /**
   * Returns true if the given template file lives under any connector directory on the ignore list,
   * or if its filename (without extension) equals or starts with an entry followed by a hyphen. The
   * filename-prefix check handles inbound connectors whose files share a directory with outbound
   * templates (e.g. {@code slack-inbound-*.json} in the {@code slack} directory).
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
    Path fileNamePath = templateFile.getFileName();
    if (fileNamePath != null) {
      String base = fileNamePath.toString().replaceAll("\\.json$", "");
      for (String entry : ENTRIES) {
        if (base.equals(entry) || base.startsWith(entry + "-")) {
          return true;
        }
      }
    }
    return false;
  }
}
