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
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Keeps every package root in a single module: the classes of a package root come from the module
 * that owns it, and a module's build output contains only classes of its own package root.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ModulePackageOwnershipArchTest {

  @ArchTest
  static final ArchRule PACKAGE_ROOTS_RESIDE_IN_THEIR_OWNING_MODULE =
      allModules(
          module ->
              classes()
                  .that(module.root())
                  .should(beLoadedFrom(module))
                  .because(
                      "a package must not be split across modules; the package root "
                          + module.layerName()
                          + " belongs to "
                          + module.artifactId()));

  @ArchTest
  static final ArchRule MODULES_CONTAIN_ONLY_THEIR_OWN_PACKAGE_ROOT =
      allModules(
          module ->
              classes()
                  .that(module.loadedFromModule())
                  .should(ArchCondition.from(module.root()))
                  .because(
                      module.artifactId() + " contains only the classes of its own package root"));

  private static ArchRule allModules(final Function<AgenticAiModules, ArchRule> ruleForModule) {
    final var rules = Arrays.stream(AgenticAiModules.values()).map(ruleForModule).toList();
    var composite = CompositeArchRule.of(rules.get(0));
    for (final var rule : rules.subList(1, rules.size())) {
      composite = composite.and(rule);
    }
    return composite;
  }

  private static ArchCondition<JavaClass> beLoadedFrom(final AgenticAiModules module) {
    return new ArchCondition<>("be loaded from module " + module.artifactId()) {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final var source = javaClass.getSource();
        if (source.isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName() + " has no source location to determine its module"));
          return;
        }
        final var actual = AgenticAiModules.moduleOf(javaClass);
        if (actual.filter(module::equals).isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " is loaded from "
                      + actual.map(AgenticAiModules::artifactId).orElse("an unknown module")
                      + " ("
                      + source.get().getUri()
                      + ")"));
        }
      }
    };
  }
}
