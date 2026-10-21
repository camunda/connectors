/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/**
 * Enforces that each vendor SDK is only used by the packages that integrate it: the chat model
 * provider, the conversation store, the HTTP proxy support, or the client of the protocol.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class VendorSdkArchTest {

  private static final String ANTHROPIC =
      AgenticAiModules.pkg("aiagent.chatmodel.provider.anthropic..");
  private static final String OPENAI = AgenticAiModules.pkg("aiagent.chatmodel.provider.openai..");
  // Mistral uses the OpenAI SDK through its OpenAI-compatible wire mapping.
  private static final String MISTRAL =
      AgenticAiModules.pkg("aiagent.chatmodel.provider.mistral..");
  private static final String GEMINI = AgenticAiModules.pkg("aiagent.chatmodel.provider.gemini..");
  private static final String BEDROCK =
      AgenticAiModules.pkg("aiagent.chatmodel.provider.bedrock..");
  private static final String AZURE = AgenticAiModules.pkg("aiagent.chatmodel.provider.azure..");
  private static final String LANGCHAIN4J =
      AgenticAiModules.pkg("aiagent.chatmodel.provider.langchain4j..");
  private static final String AWS_AGENTCORE_MEMORY =
      AgenticAiModules.pkg("aiagent.memory.conversation.awsagentcore..");
  private static final String PROXY = AgenticAiModules.pkg("aiagent.proxy..");
  private static final String A2A_CLIENT = AgenticAiModules.pkg("a2a.client..");
  private static final String MCP_CLIENT_FRAMEWORK = AgenticAiModules.pkg("mcp.client.framework..");

  private static final List<VendorSdk> VENDOR_SDKS =
      List.of(
          new VendorSdk(
              "Anthropic SDK",
              "com.anthropic..",
              "the Anthropic provider can be extracted into its own module",
              ANTHROPIC),
          new VendorSdk(
              "OpenAI SDK",
              "com.openai..",
              "the OpenAI-compatible providers can be extracted into their own modules",
              OPENAI,
              MISTRAL),
          new VendorSdk(
              "Gemini SDK",
              "com.google.genai..",
              "the Gemini providers can be extracted into their own modules",
              GEMINI),
          new VendorSdk(
              "AWS Bedrock runtime SDK",
              "software.amazon.awssdk.services.bedrockruntime..",
              "the Bedrock providers can be extracted into their own modules",
              BEDROCK),
          new VendorSdk(
              "AWS AgentCore SDK",
              "software.amazon.awssdk.services.bedrockagentcore..",
              "the AWS AgentCore conversation store can be extracted into its own module",
              AWS_AGENTCORE_MEMORY),
          new VendorSdk(
              "AWS HTTP client SDK",
              "software.amazon.awssdk.http..",
              "the AWS HTTP client setup is part of the proxy support and the Bedrock providers",
              PROXY,
              BEDROCK),
          new VendorSdk(
              "Azure HTTP SDK",
              "com.azure.core.http..",
              "the Azure HTTP client setup is part of the proxy support and the Azure credentials",
              PROXY,
              AZURE),
          new VendorSdk(
              "LangChain4j",
              "dev.langchain4j..",
              "the LangChain4j provider can be extracted into its own module",
              LANGCHAIN4J),
          new VendorSdk(
              "A2A SDK",
              "io.a2a..",
              "the A2A client can be extracted into its own module",
              A2A_CLIENT),
          new VendorSdk(
              "MCP SDK",
              "io.modelcontextprotocol..",
              "the MCP client can be extracted into its own module",
              MCP_CLIENT_FRAMEWORK));

  /** One rule per vendor SDK, each failing with a message that names the SDK. */
  @ArchTest
  static final ArchRule VENDOR_SDKS_ARE_ONLY_USED_BY_THEIR_INTEGRATION_PACKAGES = vendorSdkRules();

  private static ArchRule vendorSdkRules() {
    CompositeArchRule rules = null;
    for (final VendorSdk vendorSdk : VENDOR_SDKS) {
      rules = rules == null ? CompositeArchRule.of(vendorSdk.rule()) : rules.and(vendorSdk.rule());
    }
    return rules;
  }

  /**
   * A vendor SDK, the packages that may use it, and why only those packages may.
   *
   * @param name the name of the SDK in the failure message
   * @param sdkPackage the package of the SDK
   * @param reason why the SDK is confined to the allowed packages
   * @param allowedPackages the packages that may depend on the SDK
   */
  private record VendorSdk(
      String name, String sdkPackage, String reason, String... allowedPackages) {

    ArchRule rule() {
      return noClasses()
          .that()
          .resideOutsideOfPackages(allowedPackages)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(sdkPackage)
          .as(name + " is only used by " + String.join(", ", allowedPackages))
          .because(reason);
    }
  }
}
