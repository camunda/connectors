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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import io.camunda.connector.generator.cli.GeneratorServiceLoader;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "generate")
public class Generate implements Runnable {

  @ParentCommand ConnectorGen connectorGen;

  @Parameters(index = "0", description = "name of the generator to invoke")
  String generatorName;

  @Parameters(
      index = "1..*",
      description = "parameters to be passed to the generator (at least the generation source)")
  List<String> params;

  static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @SuppressWarnings("unchecked")
  @Override
  public void run() {
    CliCompatibleTemplateGenerator<Object, ?> generator =
        (CliCompatibleTemplateGenerator<Object, ?>) loadGenerator(generatorName);
    var input = generator.prepareInput(params);
    var template = generator.generate(input, connectorGen.generatorConfiguration());
    try {
      var resultString = mapper.writeValueAsString(template);
      System.out.println(resultString);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  static CliCompatibleTemplateGenerator<?, ?> loadGenerator(String name) {
    var generators = GeneratorServiceLoader.loadGenerators();
    if (generators.isEmpty()) {
      throw new IllegalStateException("No generators available");
    }
    var generator = generators.get(name);
    if (generator == null) {
      throw new IllegalArgumentException(
          "No generator found with name "
              + name
              + ". Known generators: "
              + String.join(", ", generators.keySet()));
    }
    return generator;
  }
}
