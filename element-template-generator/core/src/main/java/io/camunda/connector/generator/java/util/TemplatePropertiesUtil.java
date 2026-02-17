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

import static io.camunda.connector.util.reflection.ReflectionUtil.getAllFields;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.generator.dsl.*;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.OneOf;
import io.camunda.connector.generator.dsl.PropertyGroup.PropertyGroupBuilder;
import io.camunda.connector.generator.java.annotation.*;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.processor.AnnotationProcessor;
import io.camunda.connector.generator.java.processor.JakartaValidationAnnotationProcessor;
import io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Outbound;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

/** Utility class for transforming data classes into {@link PropertyBuilder} instances. */
public class TemplatePropertiesUtil {

  private static final List<AnnotationProcessor> fieldProcessors =
      List.of(
          new JakartaValidationAnnotationProcessor(), new TemplatePropertyAnnotationProcessor());

  public static List<PropertyBuilder> extractTemplatePropertiesFromParameter(
      Parameter parameter, TemplateGenerationContext context) {

    var type = parameter.getType();
    var annotation = parameter.getAnnotation(TemplateProperty.class);
    if (!isContainerType(parameter.getType(), annotation)) {
      // If the type is a primitive, String, Enum or Number, return a single property
      var property = buildProperty(parameter, parameter.getName(), type, context);
      if (property != null) {
        return List.of(property);
      } else {
        return Collections.emptyList();
      }
    }

    //
    return extractTemplatePropertiesFromType(type, context);
  }

  public static boolean shouldMapBindingsForParameter(Parameter parameter) {
    return isContainerType(parameter.getType(), parameter.getAnnotation(TemplateProperty.class));
  }

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

    if (type == Void.class) {
      // If the type is Void, return an empty list
      return Collections.emptyList();
    }

    if (type.isSealed()) {
      return handleSealedType(type, context);
    }

    var fields = getAllFields(type);
    var properties = new ArrayList<PropertyBuilder>();

    for (Field field : fields) {
      if (isContainerType(field.getType(), field.getAnnotation(TemplateProperty.class))) {
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
                              TemplatePropertyAnnotationProcessor.transformToCondition(
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
        properties.add(buildProperty(field, field.getName(), field.getType(), context));
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

  private static PropertyBuilder buildProperty(
      AnnotatedElement declaredProperty,
      String declaredName,
      Class<?> type,
      TemplateGenerationContext context) {

    var templatePropertyAnnotation = declaredProperty.getAnnotation(TemplateProperty.class);
    var variableAnnotation = declaredProperty.getAnnotation(Variable.class);
    var headerAnnotation = declaredProperty.getAnnotation(Header.class);

    String name, label, tooltip = null, exampleValue = null;
    String bindingName = declaredName;
    if (templatePropertyAnnotation != null) {
      if (templatePropertyAnnotation.ignore()) {
        return null;
      }
      if (!templatePropertyAnnotation.id().isBlank()) {
        name = templatePropertyAnnotation.id();
      } else {
        name = declaredName;
      }
      if (!templatePropertyAnnotation.label().isBlank()) {
        label = templatePropertyAnnotation.label();
      } else {
        label = transformIdIntoLabel(name);
      }
      if (!templatePropertyAnnotation.binding().name().isBlank()) {
        bindingName = templatePropertyAnnotation.binding().name();
      }
      if (!templatePropertyAnnotation.tooltip().isBlank()) {
        tooltip = templatePropertyAnnotation.tooltip();
      }
      if (!templatePropertyAnnotation.exampleValue().isBlank()) {
        exampleValue = templatePropertyAnnotation.exampleValue();
      }
    } else {
      name = declaredName;
      label = transformIdIntoLabel(name);
    }

    if (variableAnnotation != null) {
      if (!variableAnnotation.name().isBlank()) {
        name = variableAnnotation.name();
      } else if (!variableAnnotation.value().isBlank()) {
        name = variableAnnotation.value();
      }
      label = transformIdIntoLabel(name);
      bindingName = name;
    } else if (headerAnnotation != null) {
      if (!headerAnnotation.name().isBlank()) {
        bindingName = headerAnnotation.name();
      } else if (!headerAnnotation.value().isBlank()) {
        bindingName = headerAnnotation.value();
      }
      label = transformIdIntoLabel(name);
    }

    PropertyBuilder propertyBuilder =
        createPropertyBuilder(type, templatePropertyAnnotation, context)
            .id(name)
            .label(label)
            .tooltip(tooltip)
            .exampleValue(exampleValue)
            .binding(createBinding(bindingName, context));

    for (AnnotationProcessor processor : fieldProcessors) {
      processor.process(declaredProperty, type, propertyBuilder, context);
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

  private static List<DropdownModel> createDropdownModelList(Class<?> enumType) {
    return Arrays.stream(enumType.getEnumConstants())
        .map(
            enumConstant -> {
              try {
                Field enumValue = enumType.getField(((Enum<?>) enumConstant).name());
                if (enumValue.isAnnotationPresent(DropdownItem.class)) {
                  DropdownItem enumLabel = enumValue.getAnnotation(DropdownItem.class);
                  return new DropdownModel(
                      enumLabel.label(), enumValue.getName(), enumLabel.order());
                } else {
                  return new DropdownModel(
                      transformIdIntoLabel(((Enum<?>) enumConstant).name()),
                      ((Enum<?>) enumConstant).name(),
                      0);
                }
              } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  public static boolean isNumberClass(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    if (clazz.isPrimitive()) {
      return clazz == int.class
          || clazz == long.class
          || clazz == double.class
          || clazz == float.class
          || clazz == short.class;
    }
    return Number.class.isAssignableFrom(clazz);
  }

  private static PropertyBuilder createPropertyBuilder(
      Class<?> parameterType, TemplateProperty annotation, TemplateGenerationContext context) {
    PropertyType type;
    List<DropdownModel> dropdownChoices = new ArrayList<>();

    if (parameterType == Boolean.class || parameterType == boolean.class) {
      type = PropertyType.Boolean;
    } else if (parameterType.isEnum()) {
      type = PropertyType.Dropdown;
      dropdownChoices = createDropdownModelList(parameterType);
    } else if (isNumberClass(parameterType) && isOutbound(context)) { // TODO: To be removed
      type = PropertyType.Number;
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
                .map(
                    dropdownPropertyChoice ->
                        new DropdownModel(
                            dropdownPropertyChoice.label(), dropdownPropertyChoice.value(), 0))
                .toList();
      }
    }

    List<DropdownChoice> dropdownChoiceList =
        dropdownChoices.stream()
            .sorted(Comparator.comparingInt(DropdownModel::order))
            .map(dropdownModel -> new DropdownChoice(dropdownModel.label(), dropdownModel.value()))
            .toList();

    var builder =
        switch (type) {
          case Boolean -> BooleanProperty.builder();
          case Number -> NumberProperty.builder();
          case Dropdown ->
              DropdownProperty.builder().choices(dropdownChoiceList).feel(FeelMode.disabled);
          case Hidden -> HiddenProperty.builder();
          case String -> StringProperty.builder();
          case Text -> TextProperty.builder();
          case Unknown -> throw new IllegalStateException("Unknown property type");
        };
    if (Object.class.equals(parameterType)
        || JsonNode.class.equals(parameterType)
        || Collection.class.isAssignableFrom(parameterType)
        || Map.class.isAssignableFrom(parameterType)) {
      builder.feel(FeelMode.required);
    }
    return builder;
  }

  public static boolean isOutbound(TemplateGenerationContext context) {
    return switch (context) {
      case TemplateGenerationContext.Inbound unused -> false;
      case Outbound unused -> true;
    };
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

  private static boolean isContainerType(Class<?> type, TemplateProperty propertyAnnotation) {
    boolean hasManualTypeOverride =
        propertyAnnotation != null && propertyAnnotation.type() != PropertyType.Unknown;
    // true if object with fields, false if primitive or collection or map or array
    // or if the type has a manual type override
    return !ClassUtils.isPrimitiveOrWrapper(type)
        && !hasManualTypeOverride
        && !"java.time".equals(type.getPackageName())
        && type != Date.class
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
      return new ZeebeInput(propertyName);
    } else {
      return new ZeebeProperty(propertyName);
    }
  }
}
