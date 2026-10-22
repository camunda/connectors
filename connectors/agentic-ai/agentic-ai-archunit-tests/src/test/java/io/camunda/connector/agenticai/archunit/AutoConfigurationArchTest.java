/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Enforces the conventions of the Spring Boot auto-configurations of the Agentic AI modules. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class AutoConfigurationArchTest {

  private static final String AUTO_CONFIGURATION =
      "org.springframework.boot.autoconfigure.AutoConfiguration";
  private static final String CONDITIONAL_ON_BOOLEAN_PROPERTY =
      "org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty";
  private static final String ENABLED_PROPERTY = "camunda.connector.agenticai.enabled";
  private static final String IMPORTS_RESOURCE =
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

  private static final DescribedPredicate<JavaClass> AUTO_CONFIGURATIONS =
      DescribedPredicate.describe(
          "are annotated with @AutoConfiguration", javaClass -> isAutoConfiguration(javaClass));

  @ArchTest
  static final ArchRule AUTO_CONFIGURATIONS_FOLLOW_THE_PACKAGE_AND_NAMING_CONVENTION =
      classes()
          .that(AUTO_CONFIGURATIONS)
          .should()
          .resideInAPackage("..configuration")
          .andShould()
          .haveSimpleNameEndingWith("AutoConfiguration")
          .because(
              "every module keeps its Spring wiring in a configuration package, and the name tells"
                  + " auto-configurations, which users can exclude, from imported configuration"
                  + " classes");

  @ArchTest
  static final ArchRule AUTO_CONFIGURATIONS_CAN_BE_SWITCHED_OFF_AS_A_WHOLE =
      classes()
          .that(AUTO_CONFIGURATIONS)
          .should(beEnabledByDefaultThroughTheAgenticAiSwitch())
          .because(
              "camunda.connector.agenticai.enabled=false disables all Agentic AI modules at once");

  @ArchTest
  static final ArchRule AUTO_CONFIGURATIONS_ARE_REGISTERED_IN_THEIR_MODULE =
      classes()
          .that(AUTO_CONFIGURATIONS)
          .should(beListedInTheImportsResourceOfTheirModule())
          .because("Spring Boot only loads the auto-configurations listed in the imports resource");

  @ArchTest
  static final ArchRule REGISTERED_AUTO_CONFIGURATIONS_RESOLVE_TO_AUTO_CONFIGURATION_CLASSES =
      classes()
          .should(resolveEveryRegisteredEntryToAnAutoConfiguration())
          .because("a stale entry in an imports resource fails the application start");

  private static boolean isAutoConfiguration(final JavaClass javaClass) {
    return javaClass.isAnnotatedWith(AUTO_CONFIGURATION);
  }

  private static ArchCondition<JavaClass> beEnabledByDefaultThroughTheAgenticAiSwitch() {
    return new ArchCondition<>(
        "be annotated with @ConditionalOnBooleanProperty on "
            + ENABLED_PROPERTY
            + " with"
            + " matchIfMissing = true") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final boolean satisfied =
            javaClass
                .tryGetAnnotationOfType(CONDITIONAL_ON_BOOLEAN_PROPERTY)
                .map(
                    annotation -> {
                      final String prefix =
                          AnnotationProperties.strings(annotation, "prefix").stream()
                              .findFirst()
                              .map(value -> value.isEmpty() ? "" : value + ".")
                              .orElse("");
                      final Set<String> keys = new HashSet<>();
                      AnnotationProperties.strings(annotation, "value")
                          .forEach(key -> keys.add(prefix + key));
                      AnnotationProperties.strings(annotation, "name")
                          .forEach(key -> keys.add(prefix + key));
                      return keys.contains(ENABLED_PROPERTY)
                          && AnnotationProperties.bool(annotation, "matchIfMissing");
                    })
                .orElse(false);
        if (!satisfied) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " is not annotated with @ConditionalOnBooleanProperty on "
                      + ENABLED_PROPERTY
                      + " with matchIfMissing = true"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> beListedInTheImportsResourceOfTheirModule() {
    return new ArchCondition<>("be listed in the " + IMPORTS_RESOURCE + " of their module") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final Optional<URI> source = javaClass.getSource().map(s -> s.getUri());
        if (source.isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getName() + " has no known code source"));
          return;
        }
        final String classFile = javaClass.getName().replace('.', '/') + ".class";
        final String location = source.get().toString();
        final String resource =
            location.substring(0, location.length() - classFile.length()) + IMPORTS_RESOURCE;
        final Set<String> entries;
        try {
          entries = readEntries(URI.create(resource).toURL());
        } catch (final IOException e) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName() + " is loaded from a module without " + IMPORTS_RESOURCE));
          return;
        }
        if (!entries.contains(javaClass.getName())) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " is missing in "
                      + resource
                      + " (entries: "
                      + entries
                      + ")"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> resolveEveryRegisteredEntryToAnAutoConfiguration() {
    return new ArchCondition<>(
        "be the target of every Agentic AI entry in the imports resources, as @AutoConfiguration"
            + " classes") {
      private final Set<String> autoConfigurations = new HashSet<>();

      @Override
      public void init(final Collection<JavaClass> allObjectsToTest) {
        autoConfigurations.clear();
        allObjectsToTest.stream()
            .filter(AutoConfigurationArchTest::isAutoConfiguration)
            .forEach(javaClass -> autoConfigurations.add(javaClass.getName()));
      }

      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        // all checks happen in finish(), once every class is known
      }

      @Override
      public void finish(final ConditionEvents events) {
        try {
          final Enumeration<URL> resources =
              AutoConfigurationArchTest.class.getClassLoader().getResources(IMPORTS_RESOURCE);
          while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            for (final String entry : readEntries(resource)) {
              if (entry.startsWith(AgenticAiModules.ROOT + ".")
                  && !autoConfigurations.contains(entry)) {
                events.add(
                    SimpleConditionEvent.violated(
                        resource,
                        resource
                            + " lists "
                            + entry
                            + ", which is not an @AutoConfiguration class"));
              }
            }
          }
        } catch (final IOException e) {
          throw new IllegalStateException("Cannot read the " + IMPORTS_RESOURCE + " resources", e);
        }
      }
    };
  }

  private static Set<String> readEntries(final URL resource) throws IOException {
    final Set<String> entries = new LinkedHashSet<>();
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
      for (final String line : (Iterable<String>) reader.lines()::iterator) {
        final String entry = line.strip();
        if (!entry.isEmpty() && !entry.startsWith("#")) {
          entries.add(entry);
        }
      }
    }
    return entries;
  }
}
