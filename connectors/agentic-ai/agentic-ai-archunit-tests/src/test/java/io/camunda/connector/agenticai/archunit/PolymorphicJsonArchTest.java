/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/** Enforces that the polymorphic JSON of the Agentic AI modules is resolved by type name. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class PolymorphicJsonArchTest {

  private static final String JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo";
  private static final String JSON_TYPE_NAME = "com.fasterxml.jackson.annotation.JsonTypeName";
  private static final Set<String> CLASS_BASED_IDS = Set.of("CLASS", "MINIMAL_CLASS");
  private static final String CONVERSATION_CONTEXT =
      AgenticAiModules.pkg("aiagent.model.conversation.ConversationContext");

  @ArchTest
  static final ArchRule CLASSES_DO_NOT_USE_CLASS_BASED_TYPE_IDS =
      classes()
          .should(notUseClassBasedTypeIds())
          .because("class names in JSON couple the persisted data to the Java package structure");

  @ArchTest
  static final ArchRule MEMBERS_DO_NOT_USE_CLASS_BASED_TYPE_IDS =
      members()
          .should(notUseClassBasedTypeIds())
          .because("class names in JSON couple the persisted data to the Java package structure");

  @ArchTest
  static final ArchRule CONVERSATION_CONTEXT_IMPLEMENTATIONS_HAVE_A_TYPE_NAME =
      classes()
          .that()
          .implement(CONVERSATION_CONTEXT)
          .and()
          .areNotInterfaces()
          .and()
          .doNotHaveModifier(JavaModifier.ABSTRACT)
          .should()
          .beAnnotatedWith(JSON_TYPE_NAME)
          .because(
              "the conversation context is stored under its type name, which the stores use to"
                  + " read it back");

  private static <T extends HasName.AndFullName & HasAnnotations<?>>
      ArchCondition<T> notUseClassBasedTypeIds() {
    return new ArchCondition<>("not use @JsonTypeInfo with Id.CLASS or Id.MINIMAL_CLASS") {
      @Override
      public void check(final T item, final ConditionEvents events) {
        for (final JavaAnnotation<?> annotation : item.getAnnotations()) {
          if (!annotation.getRawType().getName().equals(JSON_TYPE_INFO)) {
            continue;
          }
          annotation
              .tryGetExplicitlyDeclaredProperty("use")
              .filter(use -> use instanceof JavaEnumConstant)
              .map(use -> ((JavaEnumConstant) use).name())
              .filter(CLASS_BASED_IDS::contains)
              .ifPresent(
                  id ->
                      events.add(
                          SimpleConditionEvent.violated(
                              item,
                              item.getFullName() + " uses @JsonTypeInfo(use = Id." + id + ")")));
        }
      }
    };
  }
}
