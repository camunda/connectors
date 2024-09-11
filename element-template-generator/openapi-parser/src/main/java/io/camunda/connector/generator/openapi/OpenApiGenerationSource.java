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
package io.camunda.connector.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @param openAPI Parsed OpenAPI schema
 * @param includeOperations IDs of operations that should be processed. If null/empty, all
 *     operations will be taken into account.
 * @param options Additional options for the generation process
 */
public record OpenApiGenerationSource(
    OpenAPI openAPI, Set<String> includeOperations, Options options) {

  /**
   * @param rawBody If true, the generated template will contain a JSON property "body" that
   *     contains the raw request body example. If false, the body will be parsed and transformed
   *     into individual properties when possible. Default: false.
   */
  public record Options(boolean rawBody) {}

  static final String USAGE = "[operation-id] openapi-outbound [openapi-file]... [--raw-body]";

  public OpenApiGenerationSource(List<String> cliParams) {
    this(fetchOpenApi(cliParams), extractOperationIds(cliParams), extractOptions(cliParams));
  }

  private static OpenAPI fetchOpenApi(List<String> cliParams) {
    if (cliParams.isEmpty()) {
      throw new IllegalArgumentException(
          "OpenAPI file path or URL must be provided as first parameter");
    }
    var openApiPathOrContent = cliParams.get(0);
    var openApiParser = new OpenAPIV3Parser();
    try {
      if (isValidJSON(openApiPathOrContent) || isValidYAML(openApiPathOrContent)) {
        return openApiParser.readContents(openApiPathOrContent).getOpenAPI();
      }
      return openApiParser.read(openApiPathOrContent);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse OpenAPI file from "
              + openApiPathOrContent
              + ". Make sure the location is specified correctly and does not require authentication.",
          e);
    }
  }

  public static boolean isValidJSON(String jsonInString) {
    try {
      final ObjectMapper mapper = new ObjectMapper();
      mapper.readTree(jsonInString);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static boolean isValidYAML(String yamlString) {
    try {
      final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      mapper.readTree(yamlString);
      return yamlString.contains("\n");
    } catch (IOException e) {
      return false;
    }
  }

  private static Set<String> extractOperationIds(List<String> cliParams) {
    var cliParamsWithoutOptions =
        cliParams.stream().filter(param -> !param.startsWith("--")).toList();
    if (cliParamsWithoutOptions.size() < 2) {
      return Set.of();
    }
    return Set.copyOf(cliParamsWithoutOptions.subList(1, cliParamsWithoutOptions.size()));
  }

  private static Options extractOptions(List<String> cliParams) {
    return new Options(cliParams.stream().anyMatch(param -> param.equals("--raw-body")));
  }
}
