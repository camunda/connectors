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
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "connectorgen",
    subcommands = {Generate.class, Scan.class},
    mixinStandardHelpOptions = true,
    version = "connectorgen 1.0",
    description = "Generate element templates for connectors")
public class ConnectorGen {

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

  @Parameters(index = "0", description = "name of the generator to invoke")
  String generatorName;

  GeneratorConfiguration generatorConfiguration() {
    return new GeneratorConfiguration(
        hybrid ? ConnectorMode.HYBRID : ConnectorMode.NORMAL, templateId, templateName, null);
  }
}
