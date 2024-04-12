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
package io.camunda.connector.generator.java.util;

import static io.camunda.connector.generator.java.util.ReflectionUtil.getAllFields;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.BooleanProperty;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.OneOf;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.PropertyGroup.PropertyGroupBuilder;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.generator.java.processor.FieldProcessor;
import io.camunda.connector.generator.java.processor.JakartaValidationFieldProcessor;
import io.camunda.connector.generator.java.processor.TemplatePropertyFieldProcessor;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Outbound;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

/** Utility class for transforming data classes into {@link PropertyBuilder} instances. */
public class TemplatePropertiesUtil {

  private static final List<FieldProcessor> fieldProcessors =
      List.of(new TemplatePropertyFieldProcessor(), new JakartaValidationFieldProcessor());

  /**
   * Analyze the type and return a list of {@link PropertyBuilder} instances.
   *
   * <p>Capabilities:
   *
   * <ul>
   *   <li>primitive types and Strings are mapped to template properties according to their type
   *   <li>nested types are handled recursively
   *   <li>{@link TemplateProperty} annotations are taken into account
   *   <li>Sealed hierarchies are supported by adding an extra Dropdown discriminator property.
   *       {@link TemplateSubType} annotations are used to configure the sealed hierarchies.
   * </ul>
   *
   * <p>Note: {@link PropertyBuilder#binding(PropertyBinding)} is not set by this method. The caller
   * is responsible for setting the binding according to the connector type (inbound or outbound).
   *
   * @param type the type to analyze
   * @return a list of {@link PropertyBuilder} instances
   */
  public static List<PropertyBuilder> extractTemplatePropertiesFromType(
      Class<?> type, TemplateGenerationContext context) {
    if (type.isSealed()) {
      return handleSealedType(type, context);
    }

    var fields = getAllFields(type);
    var properties = new ArrayList<PropertyBuilder>();

    for (Field field : fields) {
      if (isContainerType(field)) {
        var nestedPropertiesAnnotation = field.getAnnotation(NestedProperties.class);
        boolean hasPathPrefix =
            nestedPropertiesAnnotation == null || nestedPropertiesAnnotation.addNestedPath();
        boolean hasConditionOverride =
            nestedPropertiesAnnotation != null
                && StringUtils.isNotBlank(nestedPropertiesAnnotation.condition().property());
        boolean hasGroupOverride =
            nestedPropertiesAnnotation != null
                && StringUtils.isNotBlank(nestedPropertiesAnnotation.group());

        try {
          // analyze recursively
          var nestedProperties =
              extractTemplatePropertiesFromType(field.getType(), context).stream()
                  .peek(
                      builder -> {
                        if (hasPathPrefix) {
                          addPathPrefixToBuilder(builder, field.getName(), context);
                        }
                        if (hasConditionOverride) {
                          builder.condition(
                              TemplatePropertyFieldProcessor.transformToCondition(
                                  nestedPropertiesAnnotation.condition()));
                        }
                        if (hasGroupOverride) {
                          builder.group(nestedPropertiesAnnotation.group());
                        }
                      })
                  .toList();
          properties.addAll(nestedProperties);
        } catch (StackOverflowError e) {
          throw new RuntimeException(
              "Failed to analyze container field "
                  + field.getName()
                  + " of class "
                  + field.getDeclaringClass()
                  + " due to a stack overflow error. This is likely caused by a "
                  + "circular reference in the data class.\nCheck if the type is meant to be handled as "
                  + "a container type and consider applying a type override using @TemplateProperty or breaking the circular reference.");
        }
      } else {
        properties.add(buildProperty(field, context));
      }
    }
    return properties.stream().filter(Objects::nonNull).toList();
  }

  /**
   * Create property groups from a list of properties based on {@link TemplateProperty#group()}.
   *
   * <p>Properties without a group are ignored.
   *
   * @param properties the properties to group
   * @return a list of {@link PropertyGroup} instances
   */
  public static List<PropertyGroupBuilder> groupProperties(List<PropertyBuilder> properties) {
    return properties.stream()
        .map(PropertyBuilder::build)
        .filter(property -> property.getGroup() != null)
        .collect(Collectors.groupingBy(Property::getGroup))
        .entrySet()
        .stream()
        .map(
            entry ->
                PropertyGroup.builder()
                    .id(entry.getKey())
                    .label(transformIdIntoLabel(entry.getKey()))
                    .properties(entry.getValue()))
        .toList();
  }

  private static PropertyBuilder buildProperty(Field field, TemplateGenerationContext context) {
    var annotation = field.getAnnotation(TemplateProperty.class);
    String name, label, tooltip = null;
    String bindingName = field.getName();
    if (annotation != null) {
      if (annotation.ignore()) {
        return null;
      }
      if (!annotation.id().isBlank()) {
        name = annotation.id();
      } else {
        name = field.getName();
      }
      if (!annotation.label().isBlank()) {
        label = annotation.label();
      } else {
        label = transformIdIntoLabel(name);
      }
      if (!annotation.binding().name().isBlank()) {
        bindingName = annotation.binding().name();
      }
      if (!annotation.tooltip().isBlank()) {
        tooltip = annotation.tooltip();
      }
    } else {
      name = field.getName();
      label = transformIdIntoLabel(name);
    }

    PropertyBuilder propertyBuilder =
        createPropertyBuilder(field, annotation)
            .id(name)
            .label(label)
            .tooltip(tooltip)
            .binding(createBinding(bindingName, context));

    for (FieldProcessor processor : fieldProcessors) {
      processor.process(field, propertyBuilder, context);
    }
    return propertyBuilder;
  }

  private static void addPathPrefixToBuilder(
      PropertyBuilder builder, String path, TemplateGenerationContext context) {
    var originalId = builder.getId();
    builder.id(path + "." + originalId);
    var binding = builder.getBinding();

    if (binding instanceof ZeebeInput) {
      builder.binding(createBinding(path + "." + ((ZeebeInput) binding).name(), context));
    } else if (binding instanceof ZeebeProperty) {
      builder.binding(createBinding(path + "." + ((ZeebeProperty) binding).name(), context));
    }

    if (builder instanceof DiscriminatorPropertyBuilder discriminatorPropertyBuilder) {
      discriminatorPropertyBuilder
          .getDependantProperties()
          .forEach(
              dependant ->
                  dependant.condition(
                      addConditionPrefix(dependant.getCondition(), path, originalId)));
    }
  }

  private static PropertyCondition addConditionPrefix(
      PropertyCondition condition, String path, String discriminatorPropertyId) {
    switch (condition) {
      case AllMatch allMatchCondition -> {
        return new AllMatch(
            allMatchCondition.allMatch().stream()
                .map(
                    subCondition -> addConditionPrefix(subCondition, path, discriminatorPropertyId))
                .toList());
      }
      case Equals equalsCondition -> {
        if (!equalsCondition.property().equals(discriminatorPropertyId)) {
          return equalsCondition;
        }
        return new Equals(path + "." + equalsCondition.property(), equalsCondition.equals());
      }
      case OneOf oneOfCondition -> {
        if (!oneOfCondition.property().equals(discriminatorPropertyId)) {
          return oneOfCondition;
        }
        return new OneOf(path + "." + oneOfCondition.property(), oneOfCondition.oneOf());
      }
      default -> throw new IllegalStateException("Unknown condition type: " + condition.getClass());
    }
  }

  private static PropertyBuilder createPropertyBuilder(Field field, TemplateProperty annotation) {
    PropertyType type;
    Map<String, String> dropdownChoices = new HashMap<>();

    if (field.getType() == Boolean.class) {
      type = PropertyType.Boolean;
    } else if (field.getType().isEnum()) {
      type = PropertyType.Dropdown;
      dropdownChoices =
          Arrays.stream(field.getType().getEnumConstants())
              .collect(
                  Collectors.toMap(Object::toString, val -> transformIdIntoLabel(val.toString())));
    } else {
      type = PropertyType.String;
    }

    if (annotation != null) {
      if (annotation.type() != PropertyType.Unknown) {
        type = annotation.type();
      }
      if (annotation.choices().length > 0) {
        dropdownChoices =
            Arrays.stream(annotation.choices())
                .collect(
                    Collectors.toMap(
                        DropdownPropertyChoice::value,
                        DropdownPropertyChoice::label,
                        (a, b) -> a,
                        LinkedHashMap::new));
      }
    }

    var builder =
        switch (type) {
          case Boolean -> BooleanProperty.builder();
          case Dropdown -> DropdownProperty.builder()
              .choices(
                  dropdownChoices.entrySet().stream()
                      .map(
                          entry ->
                              new DropdownProperty.DropdownChoice(entry.getValue(), entry.getKey()))
                      .toList())
              .feel(FeelMode.disabled);
          case Hidden -> HiddenProperty.builder();
          case String -> StringProperty.builder();
          case Text -> TextProperty.builder();
          case Unknown -> throw new IllegalStateException("Unknown property type");
        };
    if (Object.class.equals(field.getType())
        || JsonNode.class.equals(field.getType())
        || Collection.class.isAssignableFrom(field.getType())
        || Map.class.isAssignableFrom(field.getType())) {
      builder.feel(FeelMode.required);
    }
    return builder;
  }

  private static List<PropertyBuilder> handleSealedType(
      Class<?> type, TemplateGenerationContext context) {
    var subTypes =
        Arrays.stream(type.getPermittedSubclasses())
            .filter(
                subType -> {
                  var annotation = subType.getAnnotation(TemplateSubType.class);
                  return annotation == null || !annotation.ignore();
                })
            .toList();
    var properties = new ArrayList<PropertyBuilder>();

    var discriminatorIdAndLabel =
        extractIdAndLabelFromAnnotationOrDeriveFromType(
            type,
            TemplateDiscriminatorProperty.class,
            prop -> {
              if (StringUtils.isBlank(prop.id())) {
                return prop.name();
              }
              return prop.id();
            },
            TemplateDiscriminatorProperty::label);

    Map<String, String> values = new LinkedHashMap<>();

    for (Class<?> subType : subTypes) {
      var subTypeIdAndName =
          extractIdAndLabelFromAnnotationOrDeriveFromType(
              subType, TemplateSubType.class, TemplateSubType::id, TemplateSubType::label);

      values.put(subTypeIdAndName.getKey(), subTypeIdAndName.getValue());

      var currentSubTypeProperties =
          extractTemplatePropertiesFromType(subType, context).stream()
              .peek(
                  property -> {
                    if (property.getCondition() == null) {
                      var condition =
                          new Equals(discriminatorIdAndLabel.getKey(), subTypeIdAndName.getKey());
                      property.condition(condition);
                    } else {
                      if (property.getCondition() instanceof AllMatch allMatch) {
                        var conditions = new ArrayList<>(allMatch.allMatch());
                        conditions.add(
                            new Equals(
                                discriminatorIdAndLabel.getKey(), subTypeIdAndName.getKey()));
                        property.condition(new AllMatch(conditions));
                      } else {
                        property.condition(
                            new AllMatch(
                                List.of(
                                    property.getCondition(),
                                    new Equals(
                                        discriminatorIdAndLabel.getKey(),
                                        subTypeIdAndName.getKey()))));
                      }
                    }
                  })
              .toList();

      properties.addAll(currentSubTypeProperties);
    }

    if (values.isEmpty()) {
      throw new IllegalStateException("Sealed type " + type + " has no subtypes");
    }

    var discriminatorAnnotation = type.getAnnotation(TemplateDiscriminatorProperty.class);

    String discriminatorBindingName;
    if (discriminatorAnnotation != null && !discriminatorAnnotation.name().isBlank()) {
      discriminatorBindingName = discriminatorAnnotation.name();
    } else {
      discriminatorBindingName = discriminatorIdAndLabel.getKey();
    }

    var discriminator =
        new DiscriminatorPropertyBuilder()
            .dependantProperties(properties)
            .choices(
                values.entrySet().stream()
                    .filter(Objects::nonNull)
                    .map(entry -> new DropdownChoice(entry.getValue(), entry.getKey()))
                    .collect(Collectors.toList()))
            .id(discriminatorIdAndLabel.getKey())
            .binding(createBinding(discriminatorBindingName, context))
            .group(
                discriminatorAnnotation == null || discriminatorAnnotation.group().isBlank()
                    ? null
                    : discriminatorAnnotation.group())
            .label(discriminatorIdAndLabel.getValue())
            .description(
                discriminatorAnnotation == null || discriminatorAnnotation.description().isBlank()
                    ? null
                    : discriminatorAnnotation.description())
            .value(
                discriminatorAnnotation == null || discriminatorAnnotation.defaultValue().isBlank()
                    ? null
                    : discriminatorAnnotation.defaultValue());

    var result = new ArrayList<>(List.of(discriminator));
    result.addAll(properties);
    return result;
  }

  private static <T extends Annotation>
      Map.Entry<String, String> extractIdAndLabelFromAnnotationOrDeriveFromType(
          Class<?> type,
          Class<T> annotationClass,
          Function<T, String> idExtractor,
          Function<T, String> labelExtractor) {

    var annotation = type.getAnnotation(annotationClass);
    if (annotation != null) {
      var id = idExtractor.apply(annotation);
      var name = labelExtractor.apply(annotation);
      if (name.isBlank()) {
        name = transformIdIntoLabel(type.getSimpleName());
      }
      return Map.entry(id, name);
    } else {
      return Map.entry(
          type.getSimpleName().toLowerCase(), transformIdIntoLabel(type.getSimpleName()));
    }
  }

  public static String transformIdIntoLabel(String id) {
    // uppercase ids are preserved
    if (id.toUpperCase().equals(id)) {
      return id;
    }
    // A simple attempt to transform camelCase into a normal sentence (first letter capitalized,
    // spaces)
    var label = new StringBuilder();
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (i == 0) {
        label.append(Character.toUpperCase(c));
      } else if (Character.isUpperCase(c)
          || (Character.isDigit(c) && !Character.isDigit(id.charAt(i - 1)))) {
        label.append(" ").append(Character.toLowerCase(c));
      } else {
        label.append(c);
      }
    }
    return label.toString();
  }

  private static boolean isContainerType(Field field) {
    var type = field.getType();
    var propertyAnnotation = field.getAnnotation(TemplateProperty.class);
    boolean hasManualTypeOverride =
        propertyAnnotation != null && propertyAnnotation.type() != PropertyType.Unknown;
    // true if object with fields, false if primitive or collection or map or array
    // or if the type has a manual type override
    return !ClassUtils.isPrimitiveOrWrapper(type)
        && !hasManualTypeOverride
        && !"java.time".equals(type.getPackageName())
        && type != Function.class
        && type != Supplier.class
        && type != String.class
        && type != Object.class
        && type != JsonNode.class
        && !type.isEnum()
        && !type.isArray()
        && !Collection.class.isAssignableFrom(type)
        && !Map.class.isAssignableFrom(type);
  }

  private static PropertyBinding createBinding(
      String propertyName, TemplateGenerationContext context) {
    if (context instanceof Outbound) {
      return new PropertyBinding.ZeebeInput(propertyName);
    } else {
      return new PropertyBinding.ZeebeProperty(propertyName);
    }
  }
}
