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

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Connectors that are exempt from the operations-metadata rules (the {@code steps} / {@code
 * presets} contract). Other rules still run on these connectors.
 *
 * <p>Inbound connectors are detected automatically from the template's {@code elementType.value}
 * field and do not need to be listed here. This list covers outbound connectors that have not yet
 * added operations-metadata.
 */
public final class OperationMetadataIgnoreList {

  private OperationMetadataIgnoreList() {}

  private static final Set<String> INBOUND_ELEMENT_TYPES =
      Set.of(
          "bpmn:StartEvent",
          "bpmn:IntermediateCatchEvent",
          "bpmn:BoundaryEvent",
          "bpmn:ReceiveTask");

  /** Outbound connectors with a single fixed operation */
  private static final Set<String> CONNECTORS_WITH_ONLY_ONE_OPERATION =
      Set.of(
          "aws-bedrock-agentcore-runtime",
          "aws-bedrock-codeinterpreter",
          "aws-bedrock-knowledgebase",
          "aws-eventbridge",
          "aws-lambda",
          "aws-sagemaker",
          "aws-sns",
          "aws-sqs",
          "aws-textract",
          "google-gemini",
          "hugging-face",
          "jdbc",
          "kafka",
          "rabbitmq",
          "rpa",
          "sendgrid",
          "soap");

  /**
   * Connectors that are intentionally skipping the native-operations feature — either because they
   * have multiple operations that require more design work, or because they have no outbound
   * element templates at all (e.g. shared libraries).
   */
  private static final Set<String> CONNECTORS_SKIPPING_NATIVE_OPERATIONS_FEATURE =
      Set.of(
          "agentic-ai",
          "aws-base",
          "google-drive",
          "http",
          "idp-extraction",
          "operate", // deprecated
          "orchestration",
          "power-automate",
          "servicenow-flow-starter",
          "servicenow-incident");

  public static final Set<String> ENTRIES =
      Stream.of(CONNECTORS_WITH_ONLY_ONE_OPERATION, CONNECTORS_SKIPPING_NATIVE_OPERATIONS_FEATURE)
          .flatMap(Set::stream)
          .collect(Collectors.toUnmodifiableSet());

  /**
   * Returns true if the template is an inbound connector (detected from {@code elementType.value})
   * or if the template file lives under any connector directory on the ignore list, or if its
   * filename prefix matches an entry.
   */
  public static boolean isIgnored(Path templateFile, JsonNode template) {
    if (isInboundTemplate(template)) {
      return true;
    }
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

  private static boolean isInboundTemplate(JsonNode template) {
    if (template == null) {
      return false;
    }
    JsonNode elementType = template.path("elementType");
    if (elementType.isMissingNode()) {
      return false;
    }
    return INBOUND_ELEMENT_TYPES.contains(elementType.path("value").asText(""));
  }
}
