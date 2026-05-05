/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.e2e.agenticai.AgentCoreMemoryTestConfiguration;
import io.camunda.connector.e2e.agenticai.InMemoryBedrockAgentCoreClientFactory;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
@Import(AgentCoreMemoryTestConfiguration.class)
public class L4JAiAgentJobWorkerMemoryStorageTests extends BaseL4JAiAgentJobWorkerTest {

  @MockitoSpyBean private CamundaDocumentConversationStore documentConversationStore;
  @MockitoSpyBean private CamundaDocumentStore documentStore;

  @BeforeEach
  void clearAgentCoreMemoryStore() {
    InMemoryBedrockAgentCoreClientFactory.INSTANCE.clear();
  }

  @Test
  void inProcessStorage() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        elementTemplate -> elementTemplate.property("data.memory.storage.type", "in-process"),
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        (agentResponse) -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse)
              .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasNoResponseJson();

          assertThat(agentResponse.context().conversation())
              .isInstanceOfSatisfying(
                  InProcessConversationContext.class,
                  conversation -> {
                    assertThat(conversation.conversationId()).isNotNull();
                    assertThat(conversation.messages()).hasSize(7);
                  });
        });
  }

  @Test
  void camundaDocumentStorage() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        elementTemplate ->
            elementTemplate
                .property("data.memory.storage.type", "camunda-document")
                .property("data.memory.storage.timeToLive", "PT1H")
                .property(
                    "data.memory.storage.customProperties", "={ customProperty: \"customValue\" }"),
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        (agentResponse) -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse)
              .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasNoResponseJson();

          assertThat(agentResponse.context().conversation())
              .isInstanceOfSatisfying(
                  CamundaDocumentConversationContext.class,
                  conversation -> {
                    assertThat(conversation.conversationId()).isNotNull();
                    assertThat(conversation.previousDocuments()).hasSize(2);

                    assertThat(conversation.document().reference())
                        .isInstanceOfSatisfying(
                            CamundaDocumentReference.class,
                            doc -> {
                              assertThat(doc.getMetadata().getContentType())
                                  .isEqualTo("application/json");
                              assertThat(doc.getMetadata().getFileName())
                                  .isEqualTo("AI_Agent_conversation.json");
                              assertThat(doc.getMetadata().getExpiresAt())
                                  .isCloseTo(
                                      OffsetDateTime.now().plusHours(1),
                                      within(Duration.ofSeconds(20)));
                              assertThat(doc.getMetadata().getCustomProperties())
                                  .containsExactlyInAnyOrderEntriesOf(
                                      Map.ofEntries(
                                          Map.entry(
                                              "conversationId", conversation.conversationId()),
                                          Map.entry("customProperty", "customValue")));
                            });
                  });
        });
  }

  @Test
  void camundaDocumentStorage_cleansUpOrphanedDocumentOnJobCompletionFailure() throws Exception {
    // capture the job key from the createSession call on the spy
    var jobKey = new AtomicLong();
    doAnswer(
            invocation -> {
              AgentExecutionContext executionContext = invocation.getArgument(0);
              jobKey.set(executionContext.jobContext().getJobKey());
              return invocation.callRealMethod();
            })
        .when(documentConversationStore)
        .createSession(any(), any());

    // capture the reference of the document created by storeMessages during this job —
    // this is the document that becomes orphaned when job completion fails
    var createdReference = new AtomicReference<CamundaDocumentReference>();
    doAnswer(
            invocation -> {
              CamundaDocumentReference ref = (CamundaDocumentReference) invocation.callRealMethod();
              createdReference.set(ref);
              return ref;
            })
        .when(documentStore)
        .createDocument(any());

    final var initialUserPrompt = "Write a haiku about the sea";
    final var responseText = "Waves crash on the shore";

    doAnswer(
            invocation -> {
              // fail the job via Zeebe API — causes the subsequent completeJob to be rejected
              camundaClient
                  .newFailCommand(jobKey.get())
                  .retries(0)
                  .errorMessage("Deliberately failed for e2e test")
                  .send()
                  .join();

              return ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(new AiMessage(responseText))
                  .build();
            })
        .when(chatModel)
        .chat(chatRequestCaptor.capture());

    createProcessInstance(
        elementTemplate ->
            elementTemplate
                .property("data.memory.storage.type", "camunda-document")
                .property("data.memory.storage.timeToLive", "PT1H"),
        Map.of("userPrompt", initialUserPrompt));

    // verify that onJobCompletionFailed deleted the exact document that was written by
    // storeMessages — the orphaned document that no Zeebe pointer will ever reference
    await()
        .alias("onJobCompletionFailed should clean up the orphaned document")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(createdReference.get()).isNotNull();
              verify(documentStore).deleteDocument(createdReference.get());
            });
  }

  @Test
  void awsAgentCoreMemoryStorage() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        elementTemplate ->
            elementTemplate
                .property("data.memory.storage.type", "aws-agentcore")
                .property("data.memory.storage.region", "us-east-1")
                .property("data.memory.storage.authentication.type", "credentials")
                .property("data.memory.storage.authentication.accessKey", "test-access-key")
                .property("data.memory.storage.authentication.secretKey", "test-secret-key")
                .property("data.memory.storage.memoryId", "test-memory-id")
                .property("data.memory.storage.actorId", "test-actor-id"),
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        (agentResponse) -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse)
              .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
              .hasNoResponseJson();

          assertThat(agentResponse.context().conversation())
              .isInstanceOfSatisfying(
                  AwsAgentCoreConversationContext.class,
                  conversation -> {
                    assertThat(conversation.conversationId()).isNotNull();
                    assertThat(conversation.memoryId()).isEqualTo("test-memory-id");
                    assertThat(conversation.actorId()).isEqualTo("test-actor-id");
                    assertThat(conversation.lastEventId()).isNotNull();
                    // After 3 agent iterations, branch should be set (turns 2+ create branches)
                    assertThat(conversation.branchName()).isNotNull();
                    // System message should be preserved in context
                    assertThat(conversation.systemMessage()).isNotNull();
                  });
        });
  }
}
