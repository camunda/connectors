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
package io.camunda.connector.generator.java;

import static java.lang.Boolean.TRUE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.generator.api.DocsGenerator;
import io.camunda.connector.generator.api.DocsGeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.dsl.Doc;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.util.DocsDataProviderStrategy;
import io.camunda.connector.generator.java.util.DocsPebbleExtension;
import io.camunda.connector.generator.java.util.DocsProperty;
import io.camunda.connector.generator.java.util.ReflectionUtil;
import io.camunda.connector.generator.java.util.TemplateGenerationContext;
import io.camunda.connector.generator.java.util.TemplateGenerationContextUtil;
import io.camunda.connector.generator.java.util.TemplatePropertiesUtil;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import uk.co.jemos.podam.api.DataProviderStrategy;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class ClassBasedDocsGenerator implements DocsGenerator<Class<?>> {

  private final ClassLoader classLoader;

  public ClassBasedDocsGenerator(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public ClassBasedDocsGenerator() {
    this(Thread.currentThread().getContextClassLoader());
  }

  @Override
  public Doc generate(Class<?> connectorDefinition, DocsGeneratorConfiguration configuration) {

    ElementTemplate template =
        ReflectionUtil.getRequiredAnnotation(connectorDefinition, ElementTemplate.class);
    TemplateGenerationContext templateGenerationContext =
        TemplateGenerationContextUtil.createContext(
            connectorDefinition, GeneratorConfiguration.DEFAULT);

    var model = buildTemplateModel(template, templateGenerationContext);

    PebbleEngine engine =
        new PebbleEngine.Builder()
            .loader(new FileLoader())
            .autoEscaping(false)
            .extension(new DocsPebbleExtension())
            .build();

    var absolute = new File(configuration.templatePath()).getAbsolutePath();
    PebbleTemplate compiledTemplate = engine.getTemplate(absolute);
    var output = renderTemplate(model, compiledTemplate);

    return new Doc(configuration.outputPath(), output);
  }

  private Map<String, Object> buildTemplateModel(
      ElementTemplate elementTemplate, TemplateGenerationContext templateGenerationContext) {
    Map<String, Object> model = new HashMap<>();
    model.put("id", elementTemplate.id());
    model.put("name", elementTemplate.name());
    model.put("description", elementTemplate.description());
    model.put("version", elementTemplate.version());
    model.put("type", templateGenerationContext.connectorType());
    model.put(
        "elementTypes",
        Arrays.stream(elementTemplate.elementTypes())
            .map(et -> et.elementType().getName())
            .toList());

    ElementTemplateIcon icon =
        !elementTemplate.icon().isBlank()
            ? ElementTemplateIcon.from(elementTemplate.icon(), classLoader)
            : null;
    if (icon != null) {
      model.put("icon", icon.contents());
    }

    if (!Void.class.equals(elementTemplate.outputDataClass())) {
      model.put("outputJson", generateExampleData(elementTemplate.outputDataClass()));
    }

    var connectorInput = elementTemplate.inputDataClass();

    Map<String, DocsProperty> properties =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(
                connectorInput, templateGenerationContext)
            .stream()
            .map(PropertyBuilder::build)
            .collect(
                Collectors.toMap(
                    Property::getId,
                    p -> {
                      var type =
                          switch (p.getType()) {
                            case "String", "Text" -> String.class;
                            default -> Object.class;
                          };
                      var exampleValue =
                          p.getExampleValue() != null
                              ? p.getExampleValue().toString()
                              : generateExampleData(type);

                      var required =
                          p.getConstraints() != null && TRUE == p.getConstraints().notEmpty();

                      return new DocsProperty(
                          p.getLabel(),
                          p.getType(),
                          StringUtils.isNotEmpty(p.getDescription()) ? p.getDescription() : "",
                          exampleValue,
                          required,
                          p);
                    }));

    model.put("properties", properties);

    return model;
  }

  public static String generateExampleData(Class<?> type) {
    DataProviderStrategy strategy = new DocsDataProviderStrategy();
    PodamFactory factory = new PodamFactoryImpl(strategy);
    var exampleOutput = factory.manufacturePojo(type);
    String exampleOutputJson;
    try {
      exampleOutputJson =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER
              .writerWithDefaultPrettyPrinter()
              .writeValueAsString(exampleOutput);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return exampleOutputJson;
  }

  private String renderTemplate(Map<String, Object> model, PebbleTemplate pebbleTemplate) {
    Writer writer = new StringWriter();
    try {
      pebbleTemplate.evaluate(writer, model);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return writer.toString();
  }
}
