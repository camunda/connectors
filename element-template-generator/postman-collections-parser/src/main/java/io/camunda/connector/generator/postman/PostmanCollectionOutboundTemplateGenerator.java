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
package io.camunda.connector.generator.postman;

import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.RestTemplateGenerator;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.http.FactoryUtils;
import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.camunda.connector.generator.dsl.http.HttpOperationBuilder;
import io.camunda.connector.generator.dsl.http.HttpOutboundElementTemplateBuilder;
import io.camunda.connector.generator.dsl.http.HttpServerData;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210;
import io.camunda.connector.generator.postman.utils.PostmanOperationUtil;
import io.camunda.connector.generator.postman.utils.SecurityUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostmanCollectionOutboundTemplateGenerator
    implements CliCompatibleTemplateGenerator<PostmanCollectionsGenerationSource>,
        RestTemplateGenerator<PostmanCollectionsGenerationSource> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PostmanCollectionOutboundTemplateGenerator.class);

  private static final String EMPTY_FEEL_STRING = "=\"\"";

  private static final Set<BpmnType> SUPPORTED_ELEMENT_TYPES =
      Set.of(BpmnType.SERVICE_TASK, BpmnType.INTERMEDIATE_THROW_EVENT);
  private static final ConnectorElementType DEFAULT_ELEMENT_TYPE =
      new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null);

  @Override
  public String getGeneratorId() {
    return "postman-collections-outbound";
  }

  @Override
  public PostmanCollectionsGenerationSource prepareInput(List<String> parameters) {
    LOG.info("Supplied the following params: " + parameters);
    return new PostmanCollectionsGenerationSource(parameters);
  }

  @Override
  public String getUsage() {
    return """
    postman-collections-outbound $COLLECTION_PATH/collection.json "/folder1/My Operation 1" "/folder1/folder2/My Operation 2"
    """;
  }

  @Override
  public List<Operation> operations(PostmanCollectionsGenerationSource input) {
    var operations = extractOperations(input);
    return FactoryUtils.transformOperationParseResults(operations);
  }

  @Override
  public ScanResult scan(PostmanCollectionsGenerationSource input) {
    var templates = generate(input, null);
    if (templates.isEmpty()) {
      throw new RuntimeException("Scan did not return any template");
    }
    var firstTemplate = templates.getFirst();

    var supportedOperations = extractSupportedOperations(input);

    return new ScanResult(
        firstTemplate.id(),
        firstTemplate.name(),
        firstTemplate.version(),
        (String)
            firstTemplate.properties().stream()
                .filter(p -> p.getBinding().equals(ZeebeTaskDefinition.TYPE))
                .findFirst()
                .orElseThrow()
                .getValue(),
        supportedOperations);
  }

  private List<OperationParseResult> extractOperations(PostmanCollectionsGenerationSource source) {
    return PostmanOperationUtil.extractOperations(source.collection(), source.includeOperations());
  }

  @Override
  public List<ElementTemplate> generate(
      PostmanCollectionsGenerationSource source, GeneratorConfiguration configuration) {
    if (configuration == null) {
      configuration = GeneratorConfiguration.DEFAULT;
    }

    LOG.info("Running with the following configuration: " + configuration);

    var supportedOperations = extractSupportedOperations(source);

    return buildTemplates(source.collection(), supportedOperations, configuration);
  }

  private List<HttpOperationBuilder> extractSupportedOperations(
      PostmanCollectionsGenerationSource source) {
    var operations = extractOperations(source);
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("No operations found in the Postman Collection document");
    }

    return operations.stream()
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
  }

  private List<ElementTemplate> buildTemplates(
      PostmanCollectionV210 postmanCollectionJson,
      List<HttpOperationBuilder> operationBuilders,
      GeneratorConfiguration configuration) {

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
                  String.format("Unsupported element type '%s'", t.elementType().getName()));
            });
    if (elementTypes.isEmpty()) {
      elementTypes = Set.of(DEFAULT_ELEMENT_TYPE);
    }

    List<ElementTemplate> templates = new ArrayList<>();
    for (var elementType : elementTypes) {
      var template =
          buildTemplate(
              postmanCollectionJson,
              operationBuilders,
              configuration,
              SecurityUtils.setGlobalAuthentication(postmanCollectionJson));
      template.elementType(elementType);
      templates.add(template.build());
    }
    return templates;
  }

  private HttpOutboundElementTemplateBuilder buildTemplate(
      PostmanCollectionV210 postmanCollectionJson,
      List<HttpOperationBuilder> operationBuilders,
      GeneratorConfiguration configuration,
      List<HttpAuthentication> authentication) {
    var info = postmanCollectionJson.info();
    return HttpOutboundElementTemplateBuilder.create(
            ConnectorMode.HYBRID.equals(configuration.connectorMode()))
        .id(
            configuration.templateId() != null
                ? configuration.templateId()
                : getIdFromApiTitle(info))
        .name(configuration.templateName() != null ? configuration.templateName() : info.name())
        .version(1)
        .operations(
            operationBuilders.stream()
                .map(HttpOperationBuilder::build)
                .collect(Collectors.toList()))
        .authentication(authentication)
        // Context: Postman Collections do not announce base server data; they are also usually
        // variables
        .servers(List.of(new HttpServerData(EMPTY_FEEL_STRING, "")));
  }

  private String getIdFromApiTitle(PostmanCollectionV210.Info info) {
    return info.name().trim().replace(" ", "-").toLowerCase();
  }
}
