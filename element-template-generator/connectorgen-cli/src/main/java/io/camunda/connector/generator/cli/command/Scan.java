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

import static io.camunda.connector.generator.cli.command.Generate.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "scan")
public class Scan implements Runnable {

  @ParentCommand ConnectorGen connectorGen;

  @Parameters(index = "0", description = "name of the generator to invoke")
  String generatorName;

  @Parameters(
      index = "1..*",
      description = "parameters to be passed to the generator (at least the generation source)")
  List<String> params;

  @SuppressWarnings("unchecked")
  @Override
  public void run() {
    CliCompatibleTemplateGenerator<Object, ?> generator =
        (CliCompatibleTemplateGenerator<Object, ?>) Generate.loadGenerator(generatorName);
    var input = generator.prepareInput(params);
    var result = generator.scan(input);
    try {
      var resultString = mapper.writeValueAsString(result);
      System.out.println(resultString);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
