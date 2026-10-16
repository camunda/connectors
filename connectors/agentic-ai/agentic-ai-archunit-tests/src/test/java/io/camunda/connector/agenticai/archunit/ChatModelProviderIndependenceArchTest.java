/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SliceRule;
import java.util.List;
import java.util.Set;

/**
 * Enforces the independence of the chat model providers: each vendor package below the provider
 * root is a slice, and the slices do not depend on each other except for the explicitly allowed
 * edges. The rest of the AI agent reaches the providers only through the chat model API.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ChatModelProviderIndependenceArchTest {

  private static final String PROVIDER_ROOT = AgenticAiModules.pkg("aiagent.chatmodel.provider");

  /**
   * Packages below the provider root that are not vendor slices: support shared by all providers
   * ({@code azure}) and the Spring wiring of the providers ({@code configuration}). Classes in the
   * provider root package itself are shared support as well.
   */
  private static final Set<String> NON_VENDOR_PACKAGES = Set.of("azure", "configuration");

  /** A vendor provider that is allowed to use the classes of another one. */
  private record AllowedDependency(String provider, String dependsOnProvider) {}

  /** To allow another dependency between two providers, add it here. */
  private static final List<AllowedDependency> ALLOWED_DEPENDENCIES =
      List.of(
          // Mistral reuses the OpenAI-compatible wire mapping.
          new AllowedDependency("mistral", "openai"));

  private static final SliceAssignment VENDOR_PROVIDERS =
      new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(final JavaClass javaClass) {
          final String packageName = javaClass.getPackageName();
          if (!packageName.startsWith(PROVIDER_ROOT + ".")) {
            return SliceIdentifier.ignore();
          }
          final String relativePackage = packageName.substring(PROVIDER_ROOT.length() + 1);
          final int end = relativePackage.indexOf('.');
          final String provider = end < 0 ? relativePackage : relativePackage.substring(0, end);
          return NON_VENDOR_PACKAGES.contains(provider)
              ? SliceIdentifier.ignore()
              : SliceIdentifier.of(provider);
        }

        @Override
        public String getDescription() {
          return "vendor providers below " + PROVIDER_ROOT;
        }
      };

  @ArchTest
  static final ArchRule CHAT_MODEL_PROVIDERS_DO_NOT_DEPEND_ON_EACH_OTHER = providersIndependent();

  @ArchTest
  static final ArchRule THE_AI_AGENT_DOES_NOT_DEPEND_ON_THE_CHAT_MODEL_PROVIDERS =
      noClasses()
          .that(AgenticAiModules.AI_AGENT.root())
          .and()
          .resideOutsideOfPackages(
              PROVIDER_ROOT + "..", AgenticAiModules.pkg("aiagent.configuration.."))
          .should()
          .dependOnClassesThat()
          .resideInAPackage(PROVIDER_ROOT + "..")
          .because("the AI agent reaches the chat model providers only through the chat model API");

  private static ArchRule providersIndependent() {
    SliceRule rule =
        slices()
            .assignedFrom(VENDOR_PROVIDERS)
            .should()
            .notDependOnEachOther()
            .because("the chat model providers can be extracted into their own modules");
    for (final var allowed : ALLOWED_DEPENDENCIES) {
      rule =
          rule.ignoreDependency(
              resideInAPackage(PROVIDER_ROOT + "." + allowed.provider() + ".."),
              resideInAPackage(PROVIDER_ROOT + "." + allowed.dependsOnProvider() + ".."));
    }
    return rule;
  }
}
