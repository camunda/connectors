/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.stream.Stream;

/** Enforces the Spring scope of the beans that provide outbound connector functions. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class OutboundConnectorBeanScopeArchTest {

  private static final String BEAN = "org.springframework.context.annotation.Bean";
  private static final String SCOPE = "org.springframework.context.annotation.Scope";
  private static final String PROTOTYPE = "prototype";
  private static final String OUTBOUND_CONNECTOR =
      "io.camunda.connector.api.annotation.OutboundConnector";
  private static final String OUTBOUND_CONNECTOR_FUNCTION =
      "io.camunda.connector.api.outbound.OutboundConnectorFunction";

  private static final DescribedPredicate<JavaClass> OUTBOUND_CONNECTOR_FUNCTIONS =
      DescribedPredicate.describe(
          "outbound connector functions",
          javaClass ->
              javaClass.isAnnotatedWith(OUTBOUND_CONNECTOR)
                  || assignableTo(OUTBOUND_CONNECTOR_FUNCTION).test(javaClass));

  @ArchTest
  static final ArchRule OUTBOUND_CONNECTOR_BEANS_ARE_PROTOTYPE_SCOPED =
      methods()
          .that()
          .areAnnotatedWith(BEAN)
          .and()
          .haveRawReturnType(OUTBOUND_CONNECTOR_FUNCTIONS)
          .should(bePrototypeScoped())
          .because(
              "the connector runtime resolves a prototype-scoped bean through its bean definition,"
                  + " but creates the instances of a singleton-scoped bean"
                  + " from the bean class alone, bypassing the @Bean method");

  private static ArchCondition<JavaMethod> bePrototypeScoped() {
    return new ArchCondition<>("be annotated with @Scope(\"" + PROTOTYPE + "\")") {
      @Override
      public void check(final JavaMethod method, final ConditionEvents events) {
        final boolean prototype =
            method
                .tryGetAnnotationOfType(SCOPE)
                .map(
                    scope ->
                        Stream.concat(
                                AnnotationProperties.strings(scope, "value").stream(),
                                AnnotationProperties.strings(scope, "scopeName").stream())
                            .anyMatch(PROTOTYPE::equals))
                .orElse(false);
        if (!prototype) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName() + " is not annotated with @Scope(\"" + PROTOTYPE + "\")"));
        }
      }
    };
  }
}
