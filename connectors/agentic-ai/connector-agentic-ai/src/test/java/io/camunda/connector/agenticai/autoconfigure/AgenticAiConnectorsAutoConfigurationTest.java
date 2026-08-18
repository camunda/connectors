/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertDoesNotHaveAnyBeansOf;
import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertHasAllBeansOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV1Function;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV2Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV1Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV2Function;
import io.camunda.connector.agenticai.aiagent.agent.AgentConversationTurnInputComposer;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.AzureOpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.BedrockChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.GoogleVertexAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.LangChain4JChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.OpenAiCompatibleChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureOpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration.OpenAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomAnthropicProviderConfig.CustomAnthropicChatModelFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomAzureOpenAiProviderConfig.CustomAzureOpenAiChatModelFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomBedrockProviderConfig.CustomBedrockChatModelFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomGoogleVertexAiProviderConfig.CustomGoogleVertexAiChatModelFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomOpenAiCompatibleProviderConfig.CustomOpenAiCompatibleChatModelFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomLangChain4JChatModelFactoryOverrides.CustomOpenAiProviderConfig.CustomOpenAiChatModelFactory;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.FieldError;

class AgenticAiConnectorsAutoConfigurationTest {

  private static final List<Class<?>> AGENTIC_AI_BEANS =
      List.of(
          AgenticAiHttpProxySupport.class,
          AdHocToolElementParameterExtractor.class,
          AdHocToolSchemaGenerator.class,
          AdHocToolsSchemaResolver.class,
          ProcessDefinitionAdHocToolElementsResolver.class,
          AdHocToolsSchemaFunction.class,
          GatewayToolHandlerRegistry.class,
          AgentToolsResolver.class,
          AgentInitializer.class,
          InProcessConversationStore.class,
          CamundaDocumentConversationStore.class,
          AwsAgentCoreConversationMapper.class,
          AwsAgentCoreConversationStore.class,
          ConversationStoreRegistry.class,
          AgentConversationTurnInputComposer.class,
          AgentResponseHandler.class,
          AgentTaskRequestHandler.class,
          AgentTaskV1Function.class,
          AgentTaskV2Function.class,
          AgentSubProcessRequestHandler.class,
          AgentSubProcessV1Function.class,
          AgentSubProcessV2Function.class,
          AgentInstanceClient.class,
          ChatModelRegistry.class,
          AnthropicChatModelFactory.class,
          OpenAiChatModelFactory.class);

  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelHttpProxySupport.class,
          DocumentToContentConverter.class,
          ContentConverter.class,
          ToolCallConverter.class,
          JsonSchemaConverter.class,
          ToolSpecificationConverter.class,
          ChatMessageConverter.class,
          io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .AnthropicChatModelFactory.class,
          AzureOpenAiChatModelFactory.class,
          BedrockChatModelFactory.class,
          GoogleVertexAiChatModelFactory.class,
          io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .OpenAiChatModelFactory.class,
          OpenAiCompatibleChatModelFactory.class);

  // this will need to be updated in case we support different frameworks
  private static final List<Class<?>> ALL_BEANS =
      Stream.concat(AGENTIC_AI_BEANS.stream(), LANGCHAIN4J_BEANS.stream()).toList();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  @Test
  void whenAgenticAiConfigurationEnabled_thenAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=true")
        .run(context -> assertHasAllBeansOf(context, ALL_BEANS));
  }

  @Test
  void whenAgenticAiConfigurationDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(context -> assertDoesNotHaveAnyBeansOf(context, ALL_BEANS));
  }

  @Test
  void whenAgentTaskConnectorDisabled_thenNoAgentTaskFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.outbound-connector.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              AgentTaskRequestHandler.class,
                              AgentTaskV1Function.class,
                              AgentTaskV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(AgentTaskRequestHandler.class)
                  .doesNotHaveBean(AgentTaskV1Function.class)
                  .doesNotHaveBean(AgentTaskV2Function.class);
            });
  }

  @Test
  void whenAgentSubProcessConnectorDisabled_thenNoAgentSubProcessFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.job-worker.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              AgentSubProcessRequestHandler.class,
                              AgentSubProcessV1Function.class,
                              AgentSubProcessV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(AgentSubProcessRequestHandler.class)
                  .doesNotHaveBean(AgentSubProcessV1Function.class)
                  .doesNotHaveBean(AgentSubProcessV2Function.class);
            });
  }

  @Test
  void whenAdHocToolsSchemaConnectorDisabled_thenNoAdHocToolsSchemaFunctionIsCreated() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream().filter(notAnyOf(AdHocToolsSchemaFunction.class)).toList());
              assertThat(context).doesNotHaveBean(AdHocToolsSchemaFunction.class);
            });
  }

  @Test
  void whenToolsCachingDisabled_thenConfiguresDefaultToolElementsResolver() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .getBean(ProcessDefinitionAdHocToolElementsResolver.class)
                    .isInstanceOf(CamundaClientProcessDefinitionAdHocToolElementsResolver.class));
  }

  @Test
  void whenToolsCachingEnabled_thenConfiguresCachingToolElementsResolver() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getBean(ProcessDefinitionAdHocToolElementsResolver.class)
                    .isInstanceOf(CachingProcessDefinitionAdHocToolElementsResolver.class));
  }

  @Test
  void whenToolsCachingMaximumSizeIsNegative_thenFailsValidation() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.maximum-size=-10")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(
                        BindValidationException.class,
                        e -> {
                          assertThat(e.getValidationErrors().getAllErrors())
                              .hasSize(1)
                              .first(InstanceOfAssertFactories.type(FieldError.class))
                              .extracting(
                                  FieldError::getObjectName,
                                  FieldError::getField,
                                  FieldError::getRejectedValue,
                                  FieldError::getDefaultMessage)
                              .containsExactly(
                                  "camunda.connector.agenticai",
                                  "tools.processDefinition.cache.maximumSize",
                                  -10L,
                                  "must be greater than or equal to 0");
                        }));
  }

  @Test
  void whenProxySupportEnabled_thenAgenticAiHttpProxySupportUsesEnvironmentProxyConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AgenticAiHttpProxySupport.class);
          var httpProxySupport = context.getBean(AgenticAiHttpProxySupport.class);
          assertThat(httpProxySupport.getProxyConfiguration())
              .isInstanceOf(EnvironmentProxyConfiguration.class);
        });
  }

  @Test
  void whenProxySupportDisabled_thenAgenticAiHttpProxySupportUsesNoProxyConfiguration() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.http.proxy-support.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AgenticAiHttpProxySupport.class);
              var httpProxySupport = context.getBean(AgenticAiHttpProxySupport.class);

              final var proxyConfiguration = httpProxySupport.getProxyConfiguration();
              assertThat(proxyConfiguration).isNotInstanceOf(EnvironmentProxyConfiguration.class);

              assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("http"))
                  .isEmpty();
              assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("https"))
                  .isEmpty();
            });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("chatModelResolutionCases")
  void resolvesChatModelForConfiguration(ChatModelResolutionCase resolutionCase) {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(resolutionCase.factoryClass());

          final var chatModelRegistry = context.getBean(ChatModelRegistry.class);
          try (var chatModel = chatModelRegistry.resolve(resolutionCase.configuration())) {
            assertThat(chatModel).isNotNull();
          }
        });
  }

  // A throwaway, self-signed key never used against a real Google account - only needed so
  // ServiceAccountCredentials.fromStream() can parse it without hitting the network.
  private static final String FAKE_GOOGLE_SERVICE_ACCOUNT_KEY =
      """
      {
        "type": "service_account",
        "project_id": "my-project",
        "private_key_id": "test-key-id",
        "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC4KONsg1R7zrU1\\nur0Mbdc9hxiVnzUXTEULxsWioJrILEcn4Ne6BHHgDuusHvJv04OmptPpr/kmGXc9\\n1MRUlnxR4pQ4MPIDlasCWROG3WfQ27ru4pFyhUvxUwWV4dWMWntQjAVcKUVV2kX0\\n4M7SxRTZnbS8D77RkfrLa8CuVL/lukTscXe1vmpCVBXDqMC+meAgwdW0JwZhRgtx\\n0RvjHSvi91Bi2hPmmjpRwts3bMYJXxj38kNut++GIJQ0Mhd77AN4fJMhqiDoWHKE\\nBvYTm0xRsAlBlLaEEFzLNLWsO3hobJI1oYoddjq9009/+InRFiHqHZWcWXbYVX0w\\nEVjNeWUHAgMBAAECggEACtkZwHgh/2MHSKF15mgIAE9XcuTcd0FeZdmxJanJRFZb\\nYK19d68wWA748fwmstCmVihInmDnz8c7P3CrmgH9U8OBkKfNccmct7gwjsa3CVYQ\\nNmcxQyo39YC6+P/DGQ/xaKa+4BVsSKjhaxHdDQxf9Iu2LEfPKUAsolP4FyzV2v3a\\n34GV6OaNeV1mnxz7wf+Y1L/YvxHU9iCClQY9ZPxYS4rtLLPQPkxcWog853ZwrSMP\\n0VZ+1XJlfo7zOmgn/ye8XEzlZ+e5yFK+ceff0dKkNQyK4DL4OTcZsQNavg3xWdUU\\nKLQh5Bc4l4yQy1S6BF0UsfSsftyYYb7vqcl/6Xjb1QKBgQDfDnXHgJnoEdg2YyIc\\nVAuYMxCJWCVS//PyJFpz+TqAQvpKTxzMuIr6B48mJFNoaOcygxXPWT0WD0B50FUi\\ncR48Oh30dN1NBr12QjlyPyWCcMgQ63DYNoZIc+rV4Mu1mxyX+KdlnUdMgyKirka0\\nCJ/oP2vXh11nzIJ3Z3OFr3SdhQKBgQDTW8fv1eMQ90wpWQ8OUZlEW5gRDFwmK4sD\\nrrCNX6QNegr32DD/SiLWIWFmpYcanN8m60ZX56R/ai4iUu2KsG/6Xkg5wqQy3vN+\\nt56Eet/SDjbanMX5i42qoY5CPem/1TVSBHWcR5x3i3tNxExSDAclzb/N1DDtTbfO\\nqby2klAoGwKBgQDXo+UdkCg6gTXjrocFmAL1iziLbxn2Wdf+2kJQKDv0T8wlFsKi\\n8C37dl9f4nJ4WCJbZPsqz/0MXIZavZvwhidS1mSrNmfT1ZZIw9FBr+aVam8gXF1l\\nyaCcXuRDDOYjledYzF0ZEaoiQAy19YII/uWI4/dgEE+uz7m5sduu/Gbi+QKBgQDG\\n/zsXzMGlT7EdnQRX7uvnOHXMV17LcWPJa8g+0zWamrWI9LvtINf71CHoiyDRJbHU\\n6t+oFCkE7evR1VJhqg1EJVDLUT9XxiJrxGYzRZ1GIKv02HZtpb8UUFeodrKGMy+o\\nsRoqsiHXTDQj3BYficORDE7ydD48r1fH9HgBTXC60QKBgQDL76i13a3RyJqABG4v\\n5ysATMLyIBtlOn4ncQuFKFVt8SPhSraaNtWDPU44J3d2jlXpeh3IWhhZpheD+6Sb\\nv1I4uOMxGeFV1vPhHLg1vqmB3WH/upZmLAgKj9OOqNvYPj1FMN5jpmHnaUhy/DVy\\n4jhO1CWKD3CKJhgOCN58k7ohHQ==\\n-----END PRIVATE KEY-----\\n",
        "client_email": "test@my-project.iam.gserviceaccount.com",
        "client_id": "123456789",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test%40my-project.iam.gserviceaccount.com"
      }
      """;

  static Stream<ChatModelResolutionCase> chatModelResolutionCases() {
    return Stream.of(
        new ChatModelResolutionCase(
            "anthropic (native)",
            new AnthropicChatModelConfiguration(
                new AnthropicConnection(
                    new AnthropicApiBackend(
                        new AnthropicApiBackend.AnthropicApi(
                            "sk-ant-test", null, null, null, null)),
                    new AnthropicModel("claude-sonnet-5", null),
                    null)),
            AnthropicChatModelFactory.class),
        new ChatModelResolutionCase(
            "anthropic (langchain4j)",
            new AnthropicProviderConfiguration(
                new AnthropicProviderConfiguration.AnthropicConnection(
                    null,
                    new AnthropicProviderConfiguration.AnthropicAuthentication("sk-ant-test"),
                    null,
                    new AnthropicProviderConfiguration.AnthropicModel("claude-sonnet-5", null))),
            io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
                .AnthropicChatModelFactory.class),
        new ChatModelResolutionCase(
            "azure-openai",
            new AzureOpenAiProviderConfiguration(
                new AzureOpenAiConnection(
                    "https://example.openai.azure.com",
                    new AzureApiKeyAuthentication("azure-api-key"),
                    null,
                    new AzureOpenAiModel("gpt-4o", null))),
            AzureOpenAiChatModelFactory.class),
        new ChatModelResolutionCase(
            "bedrock",
            new BedrockProviderConfiguration(
                new BedrockConnection(
                    "eu-west-1",
                    null,
                    new AwsDefaultCredentialsChainAuthentication(),
                    null,
                    new BedrockModel("anthropic.claude-3-sonnet", null))),
            BedrockChatModelFactory.class),
        new ChatModelResolutionCase(
            // Google's genai SDK resolves application default credentials eagerly when the
            // client is built, which would require real GCP credentials in this environment.
            // Service account credentials avoid that lookup entirely.
            "google-vertex-ai",
            new GoogleVertexAiProviderConfiguration(
                new GoogleVertexAiConnection(
                    "my-project",
                    "us-central1",
                    new ServiceAccountCredentialsAuthentication(FAKE_GOOGLE_SERVICE_ACCOUNT_KEY),
                    new GoogleVertexAiModel("gemini-2.5-pro", null))),
            GoogleVertexAiChatModelFactory.class),
        new ChatModelResolutionCase(
            "openai (native)",
            new OpenAiChatModelConfiguration(
                new OpenAiChatModelConfiguration.OpenAiConnection(
                    new OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi(
                        new OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi
                            .ResponsesParameters(null, null, null, null)),
                    new OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend(
                        new OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend
                            .OpenAiApiConnection(
                            "sk-openai-test", null, null, null, null, null, null)),
                    new OpenAiChatModelConfiguration.OpenAiModel("gpt-5.5"),
                    null)),
            OpenAiChatModelFactory.class),
        new ChatModelResolutionCase(
            "openai (langchain4j)",
            new OpenAiProviderConfiguration(
                new OpenAiConnection(
                    new OpenAiAuthentication("sk-openai-test", null, null),
                    null,
                    new OpenAiModel("gpt-4o", null))),
            io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
                .OpenAiChatModelFactory.class),
        new ChatModelResolutionCase(
            "openai-compatible",
            new OpenAiCompatibleProviderConfiguration(
                new OpenAiCompatibleConnection(
                    "https://my-endpoint.local",
                    new OpenAiCompatibleAuthentication("api-key"),
                    null,
                    null,
                    null,
                    new OpenAiCompatibleModel("test-model", null))),
            OpenAiCompatibleChatModelFactory.class));
  }

  record ChatModelResolutionCase(
      String label,
      ChatModelConfiguration configuration,
      Class<? extends ChatModelFactory> factoryClass) {

    @Override
    public String toString() {
      return label;
    }
  }

  @Nested
  class CustomLangChain4JChatModelFactoryOverrides {

    @ParameterizedTest
    @MethodSource("factoryOverrideCases")
    void userProvidedFactoryBeanOverridesDefault(FactoryOverrideCase override) {
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, override.configurationClass())
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class)
          .run(
              context -> {
                final var beanNamesForType = context.getBeanNamesForType(override.factoryClass());
                assertThat(beanNamesForType).hasSize(1).containsExactly(override.beanName());

                assertThat(context.getBean(beanNamesForType[0]))
                    .isInstanceOf(override.customFactoryClass());
              });
    }

    static Stream<FactoryOverrideCase> factoryOverrideCases() {
      return Stream.of(
          new FactoryOverrideCase(
              CustomAnthropicProviderConfig.class,
              "customAnthropicChatModelFactory",
              io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
                  .AnthropicChatModelFactory.class,
              CustomAnthropicChatModelFactory.class),
          new FactoryOverrideCase(
              CustomAzureOpenAiProviderConfig.class,
              "customAzureOpenAiChatModelFactory",
              AzureOpenAiChatModelFactory.class,
              CustomAzureOpenAiChatModelFactory.class),
          new FactoryOverrideCase(
              CustomBedrockProviderConfig.class,
              "customBedrockChatModelFactory",
              BedrockChatModelFactory.class,
              CustomBedrockChatModelFactory.class),
          new FactoryOverrideCase(
              CustomGoogleVertexAiProviderConfig.class,
              "customGoogleVertexAiChatModelFactory",
              GoogleVertexAiChatModelFactory.class,
              CustomGoogleVertexAiChatModelFactory.class),
          new FactoryOverrideCase(
              CustomOpenAiProviderConfig.class,
              "customOpenAiChatModelFactory",
              io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
                  .OpenAiChatModelFactory.class,
              CustomOpenAiChatModelFactory.class),
          new FactoryOverrideCase(
              CustomOpenAiCompatibleProviderConfig.class,
              "customOpenAiCompatibleChatModelFactory",
              OpenAiCompatibleChatModelFactory.class,
              CustomOpenAiCompatibleChatModelFactory.class));
    }

    record FactoryOverrideCase(
        Class<?> configurationClass,
        String beanName,
        Class<? extends LangChain4JChatModelFactory<?>> factoryClass,
        Class<? extends LangChain4JChatModelFactory<?>> customFactoryClass) {

      @Override
      public String toString() {
        return factoryClass.getSimpleName();
      }
    }

    static class CustomAnthropicProviderConfig {
      @Bean
      io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .AnthropicChatModelFactory
          customAnthropicChatModelFactory() {
        return new CustomAnthropicChatModelFactory();
      }

      static class CustomAnthropicChatModelFactory
          extends io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .AnthropicChatModelFactory {

        CustomAnthropicChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            AnthropicProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomAzureOpenAiProviderConfig {
      @Bean
      AzureOpenAiChatModelFactory customAzureOpenAiChatModelFactory() {
        return new CustomAzureOpenAiChatModelFactory();
      }

      static class CustomAzureOpenAiChatModelFactory extends AzureOpenAiChatModelFactory {

        CustomAzureOpenAiChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            AzureOpenAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomBedrockProviderConfig {
      @Bean
      BedrockChatModelFactory customBedrockChatModelFactory() {
        return new CustomBedrockChatModelFactory();
      }

      static class CustomBedrockChatModelFactory extends BedrockChatModelFactory {

        CustomBedrockChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            BedrockProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomGoogleVertexAiProviderConfig {
      @Bean
      GoogleVertexAiChatModelFactory customGoogleVertexAiChatModelFactory() {
        return new CustomGoogleVertexAiChatModelFactory();
      }

      static class CustomGoogleVertexAiChatModelFactory extends GoogleVertexAiChatModelFactory {

        CustomGoogleVertexAiChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            GoogleVertexAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomOpenAiProviderConfig {
      @Bean
      io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .OpenAiChatModelFactory
          customOpenAiChatModelFactory() {
        return new CustomOpenAiChatModelFactory();
      }

      static class CustomOpenAiChatModelFactory
          extends io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory
              .OpenAiChatModelFactory {

        CustomOpenAiChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            OpenAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomOpenAiCompatibleProviderConfig {
      @Bean
      OpenAiCompatibleChatModelFactory customOpenAiCompatibleChatModelFactory() {
        return new CustomOpenAiCompatibleChatModelFactory();
      }

      static class CustomOpenAiCompatibleChatModelFactory extends OpenAiCompatibleChatModelFactory {

        CustomOpenAiCompatibleChatModelFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class));
        }

        @Override
        public CloseableChatModel createChatModel(
            OpenAiCompatibleProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }
  }

  @Nested
  class CustomNativeChatModelFactoryOverrides {

    @ParameterizedTest
    @MethodSource("nativeFactoryOverrideCases")
    void userProvidedFactoryBeanOverridesDefault(NativeFactoryOverrideCase override) {
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, override.configurationClass())
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class)
          .run(
              context -> {
                final var beanNamesForType = context.getBeanNamesForType(override.factoryClass());
                assertThat(beanNamesForType).hasSize(1).containsExactly(override.beanName());

                assertThat(context.getBean(beanNamesForType[0]))
                    .isInstanceOf(override.customFactoryClass());
              });
    }

    static Stream<NativeFactoryOverrideCase> nativeFactoryOverrideCases() {
      return Stream.of(
          new NativeFactoryOverrideCase(
              CustomAnthropicProviderConfig.class,
              "customNativeAnthropicChatModelFactory",
              AnthropicChatModelFactory.class,
              CustomAnthropicProviderConfig.CustomAnthropicChatModelFactory.class),
          new NativeFactoryOverrideCase(
              CustomOpenAiProviderConfig.class,
              "customNativeOpenAiChatModelFactory",
              OpenAiChatModelFactory.class,
              CustomOpenAiProviderConfig.CustomOpenAiChatModelFactory.class));
    }

    record NativeFactoryOverrideCase(
        Class<?> configurationClass,
        String beanName,
        Class<? extends ChatModelFactory> factoryClass,
        Class<? extends ChatModelFactory> customFactoryClass) {

      @Override
      public String toString() {
        return factoryClass.getSimpleName();
      }
    }

    static class CustomAnthropicProviderConfig {
      @Bean
      AnthropicChatModelFactory customNativeAnthropicChatModelFactory() {
        return new CustomAnthropicChatModelFactory();
      }

      static class CustomAnthropicChatModelFactory extends AnthropicChatModelFactory {

        CustomAnthropicChatModelFactory() {
          super(
              mock(AgenticAiHttpProxySupport.class),
              mock(AnthropicMessageRequestConverter.class),
              mock(AnthropicMessageResponseConverter.class));
        }

        @Override
        public ChatModel create(ChatModelConfiguration configuration) {
          return mock(ChatModel.class);
        }
      }
    }

    static class CustomOpenAiProviderConfig {
      @Bean
      OpenAiChatModelFactory customNativeOpenAiChatModelFactory() {
        return new CustomOpenAiChatModelFactory();
      }

      static class CustomOpenAiChatModelFactory extends OpenAiChatModelFactory {

        CustomOpenAiChatModelFactory() {
          super(
              mock(AgenticAiHttpProxySupport.class),
              mock(OpenAiApiFamilyStrategy.class),
              mock(OpenAiApiFamilyStrategy.class));
        }

        @Override
        public ChatModel create(ChatModelConfiguration configuration) {
          return mock(ChatModel.class);
        }
      }
    }
  }

  private Predicate<Class<?>> notAnyOf(Class<?>... classes) {
    return c -> Stream.of(classes).noneMatch(c::equals);
  }
}
