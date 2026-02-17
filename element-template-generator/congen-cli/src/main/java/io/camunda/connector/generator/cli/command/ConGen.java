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
package io.camunda.connector.generator.cli.command;

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.java.annotation.BpmnType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "congen",
    subcommands = {Generate.class, Scan.class, ListGenerators.class},
    mixinStandardHelpOptions = true,
    version = "congen 0.1",
    description = "Generate element templates for connectors")
public class ConGen {

  @Option(
      names = {"-h", "--hybrid"},
      usageHelp = true,
      description = {
        "Generate a hybrid template.",
        "A hybrid template exposes its task definition type as an editable property.",
      })
  boolean hybrid;

  @Option(
      names = {"-i", "--id"},
      description = {
        "Template ID to use for generation",
        "If not specified, a sensible default will be chosen by the selected generator."
      })
  String templateId;

  @Option(
      names = {"-n", "--name"},
      description = {
        "Template name to use for generation.",
        "If not specified, a sensible default will be chosen by the selected generator."
      })
  String templateName;

  @Option(
      names = {"-e", "--element-types"},
      description = {
        "Target element types for the resulting connector.",
        "Multiple values possible, for example:",
        "-e bpmn:ServiceTask -e bpmn:IntermediateThrowEvent"
      },
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      defaultValue = "ServiceTask")
  List<String> elementTypes;

  GeneratorConfiguration generatorConfiguration() {
    var bpmnTypes =
        Optional.ofNullable(elementTypes)
            .map(
                types ->
                    types.stream()
                        .map(this::parseBpmnType)
                        .map(
                            bpmnType ->
                                new ConnectorElementType(
                                    getAppliesToFromBpmnType(bpmnType), bpmnType, null, null))
                        .collect(Collectors.toSet()))
            .orElse(null);
    return new GeneratorConfiguration(
        hybrid ? ConnectorMode.HYBRID : ConnectorMode.NORMAL,
        templateId,
        templateName,
        null,
        bpmnTypes,
        Map.of()); // todo: do we need to support feature overrides from the CLI?
  }

  private BpmnType parseBpmnType(String type) {
    var supportedTypes =
        Arrays.stream(BpmnType.values()).map(BpmnType::getId).collect(Collectors.joining(", "));
    return Arrays.stream(BpmnType.values())
        .filter(bpmnType -> bpmnType.getId().equalsIgnoreCase(type))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported BPMN type: " + type + ". Supported types: " + supportedTypes));
  }

  private Set<BpmnType> getAppliesToFromBpmnType(BpmnType bpmnType) {
    if (bpmnType == null) {
      return null;
    }
    return switch (bpmnType) {
      case SERVICE_TASK, TASK, SCRIPT_TASK -> Set.of(BpmnType.TASK);
      case INTERMEDIATE_THROW_EVENT, INTERMEDIATE_CATCH_EVENT ->
          Set.of(BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT);
      case START_EVENT -> Set.of(BpmnType.START_EVENT);
      case RECEIVE_TASK -> Set.of(BpmnType.RECEIVE_TASK);
      case MESSAGE_START_EVENT -> Set.of(BpmnType.MESSAGE_START_EVENT);
      case END_EVENT, MESSAGE_END_EVENT -> Set.of(BpmnType.END_EVENT);
      default -> throw new IllegalArgumentException("Unsupported BPMN type: " + bpmnType);
    };
  }
}
