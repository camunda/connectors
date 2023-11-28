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
import io.camunda.connector.generator.dsl.BpmnType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "congen",
    subcommands = {Generate.class, Scan.class},
    mixinStandardHelpOptions = true,
    version = "congen 0.1",
    description = "Generate element templates for connectors")
public class ConGen {

  @Option(
      names = {"-h", "--hybrid"},
      usageHelp = true,
      description = "generate a hybrid template (with configurable type)")
  boolean hybrid;

  @Option(
      names = {"-i", "--id"},
      description = "template id to use for generation")
  String templateId;

  @Option(
      names = {"-n", "--name"},
      description = "template name to use for generation")
  String templateName;

  @Option(
      names = {"-e", "--element-types"},
      description = "target element types for the resulting connector")
  List<String> elementTypes;

  @Parameters(index = "0", description = "name of the generator to invoke")
  String generatorName;

  GeneratorConfiguration generatorConfiguration() {
    var bpmnTypes =
        elementTypes == null
            ? null
            : elementTypes.stream()
                .map(BpmnType::fromName)
                .map(
                    bpmnType ->
                        new ConnectorElementType(getAppliesToFromBpmnType(bpmnType), bpmnType))
                .collect(Collectors.toSet());
    return new GeneratorConfiguration(
        hybrid ? ConnectorMode.HYBRID : ConnectorMode.NORMAL,
        templateId,
        templateName,
        null,
        bpmnTypes);
  }

  private Set<BpmnType> getAppliesToFromBpmnType(BpmnType bpmnType) {
    if (bpmnType == null) {
      return null;
    }
    return switch (bpmnType) {
      case SERVICE_TASK, TASK, SCRIPT_TASK -> Set.of(BpmnType.TASK);
      case INTERMEDIATE_THROW_EVENT, INTERMEDIATE_CATCH_EVENT -> Set.of(
          BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT);
      case START_EVENT -> Set.of(BpmnType.START_EVENT);
      case MESSAGE_START_EVENT -> Set.of(BpmnType.MESSAGE_START_EVENT);
      case END_EVENT, MESSAGE_END_EVENT -> Set.of(BpmnType.END_EVENT);
      default -> throw new IllegalArgumentException("Unsupported BPMN type: " + bpmnType);
    };
  }
}
