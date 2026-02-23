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

import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentLimitsValidator;
import io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandler;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerValueCustomizer;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentExecutionContextFactory;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.common.AgenticAiHttpSupport;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.validation.FieldError;

class AgenticAiConnectorsAutoConfigurationTest {

  private static final List<Class<?>> AGENTIC_AI_BEANS =
      List.of(
          AgenticAiHttpSupport.class,
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
          ConversationStoreRegistry.class,
          AgentLimitsValidator.class,
          AgentMessagesHandler.class,
          AgentResponseHandler.class,
          OutboundConnectorAgentRequestHandler.class,
          AiAgentFunction.class,
          AiAgentJobWorkerValueCustomizer.class,
          JobWorkerAgentRequestHandler.class,
          JobWorkerAgentExecutionContextFactory.class,
          AiAgentJobWorkerHandler.class,
          AiAgentJobWorker.class);

  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelFactory.class,
          DocumentToContentConverter.class,
          ContentConverter.class,
          ToolCallConverter.class,
          JsonSchemaConverter.class,
          ToolSpecificationConverter.class,
          ChatMessageConverter.class,
          Langchain4JAiFrameworkAdapter.class);

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
  void whenAiAgentConnectorDisabled_thenNoAiAgentFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.outbound-connector.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              OutboundConnectorAgentRequestHandler.class, AiAgentFunction.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(OutboundConnectorAgentRequestHandler.class)
                  .doesNotHaveBean(AiAgentFunction.class);
            });
  }

  @Test
  void whenAiAgentJobWorkerConnectorDisabled_thenNoAiAgentJobWorkerIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.job-worker.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              AiAgentJobWorkerValueCustomizer.class,
                              JobWorkerAgentRequestHandler.class,
                              JobWorkerAgentExecutionContextFactory.class,
                              AiAgentJobWorkerHandler.class,
                              AiAgentJobWorker.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(AiAgentJobWorkerValueCustomizer.class)
                  .doesNotHaveBean(JobWorkerAgentRequestHandler.class)
                  .doesNotHaveBean(JobWorkerAgentExecutionContextFactory.class)
                  .doesNotHaveBean(AiAgentJobWorkerHandler.class)
                  .doesNotHaveBean(AiAgentJobWorker.class);
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

  private Predicate<Class<?>> notAnyOf(Class<?>... classes) {
    return c -> Stream.of(classes).noneMatch(c::equals);
  }
}
