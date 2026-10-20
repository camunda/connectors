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

/** Enforces the naming conventions of the production classes. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class NamingConventionsArchTest {

  @ArchTest
  static final ArchRule IMPL_CLASSES_IMPLEMENT_THE_INTERFACE_WITHOUT_THE_SUFFIX =
      classes()
          .that()
          .haveSimpleNameEndingWith("Impl")
          .should(implementTheInterfaceWithoutTheSuffix())
          .because(
              "an Impl class is the default implementation of the interface it is named after");

  private static ArchCondition<JavaClass> implementTheInterfaceWithoutTheSuffix() {
    return new ArchCondition<>("implement the interface named like themselves without Impl") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final String simpleName = javaClass.getSimpleName();
        final String interfaceName = simpleName.substring(0, simpleName.length() - "Impl".length());
        final boolean implemented =
            javaClass.getAllRawInterfaces().stream()
                .anyMatch(type -> type.getSimpleName().equals(interfaceName));
        if (!implemented) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName() + " does not implement an interface named " + interfaceName));
        }
      }
    };
  }
}
