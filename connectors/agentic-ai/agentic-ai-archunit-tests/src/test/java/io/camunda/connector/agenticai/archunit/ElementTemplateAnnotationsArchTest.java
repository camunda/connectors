/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.Set;

/**
 * Enforces that the library modules (common, model, api and tools schema) carry no element template
 * annotations: the element template generator belongs to the connectors, which own the templates.
 *
 * <p>The allow list names the records that still carry element template annotations. It is the
 * follow-up of ADR 015, moving the annotated records to the connector, and must only shrink.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ElementTemplateAnnotationsArchTest {

  private static final String ELEMENT_TEMPLATE_GENERATOR = "io.camunda.connector.generator..";

  /** The top-level classes, including their nested classes, that may use the generator. */
  private static final Set<String> ANNOTATED_RECORDS_NOT_YET_MOVED_TO_THE_CONNECTOR =
      Set.of(
          AgenticAiModules.pkg("aiagent.model.request.PromptConfiguration"),
          AgenticAiModules.pkg("aiagent.model.request.LimitsConfiguration"),
          AgenticAiModules.pkg("aiagent.model.request.EventHandlingConfiguration"),
          AgenticAiModules.pkg("aiagent.model.request.ResponseFormatConfiguration"),
          AgenticAiModules.pkg("tools.schema.model.AdHocToolsSchemaRequest"));

  private static final DescribedPredicate<JavaClass> LIBRARY_CLASSES =
      Arrays.stream(AgenticAiModules.values())
          .filter(module -> module.kind() == AgenticAiModules.Kind.LIBRARY)
          .map(AgenticAiModules::root)
          .reduce((left, right) -> left.or(right))
          .orElseThrow()
          .as("reside in a library module");

  private static final DescribedPredicate<JavaClass> NOT_ON_THE_ALLOW_LIST =
      DescribedPredicate.describe(
          "are not on the allow list of annotated records",
          javaClass ->
              !ANNOTATED_RECORDS_NOT_YET_MOVED_TO_THE_CONNECTOR.contains(topLevelName(javaClass)));

  @ArchTest
  static final ArchRule LIBRARIES_DO_NOT_USE_THE_ELEMENT_TEMPLATE_GENERATOR =
      noClasses()
          .that(LIBRARY_CLASSES)
          .and(NOT_ON_THE_ALLOW_LIST)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ELEMENT_TEMPLATE_GENERATOR)
          .because(
              "the connectors own their element templates, so the libraries stay free of the"
                  + " generator");

  private static String topLevelName(final JavaClass javaClass) {
    JavaClass topLevel = javaClass;
    while (topLevel.getEnclosingClass().isPresent()) {
      topLevel = topLevel.getEnclosingClass().get();
    }
    return topLevel.getName();
  }
}
