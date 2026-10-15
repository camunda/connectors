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

  @ArchTest
  static final ArchRule OPENAI_SDK_IS_ONLY_USED_BY_THE_OPENAI_COMPATIBLE_PROVIDERS =
      onlyUsedBy(
          "com.openai..",
          "the OpenAI-compatible providers can be extracted into their own modules",
          OPENAI,
          MISTRAL);

  @ArchTest
  static final ArchRule GEMINI_SDK_IS_ONLY_USED_BY_THE_GEMINI_PROVIDERS =
      onlyUsedBy(
          "com.google.genai..",
          "the Gemini providers can be extracted into their own modules",
          GEMINI,
          LANGCHAIN4J);

  @ArchTest
  static final ArchRule BEDROCK_RUNTIME_SDK_IS_ONLY_USED_BY_THE_BEDROCK_PROVIDERS =
      onlyUsedBy(
          "software.amazon.awssdk.services.bedrockruntime..",
          "the Bedrock providers can be extracted into their own modules",
          BEDROCK,
          LANGCHAIN4J);

  @ArchTest
  static final ArchRule BEDROCK_AGENTCORE_SDK_IS_ONLY_USED_BY_THE_AGENTCORE_CONVERSATION_STORE =
      onlyUsedBy(
          "software.amazon.awssdk.services.bedrockagentcore..",
          "the AWS AgentCore conversation store can be extracted into its own module",
          AWS_AGENTCORE_MEMORY);

  @ArchTest
  static final ArchRule AWS_HTTP_CLIENT_SDK_IS_ONLY_USED_BY_PROXY_SUPPORT_AND_BEDROCK =
      onlyUsedBy(
          "software.amazon.awssdk.http..",
          "the AWS HTTP client setup is part of the proxy support and the Bedrock providers",
          PROXY,
          BEDROCK,
          LANGCHAIN4J);

  @ArchTest
  static final ArchRule AZURE_HTTP_SDK_IS_ONLY_USED_BY_PROXY_SUPPORT_AND_AZURE_CONSUMERS =
      onlyUsedBy(
          "com.azure.core.http..",
          "the Azure HTTP client setup is part of the proxy support and the Azure credentials",
          PROXY,
          AZURE,
          LANGCHAIN4J);

  @ArchTest
  static final ArchRule LANGCHAIN4J_IS_ONLY_USED_BY_THE_LANGCHAIN4J_PROVIDER =
      onlyUsedBy(
          "dev.langchain4j..",
          "the LangChain4j provider can be extracted into its own module",
          LANGCHAIN4J);

  @ArchTest
  static final ArchRule A2A_SDK_IS_ONLY_USED_BY_THE_A2A_CLIENT =
      onlyUsedBy("io.a2a..", "the A2A client can be extracted into its own module", A2A_CLIENT);

  @ArchTest
  static final ArchRule MCP_SDK_IS_ONLY_USED_BY_THE_MCP_CLIENT_FRAMEWORK =
      onlyUsedBy(
          "io.modelcontextprotocol..",
          "the MCP client can be extracted into its own module",
          MCP_CLIENT_FRAMEWORK);

  private static ArchRule onlyUsedBy(
      final String sdkPackage, final String because, final String... allowedPackages) {
    return noClasses()
        .that()
        .resideOutsideOfPackages(allowedPackages)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(sdkPackage)
        .because(because);
  }
}
