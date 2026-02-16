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

import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static io.camunda.connector.util.reflection.ReflectionUtil.getRequiredAnnotation;
import static java.lang.Boolean.TRUE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.generator.api.DocsGenerator;
import io.camunda.connector.generator.api.DocsGeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.dsl.Doc;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.java.annotation.DataExample;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.java.util.DataExampleModel;
import io.camunda.connector.generator.java.util.DocsDataProviderStrategy;
import io.camunda.connector.generator.java.util.DocsPebbleExtension;
import io.camunda.connector.generator.java.util.DocsProperty;
import io.camunda.connector.generator.java.util.TemplateGenerationContext;
import io.camunda.connector.generator.java.util.TemplateGenerationContextUtil;
import io.camunda.connector.generator.java.util.TemplatePropertiesUtil;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.core.DisallowExtensionCustomizerBuilder;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.DelegatingLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import uk.co.jemos.podam.api.DataProviderStrategy;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class ClassBasedDocsGenerator implements DocsGenerator<Class<?>> {

  private static final ObjectWriter OBJECT_WRITER =
      ConnectorsObjectMapperSupplier.getCopy()
          .enable(SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .registerModule(new ElementTemplateModule())
          .writerWithDefaultPrettyPrinter();
  private static final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();
  private final ClassLoader classLoader;

  public ClassBasedDocsGenerator(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public ClassBasedDocsGenerator() {
    this(Thread.currentThread().getContextClassLoader());
  }

  public static Map<String, DataExampleModel> collectExampleData(Class<?> type) {
    var methods = findAllDataExampleMethods(type);
    return methods.stream()
        .map(
            pair -> {
              var method = pair.getLeft();
              var annotation = pair.getRight();
              Object result;
              String json;
              Object feelResult = null;
              String feelResultJson = null;
              try {
                result = method.invoke(new Arrays[0]);
                json = OBJECT_WRITER.writeValueAsString(result);
                if (StringUtils.isNotBlank(annotation.feel())) {
                  feelResult = feelEngineWrapper.evaluate(annotation.feel(), result);
                  feelResultJson = OBJECT_WRITER.writeValueAsString(feelResult);
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              return new DataExampleModel(
                  annotation.id(), result, json, annotation.feel(), feelResult, feelResultJson);
            })
        .collect(Collectors.toMap(DataExampleModel::id, v -> v));
  }

  public static List<Pair<Method, DataExample>> findAllDataExampleMethods(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(m -> Modifier.isStatic(m.getModifiers()))
        .filter(
            m ->
                Arrays.stream(m.getAnnotations())
                    .anyMatch(a -> DataExample.class.equals(a.annotationType())))
        .map(m -> Pair.of(m, m.getDeclaredAnnotation(DataExample.class)))
        .toList();
  }

  public static String generateExampleData(Class<?> type) {
    DataProviderStrategy strategy = new DocsDataProviderStrategy();
    PodamFactory factory = new PodamFactoryImpl(strategy);
    var exampleOutput = factory.manufacturePojo(type);
    String exampleOutputJson;
    try {
      exampleOutputJson = OBJECT_WRITER.writeValueAsString(exampleOutput);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return exampleOutputJson;
  }

  @Override
  public Doc generate(Class<?> connectorDefinition, DocsGeneratorConfiguration configuration) {

    ElementTemplate template = getRequiredAnnotation(connectorDefinition, ElementTemplate.class);
    TemplateGenerationContext templateGenerationContext =
        TemplateGenerationContextUtil.createContext(
            connectorDefinition, GeneratorConfiguration.DEFAULT);

    var model = buildTemplateModel(template, templateGenerationContext);
    var path = Path.of(configuration.templatePath());

    // Create loaders for both filesystem and classpath
    FileLoader fileLoader = new FileLoader(path.getParent().toAbsolutePath().toString());

    ClasspathLoader classpathLoader = new ClasspathLoader();
    classpathLoader.setPrefix("templates/");

    DelegatingLoader delegatingLoader = new DelegatingLoader(List.of(fileLoader, classpathLoader));

    PebbleEngine engine =
        new PebbleEngine.Builder()
            .registerExtensionCustomizer(
                new DisallowExtensionCustomizerBuilder()
                    .disallowedTokenParserTags(List.of("include"))
                    .build()) // Security fix for https://www.cve.org/CVERecord?id=CVE-2025-1686
            .loader(delegatingLoader)
            .autoEscaping(false)
            .extension(new DocsPebbleExtension())
            .build();

    PebbleTemplate compiledTemplate = engine.getTemplate(path.getFileName().toString());
    var output = renderTemplate(model, compiledTemplate);

    return new Doc(configuration.outputPath(), output);
  }

  private Map<String, Object> buildTemplateModel(
      ElementTemplate elementTemplate, TemplateGenerationContext templateGenerationContext) {
    Map<String, Object> model = new HashMap<>();
    model.put("id", elementTemplate.id());
    model.put("name", elementTemplate.name());
    model.put("description", elementTemplate.description());
    model.put("keywords", elementTemplate.metadata().keywords());
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

    // Extract example data
    if (!Void.class.equals(elementTemplate.outputDataClass())) {
      var exampleDataModels = collectExampleData(elementTemplate.outputDataClass());
      model.put("exampleData", exampleDataModels);
    }

    // Extract properties from element template metadata
    var connectorInput = elementTemplate.inputDataClass();
    var propertyBuilders =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(
            connectorInput, templateGenerationContext);

    // Map to docs compatible property representation
    Map<String, DocsProperty> properties =
        propertyBuilders.stream()
            .map(PropertyBuilder::build)
            .collect(Collectors.toMap(Property::getId, this::mapToDocsProperty));
    model.put("properties", properties);

    return model;
  }

  private DocsProperty mapToDocsProperty(Property property) {
    var type =
        switch (property.getType()) {
          case "String", "Text" -> String.class;
          default -> Object.class;
        };
    var exampleValue =
        property.getExampleValue() != null
            ? property.getExampleValue().toString()
            : generateExampleData(type);

    var required =
        property.getConstraints() != null && TRUE == property.getConstraints().notEmpty();

    return new DocsProperty(
        property.getLabel(),
        property.getType(),
        StringUtils.isNotEmpty(property.getDescription()) ? property.getDescription() : "",
        exampleValue,
        required,
        property);
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
