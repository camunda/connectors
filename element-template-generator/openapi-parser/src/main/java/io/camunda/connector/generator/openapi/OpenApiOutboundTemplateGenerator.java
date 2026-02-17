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

import static io.camunda.connector.generator.openapi.util.SecurityUtil.parseAuthentication;

import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.RestTemplateGenerator;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.http.FactoryUtils;
import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.camunda.connector.generator.dsl.http.HttpOperationBuilder;
import io.camunda.connector.generator.dsl.http.HttpOutboundElementTemplateBuilder;
import io.camunda.connector.generator.dsl.http.HttpServerData;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.openapi.util.OperationUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiOutboundTemplateGenerator
    implements CliCompatibleTemplateGenerator<OpenApiGenerationSource>,
        RestTemplateGenerator<OpenApiGenerationSource> {

  private static final Set<BpmnType> SUPPORTED_ELEMENT_TYPES =
      Set.of(BpmnType.SERVICE_TASK, BpmnType.INTERMEDIATE_THROW_EVENT);
  private static final ConnectorElementType DEFAULT_ELEMENT_TYPE =
      new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null);

  public OpenApiOutboundTemplateGenerator() {
    super();
    // workaround for https://github.com/swagger-api/swagger-parser/issues/1857 (large yaml files)
    System.setProperty("maxYamlCodePoints", String.valueOf(Integer.MAX_VALUE));
  }

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiOutboundTemplateGenerator.class);

  @Override
  public String getGeneratorId() {
    return "openapi-outbound";
  }

  @Override
  public OpenApiGenerationSource prepareInput(List<String> parameters) {
    return new OpenApiGenerationSource(parameters);
  }

  public String getUsage() {
    return OpenApiGenerationSource.USAGE;
  }

  private List<OperationParseResult> extractOperations(OpenApiGenerationSource input) {
    return OperationUtil.extractOperations(
        input.openAPI(), input.includeOperations(), input.options());
  }

  @Override
  public List<RestTemplateGenerator.Operation> operations(OpenApiGenerationSource input) {
    var operations = extractOperations(input);
    return FactoryUtils.transformOperationParseResults(operations);
  }

  @Override
  public ScanResult scan(OpenApiGenerationSource input) {
    var operations = extractOperations(input);
    var supportedOperations =
        operations.stream()
            .filter(OperationParseResult::supported)
            .map(OperationParseResult::builder)
            .toList();
    var templates =
        buildTemplates(input.openAPI(), supportedOperations, GeneratorConfiguration.DEFAULT);
    if (templates.isEmpty()) {
      throw new IllegalArgumentException("No operations found in OpenAPI document");
    }
    var template = templates.getFirst();
    return new ScanResult(
        template.id(),
        template.name(),
        template.version(),
        (String)
            template.properties().stream()
                .filter(p -> p.getBinding().equals(ZeebeTaskDefinition.TYPE))
                .findFirst()
                .orElseThrow()
                .getValue(),
        operations);
  }

  @Override
  public List<ElementTemplate> generate(
      OpenApiGenerationSource source, GeneratorConfiguration configuration) {

    var operations =
        OperationUtil.extractOperations(
            source.openAPI(), source.includeOperations(), source.options());
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("No operations found in OpenAPI document");
    }
    var supportedOperations =
        operations.stream()
            .filter(
                op -> {
                  if (op.supported()) {
                    return true;
                  }
                  LOG.warn(
                      "Operation {} is not supported, reason: {}. It will be skipped",
                      op.id(),
                      op.info());
                  return false;
                })
            .map(OperationParseResult::builder)
            .toList();

    return buildTemplates(source.openAPI(), supportedOperations, configuration);
  }

  private List<ElementTemplate> buildTemplates(
      OpenAPI openAPI,
      List<HttpOperationBuilder> operationBuilders,
      GeneratorConfiguration configuration) {

    if (configuration == null) {
      configuration = GeneratorConfiguration.DEFAULT;
    }

    var authentication = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());
    if (authentication.isEmpty()) {
      authentication = List.of(NoAuth.INSTANCE);
    }

    var elementTypes = configuration.elementTypes();
    if (elementTypes == null) {
      elementTypes = Set.of(DEFAULT_ELEMENT_TYPE);
    }
    elementTypes.stream()
        .filter(t -> !SUPPORTED_ELEMENT_TYPES.contains(t.elementType()))
        .findFirst()
        .ifPresent(
            t -> {
              throw new IllegalArgumentException(
                  String.format("Unsupported element type '%s'", t.elementType().getName())
                      + " for OpenAPI generator. Supported element types: "
                      + SUPPORTED_ELEMENT_TYPES.stream()
                          .map(BpmnType::getName)
                          .collect(Collectors.joining(", ")));
            });
    if (elementTypes.isEmpty()) {
      elementTypes = Set.of(DEFAULT_ELEMENT_TYPE);
    }

    List<ElementTemplate> templates = new ArrayList<>();
    for (var elementType : elementTypes) {
      var template = buildTemplate(openAPI, operationBuilders, configuration, authentication);
      template.elementType(elementType);
      templates.add(template.build());
    }
    return templates;
  }

  private HttpOutboundElementTemplateBuilder buildTemplate(
      OpenAPI openAPI,
      List<HttpOperationBuilder> operationBuilders,
      GeneratorConfiguration configuration,
      List<HttpAuthentication> authentication) {
    var info = openAPI.getInfo();
    return HttpOutboundElementTemplateBuilder.create(
            ConnectorMode.HYBRID.equals(configuration.connectorMode()))
        .id(
            configuration.templateId() != null
                ? configuration.templateId()
                : getIdFromApiTitle(info.getTitle()))
        .name(configuration.templateName() != null ? configuration.templateName() : info.getTitle())
        .version(
            processVersion(
                configuration.templateVersion() != null
                    ? configuration.templateVersion().toString()
                    : info.getVersion()))
        .operations(
            operationBuilders.stream()
                .map(HttpOperationBuilder::build)
                .collect(Collectors.toList()))
        .servers(extractServers(openAPI.getServers()))
        .authentication(authentication);
  }

  private String getIdFromApiTitle(String title) {
    return title.trim().replace(" ", "-");
  }

  private int processVersion(String openAPIDocVersion) {
    // open API doc version is a string, usually a semantic version, but it's not guaranteed
    // if it contains numbers and no letters, we only keep the numbers and parse as int
    // otherwise we transform characters to their ascii value and sum them up

    String onlyNumbers = openAPIDocVersion.replaceAll("[^0-9]", "");
    if (!onlyNumbers.isEmpty()) {
      return Integer.parseInt(onlyNumbers);
    } else {
      return openAPIDocVersion.chars().sum();
    }
  }

  private List<HttpServerData> extractServers(List<Server> servers) {
    if (servers == null) {
      return Collections.emptyList();
    }
    return servers.stream()
        .map(server -> new HttpServerData(server.getUrl(), getServerLabel(server)))
        .collect(Collectors.toList());
  }

  private String getServerLabel(Server server) {
    return Optional.ofNullable(server.getDescription()).orElse(server.getUrl());
  }
}
