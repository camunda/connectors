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
import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator.ScanResult;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "scan")
public class Scan implements Callable<Integer> {

  @ParentCommand ConGen connectorGen;

  @Parameters(
      index = "0..*",
      description =
          "parameters to be passed to the generator (at least the generation source)."
              + " Refer to the documentation of the specific generator module for details.")
  List<String> params;

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @SuppressWarnings("unchecked")
  @Override
  public Integer call() {
    CliCompatibleTemplateGenerator<Object, ?> generator =
        (CliCompatibleTemplateGenerator<Object, ?>)
            Generate.loadGenerator(connectorGen.generatorName);
    Object input;
    try {
      input = generator.prepareInput(params);
    } catch (Exception e) {
      System.err.println("Error while preparing input data: " + e.getMessage());
      return -1;
    }
    ScanResult result;
    try {
      result = generator.scan(input);
    } catch (Exception e) {
      System.err.println("The API is not supported: " + e.getMessage());
      return -1;
    }
    try {
      var resultString = mapper.writeValueAsString(result);
      System.out.println(resultString);
      return 0;
    } catch (JsonProcessingException e) {
      System.err.println("Failed to serialize result: " + e.getMessage());
      return -1;
    }
  }
}
