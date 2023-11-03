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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Set;

/**
 * @param openAPI Parsed OpenAPI schema
 * @param includeOperations IDs of operations that should be processed. If null/empty, all
 *     operations will be taken into account.
 */
public record OpenApiGenerationSource(OpenAPI openAPI, Set<String> includeOperations) {

  public OpenApiGenerationSource(List<String> cliParams) {
    this(fetchOpenApi(cliParams), extractOperationIds(cliParams));
  }

  private static OpenAPI fetchOpenApi(List<String> cliParams) {
    if (cliParams.size() < 1) {
      throw new IllegalArgumentException(
          "OpenAPI file path or URL must be provided as first parameter");
    }
    var openApiPath = cliParams.get(0);
    var openApiParser = new OpenAPIV3Parser();
    try {
      return openApiParser.read(openApiPath);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse OpenAPI file from "
              + openApiPath
              + ". Make sure the location is specified correctly and does not require authentication.",
          e);
    }
  }

  private static Set<String> extractOperationIds(List<String> cliParams) {
    return cliParams.size() > 1 ? Set.copyOf(cliParams.subList(1, cliParams.size())) : Set.of();
  }
}
