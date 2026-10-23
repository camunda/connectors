/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.conversation.ConversationContext;
import org.junit.jupiter.api.Test;

class AwsAgentCoreConversationContextSerializationTest {

  @Test
  void isDiscoveredByPlainObjectMapperAndRoundTrips() throws Exception {
    var objectMapper = new ObjectMapper();
    var context =
        AwsAgentCoreConversationContext.builder("conv-2")
            .memoryId("memory")
            .actorId("actor")
            .branchName("branch")
            .lastEventId("event")
            .build();

    var json = objectMapper.writeValueAsString(context);

    assertThat(objectMapper.readTree(json).get("type").asText()).isEqualTo("aws-agentcore");
    assertThat(objectMapper.readValue(json, ConversationContext.class)).isEqualTo(context);
  }
}
