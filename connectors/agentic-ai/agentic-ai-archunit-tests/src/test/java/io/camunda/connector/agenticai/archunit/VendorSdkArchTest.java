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
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforces that each vendor SDK is only used by the modules that integrate it: the chat model
 * provider, the conversation store, the HTTP proxy support, or the client of the protocol. The
 * connector sees all SDKs on its class path through the provider modules, so the modules' own
 * dependencies do not keep the SDKs out of it.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class VendorSdkArchTest {

  private static final List<VendorSdk> VENDOR_SDKS =
      List.of(
          new VendorSdk(
              "Anthropic SDK",
              "com.anthropic..",
              "the Anthropic provider is a module of its own",
              AgenticAiModules.PROVIDER_ANTHROPIC),
          // Mistral uses the OpenAI SDK through its OpenAI-compatible wire mapping.
          new VendorSdk(
              "OpenAI SDK",
              "com.openai..",
              "the OpenAI-compatible providers are modules of their own",
              AgenticAiModules.PROVIDER_OPENAI,
              AgenticAiModules.PROVIDER_MISTRAL),
          new VendorSdk(
              "Gemini SDK",
              "com.google.genai..",
              "the Gemini provider is a module of its own",
              AgenticAiModules.PROVIDER_GEMINI),
          new VendorSdk(
              "AWS Bedrock runtime SDK",
              "software.amazon.awssdk.services.bedrockruntime..",
              "the Bedrock provider is a module of its own",
              AgenticAiModules.PROVIDER_BEDROCK),
          new VendorSdk(
              "AWS AgentCore SDK",
              "software.amazon.awssdk.services.bedrockagentcore..",
              "the AWS AgentCore conversation store is a module of its own",
              AgenticAiModules.MEMORY_AGENTCORE),
          new VendorSdk(
              "AWS HTTP client SDK",
              "software.amazon.awssdk.http..",
              "the AWS HTTP client setup is part of the AWS support and the Bedrock provider",
              AgenticAiModules.AWS_SUPPORT,
              AgenticAiModules.PROVIDER_BEDROCK),
          new VendorSdk(
              "Azure HTTP SDK",
              "com.azure.core.http..",
              "the Azure HTTP client setup is part of the Azure support",
              AgenticAiModules.AZURE_SUPPORT),
          new VendorSdk(
              "LangChain4j",
              "dev.langchain4j..",
              "the LangChain4j bridge is a module of its own",
              AgenticAiModules.PROVIDER_LANGCHAIN4J),
          new VendorSdk(
              "A2A SDK",
              "io.a2a..",
              "the A2A client is a module of its own",
              AgenticAiModules.A2A_CLIENT),
          new VendorSdk(
              "MCP SDK",
              "io.modelcontextprotocol..",
              "the MCP client is a module of its own",
              AgenticAiModules.MCP_CLIENT));

  /** One rule per vendor SDK, each failing with a message that names the SDK. */
  @ArchTest
  static final ArchRule VENDOR_SDKS_ARE_ONLY_USED_BY_THEIR_INTEGRATION_MODULES = vendorSdkRules();

  private static ArchRule vendorSdkRules() {
    CompositeArchRule rules = null;
    for (final VendorSdk vendorSdk : VENDOR_SDKS) {
      rules = rules == null ? CompositeArchRule.of(vendorSdk.rule()) : rules.and(vendorSdk.rule());
    }
    return rules;
  }

  /**
   * A vendor SDK, the modules that may use it, and why only those modules may.
   *
   * @param name the name of the SDK in the failure message
   * @param sdkPackage the package of the SDK
   * @param reason why the SDK is confined to the allowed modules
   * @param allowedModules the modules that may depend on the SDK
   */
  private record VendorSdk(
      String name, String sdkPackage, String reason, AgenticAiModules... allowedModules) {

    ArchRule rule() {
      final DescribedPredicate<JavaClass> allowed =
          Arrays.stream(allowedModules)
              .map(AgenticAiModules::root)
              .reduce((left, right) -> left.or(right))
              .orElseThrow();
      final String allowedNames =
          Arrays.stream(allowedModules)
              .map(AgenticAiModules::artifactId)
              .collect(Collectors.joining(", "));
      return noClasses()
          .that(DescribedPredicate.not(allowed))
          .should()
          .dependOnClassesThat()
          .resideInAPackage(sdkPackage)
          .as(name + " is only used by " + allowedNames)
          .because(reason);
    }
  }
}
