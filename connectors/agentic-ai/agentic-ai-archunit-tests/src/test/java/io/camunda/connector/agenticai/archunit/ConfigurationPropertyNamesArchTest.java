/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the naming of the configuration properties: they share one namespace and use canonical
 * kebab-case, the only spelling Spring Boot's relaxed binding resolves reliably from every property
 * source.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ConfigurationPropertyNamesArchTest {

  private static final String CONFIGURATION_PROPERTIES =
      "org.springframework.boot.context.properties.ConfigurationProperties";
  private static final String CONDITIONAL_ON_PROPERTY =
      "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty";
  private static final String CONDITIONAL_ON_BOOLEAN_PROPERTY =
      "org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty";
  private static final String VALUE = "org.springframework.beans.factory.annotation.Value";

  private static final String PROPERTIES_NAMESPACE = "camunda.connector.agenticai";

  /**
   * Lowercase letters, digits, dots and hyphens, as for the property keys in Camunda's monorepo.
   */
  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9.-]+");

  /** A {@code ${key:default}} placeholder; group 1 is the key. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)[^}]*}");

  @ArchTest
  static final ArchRule CONFIGURATION_PROPERTIES_PREFIXES_USE_KEBAB_CASE =
      classes()
          .that()
          .areAnnotatedWith(CONFIGURATION_PROPERTIES)
          .should(
              haveKebabCaseKeys(
                  "use kebab-case in the @ConfigurationProperties prefix",
                  (JavaClass javaClass) ->
                      attributes(
                          javaClass.getAnnotations(), CONFIGURATION_PROPERTIES, "prefix", "value")))
          .because("relaxed binding resolves only kebab-case prefixes from every property source");

  @ArchTest
  static final ArchRule CONFIGURATION_PROPERTIES_PREFIXES_USE_THE_AGENTIC_AI_NAMESPACE =
      classes()
          .that()
          .areAnnotatedWith(CONFIGURATION_PROPERTIES)
          .should(
              new ArchCondition<JavaClass>(
                  "use the prefix " + PROPERTIES_NAMESPACE + " or one below it") {
                @Override
                public void check(final JavaClass javaClass, final ConditionEvents events) {
                  for (final String prefix :
                      attributes(
                          javaClass.getAnnotations(),
                          CONFIGURATION_PROPERTIES,
                          "prefix",
                          "value")) {
                    if (!prefix.equals(PROPERTIES_NAMESPACE)
                        && !prefix.startsWith(PROPERTIES_NAMESPACE + ".")) {
                      events.add(
                          SimpleConditionEvent.violated(
                              javaClass,
                              javaClass.getFullName()
                                  + " uses the @ConfigurationProperties prefix '"
                                  + prefix
                                  + "'"));
                    }
                  }
                }
              })
          .because("the agentic AI configuration properties share one namespace");

  @ArchTest
  static final ArchRule PROPERTY_CONDITIONS_ON_CLASSES_USE_KEBAB_CASE_KEYS =
      classes()
          .that()
          .areAnnotatedWith(CONDITIONAL_ON_PROPERTY)
          .or()
          .areAnnotatedWith(CONDITIONAL_ON_BOOLEAN_PROPERTY)
          .should(
              haveKebabCaseKeys(
                  "use kebab-case in the keys of the property conditions",
                  (JavaClass javaClass) -> conditionKeys(javaClass.getAnnotations())))
          .because(
              "a key that is not kebab-case silently matches no property and falls back to"
                  + " matchIfMissing");

  @ArchTest
  static final ArchRule PROPERTY_CONDITIONS_ON_METHODS_USE_KEBAB_CASE_KEYS =
      methods()
          .that()
          .areAnnotatedWith(CONDITIONAL_ON_PROPERTY)
          .or()
          .areAnnotatedWith(CONDITIONAL_ON_BOOLEAN_PROPERTY)
          .should(
              haveKebabCaseKeys(
                  "use kebab-case in the keys of the property conditions",
                  (JavaMethod method) -> conditionKeys(method.getAnnotations())))
          .because(
              "a key that is not kebab-case silently matches no property and falls back to"
                  + " matchIfMissing");

  @ArchTest
  static final ArchRule VALUE_PLACEHOLDERS_ON_FIELDS_USE_KEBAB_CASE_KEYS =
      fields()
          .should(
              haveKebabCaseKeys(
                  "use kebab-case in the @Value placeholders",
                  (JavaField field) -> placeholderKeys(field.getAnnotations())))
          .because("relaxed binding resolves only kebab-case keys from every property source");

  @ArchTest
  static final ArchRule VALUE_PLACEHOLDERS_ON_PARAMETERS_USE_KEBAB_CASE_KEYS =
      codeUnits()
          .should(
              haveKebabCaseKeys(
                  "use kebab-case in the @Value placeholders of their parameters",
                  (JavaCodeUnit codeUnit) -> {
                    final List<String> keys = new ArrayList<>();
                    for (final JavaParameter parameter : codeUnit.getParameters()) {
                      keys.addAll(placeholderKeys(parameter.getAnnotations()));
                    }
                    return keys;
                  }))
          .because("relaxed binding resolves only kebab-case keys from every property source");

  private static List<String> attributes(
      final Collection<? extends JavaAnnotation<?>> annotations,
      final String annotationType,
      final String... attributes) {
    final List<String> values = new ArrayList<>();
    for (final JavaAnnotation<?> annotation : annotations) {
      if (annotation.getRawType().getName().equals(annotationType)) {
        for (final String attribute : attributes) {
          values.addAll(AnnotationProperties.strings(annotation, attribute));
        }
      }
    }
    return values;
  }

  private static List<String> conditionKeys(
      final Collection<? extends JavaAnnotation<?>> annotations) {
    final List<String> keys = new ArrayList<>();
    for (final String type : List.of(CONDITIONAL_ON_PROPERTY, CONDITIONAL_ON_BOOLEAN_PROPERTY)) {
      keys.addAll(attributes(annotations, type, "prefix", "name", "value"));
    }
    return keys;
  }

  private static List<String> placeholderKeys(
      final Collection<? extends JavaAnnotation<?>> annotations) {
    final List<String> keys = new ArrayList<>();
    for (final String value : attributes(annotations, VALUE, "value")) {
      final Matcher matcher = PLACEHOLDER.matcher(value);
      while (matcher.find()) {
        keys.add(matcher.group(1));
      }
    }
    return keys;
  }

  private static <T extends HasName.AndFullName & HasAnnotations<?>>
      ArchCondition<T> haveKebabCaseKeys(
          final String description, final Function<T, List<String>> keys) {
    return new ArchCondition<>(description) {
      @Override
      public void check(final T item, final ConditionEvents events) {
        for (final String key : keys.apply(item)) {
          if (!KEBAB_CASE.matcher(key).matches()) {
            events.add(
                SimpleConditionEvent.violated(
                    item, item.getFullName() + " uses the property key '" + key + "'"));
          }
        }
      }
    };
  }
}
