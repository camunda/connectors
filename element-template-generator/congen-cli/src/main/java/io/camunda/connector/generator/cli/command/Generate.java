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

import static io.camunda.connector.generator.cli.ReturnCodes.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.resource.SchemaLoader.Builder;
import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import io.camunda.connector.generator.cli.GeneratorServiceLoader;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "generate")
public class Generate implements Callable<Integer> {

  static final Schema jsonSchema =
      SchemaRegistry.withDialect(
              Dialects.getDraft202012(),
              builder -> builder.schemaLoader(Builder::fetchRemoteResources))
          .getSchema(
              SchemaLocation.of(
                  "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json"));

  private static final ObjectMapper mapper =
      new ObjectMapper().registerModule(new ElementTemplateModule());

  @ParentCommand ConGen connectorGen;

  @Parameters(index = "0", description = "name of the generator to invoke")
  String generatorName;

  @Parameters(
      index = "1..*",
      description =
          "parameters to be passed to the generator (at least the generation source)."
              + " Refer to the documentation of the specific generator module for details.")
  List<String> params;

  static CliCompatibleTemplateGenerator<?> loadGenerator(String name) {
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

  @SuppressWarnings("unchecked")
  @Override
  public Integer call() {
    CliCompatibleTemplateGenerator<Object> generator =
        (CliCompatibleTemplateGenerator<Object>) loadGenerator(generatorName);
    Object input;
    try {
      input = generator.prepareInput(params);
    } catch (Exception e) {
      System.err.println("Error while preparing input data: " + e.getMessage());
      return INPUT_PREPARATION_FAILED.getCode();
    }
    List<ElementTemplate> templates;
    try {
      templates = generator.generate(input, connectorGen.generatorConfiguration());
    } catch (Exception e) {
      System.err.println("Generation failed: " + e.getMessage());
      return GENERATION_FAILED.getCode();
    }
    try {
      String resultString;
      if (templates.size() == 1) {
        resultString =
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(templates.getFirst());
      } else {
        resultString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(templates);
      }
      List<Error> errors = jsonSchema.validate(resultString, InputFormat.JSON);
      if (!errors.isEmpty()) {
        System.err.println("Validation failed:");
        for (Error error : errors) {
          System.err.println(error.getMessage());
        }
        return JSON_SCHEMA_VALIDATION_FAILED.getCode();
      }
      System.out.println(resultString);
      return SUCCESS.getCode();
    } catch (JsonProcessingException e) {
      System.err.println("Failed to serialize the result: " + e.getMessage());
      return GENERATION_FAILED.getCode();
    }
  }
}
