/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforces that every package is null-marked. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class NullMarkedPackagesArchTest {

  private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";

  @ArchTest
  static final ArchRule EVERY_PACKAGE_IS_NULL_MARKED =
      classes()
          .should(resideInANullMarkedPackage())
          .because(
              "NullAway checks only null-marked code, and @NullMarked does not apply to subpackages");

  private static ArchCondition<JavaClass> resideInANullMarkedPackage() {
    return new ArchCondition<>("reside in a package annotated with @NullMarked") {
      private final Set<String> reportedPackages = new HashSet<>();

      @Override
      public void init(final Collection<JavaClass> allObjectsToTest) {
        reportedPackages.clear();
      }

      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final var javaPackage = javaClass.getPackage();
        if (!javaPackage.isAnnotatedWith(NULL_MARKED)
            && reportedPackages.add(javaPackage.getName())) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  "Package "
                      + javaPackage.getName()
                      + " has no package-info annotated with @NullMarked"));
        }
      }
    };
  }
}
