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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class L4JAiAgentConnectorMemoryStorageTests extends BaseL4JAiAgentConnectorTest {

  @Test
  void inProcessStorage() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        elementTemplate -> elementTemplate.property("data.memory.storage.type", "in-process"),
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        (agentResponse) -> {
          AgentResponseAssert.assertThat(agentResponse)
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
          AgentResponseAssert.assertThat(agentResponse)
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
}
