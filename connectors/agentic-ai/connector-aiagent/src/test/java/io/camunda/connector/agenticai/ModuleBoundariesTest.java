/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enforces the package-level boundaries between the agentic AI modules on the test classpath: the
 * shared library ({@code common}), the MCP and A2A client connectors, their AI Agent integrations,
 * and the AI Agent connector itself.
 */
class ModuleBoundariesTest {

  private static final String ROOT = "io.camunda.connector.agenticai";

  private static final String COMMON = ROOT + ".common..";
  private static final String A2A = ROOT + ".a2a..";
  private static final String A2A_CLIENT = ROOT + ".a2a.client..";
  private static final String A2A_INTEGRATION = ROOT + ".a2a.integration..";
  private static final String MCP = ROOT + ".mcp..";
  private static final String MCP_CLIENT = ROOT + ".mcp.client..";
  private static final String MCP_INTEGRATION = ROOT + ".mcp.integration..";
  private static final String AUTOCONFIGURE = ROOT + ".autoconfigure..";
  private static final String AGENT = ROOT + ".aiagent.agent..";
  private static final String CHAT_MODEL_PROVIDER = ROOT + ".aiagent.chatmodel.provider..";
  private static final String CONVERSATION_INPROCESS =
      ROOT + ".aiagent.memory.conversation.inprocess..";
  private static final String CONVERSATION_DOCUMENT =
      ROOT + ".aiagent.memory.conversation.document..";
  private static final String CONVERSATION_AWS_AGENTCORE =
      ROOT + ".aiagent.memory.conversation.awsagentcore..";
  private static final String TOOLS_SCHEMA = ROOT + ".tools.schema..";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  @Test
  void commonLibraryDependsOnNoOtherFeatureArea() {
    noClasses()
        .that()
        .resideInAPackage(COMMON)
        .should()
        .dependOnClassesThat(agenticAiClassesOutside(COMMON))
        .because("the common library is the base of every module and depends on nothing else")
        .check(classes);
  }

  @Test
  void a2aClientDependsOnlyOnCommon() {
    classes()
        .that()
        .resideInAPackage(A2A_CLIENT)
        .should()
        .onlyDependOnClassesThat(outsideAgenticAiOr(A2A_CLIENT, COMMON))
        .because("the A2A client connector depends only on the common library")
        .check(classes);
  }

  @Test
  void mcpClientDependsOnlyOnCommon() {
    classes()
        .that()
        .resideInAPackage(MCP_CLIENT)
        .should()
        .onlyDependOnClassesThat(outsideAgenticAiOr(MCP_CLIENT, COMMON))
        .because("the MCP client connector depends only on the common library")
        .check(classes);
  }

  @Test
  void integrationsDoNotDependOnAiAgentConnectorImplementation() {
    noClasses()
        .that()
        .resideInAnyPackage(A2A_INTEGRATION, MCP_INTEGRATION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            AUTOCONFIGURE,
            AGENT,
            CONVERSATION_INPROCESS,
            CONVERSATION_DOCUMENT,
            CONVERSATION_AWS_AGENTCORE,
            CHAT_MODEL_PROVIDER)
        .because(
            "the integrations ship in their own module, which must not depend on the AI Agent"
                + " connector implementation")
        .check(classes);
  }

  @Test
  void a2aIntegrationDoesNotDependOnMcp() {
    noClasses()
        .that()
        .resideInAPackage(A2A_INTEGRATION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(MCP)
        .because("the A2A integration module must not pull in MCP")
        .check(classes);
  }

  @Test
  void mcpIntegrationDoesNotDependOnA2a() {
    noClasses()
        .that()
        .resideInAPackage(MCP_INTEGRATION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(A2A)
        .because("the MCP integration module must not pull in A2A")
        .check(classes);
  }

  @Test
  void autoConfigurationDoesNotDependOnA2aOrMcp() {
    noClasses()
        .that()
        .resideInAPackage(AUTOCONFIGURE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(A2A, MCP)
        .because(
            "the AI Agent auto-configuration lives in a module that does not include A2A or MCP")
        .check(classes);
  }

  @Test
  void toolsSchemaDoesNotDependOnConnectorImplementations() {
    noClasses()
        .that()
        .resideInAPackage(TOOLS_SCHEMA)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(AUTOCONFIGURE, A2A, MCP, AGENT, CHAT_MODEL_PROVIDER)
        .because("the tools schema library is shared and independent of the connectors using it")
        .check(classes);
  }

  @Test
  void onlyIntegrationsDependOnClientConnectors() {
    noClasses()
        .that()
        .resideOutsideOfPackages(A2A, MCP)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(A2A_CLIENT, MCP_CLIENT)
        .because("only the A2A and MCP integrations depend on the client connectors")
        .check(classes);
  }

  private static DescribedPredicate<JavaClass> agenticAiClassesOutside(String packageIdentifier) {
    return resideInAnyPackage(ROOT + "..").and(not(resideInAnyPackage(packageIdentifier)));
  }

  private static DescribedPredicate<JavaClass> outsideAgenticAiOr(
      String... allowedPackageIdentifiers) {
    return resideOutsideOfPackage(ROOT + "..").or(resideInAnyPackage(allowedPackageIdentifiers));
  }
}
