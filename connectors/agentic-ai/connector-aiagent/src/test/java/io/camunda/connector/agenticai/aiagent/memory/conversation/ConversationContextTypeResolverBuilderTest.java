/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.versioning.AgentContextSchemaMigration;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConversationContextTypeResolverBuilderTest {

  @JsonTypeName("my-custom")
  record CustomConversationContext(String conversationId, String customField)
      implements ConversationContext {}

  record WithoutTypeName(String conversationId) implements ConversationContext {}

  static Stream<Arguments> mappers() {
    return Stream.of(
        Arguments.of("plain ObjectMapper", (Supplier<ObjectMapper>) ObjectMapper::new),
        Arguments.of(
            "connectors ObjectMapper",
            (Supplier<ObjectMapper>) TestObjectMapperSupplier::getInstance));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void serializesBuiltInContextsWithExpectedTypeAndRoundTrips(
      String name, Supplier<ObjectMapper> mapperSupplier) throws Exception {
    var objectMapper = mapperSupplier.get();

    var inProcess = InProcessConversationContext.builder("conv-1").messages(List.of()).build();
    var awsAgentCore =
        AwsAgentCoreConversationContext.builder("conv-2")
            .memoryId("memory")
            .actorId("actor")
            .branchName("branch")
            .lastEventId("event")
            .build();

    assertRoundTrip(objectMapper, inProcess, "in-process");
    assertRoundTrip(objectMapper, awsAgentCore, "aws-agentcore");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void resolvesSubtypesRegisteredOnMapperAlongsideBuiltIns(
      String name, Supplier<ObjectMapper> mapperSupplier) throws Exception {
    var objectMapper = mapperSupplier.get();
    objectMapper.registerSubtypes(CustomConversationContext.class);

    var custom = new CustomConversationContext("conv-3", "value");
    assertRoundTrip(objectMapper, custom, "my-custom");

    var inProcess = InProcessConversationContext.builder("conv-1").messages(List.of()).build();
    assertRoundTrip(objectMapper, inProcess, "in-process");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappers")
  void failsOnUnknownTypeId(String name, Supplier<ObjectMapper> mapperSupplier) {
    var objectMapper = mapperSupplier.get();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    "{\"type\":\"unknown\",\"conversationId\":\"conv-1\"}",
                    ConversationContext.class))
        .isInstanceOf(InvalidTypeIdException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void deserializesInProcessFixtureWithPlainObjectMapper() throws IOException {
    JsonNode tree;
    try (InputStream is =
        getClass()
            .getResourceAsStream(
                "/backwardcompatibility/camunda-8.9/agentContext-inprocess.json")) {
      tree = new ObjectMapper().readTree(is);
    }

    AgentContext agentContext =
        AgentContextSchemaMigration.migrateAndBindAgentContext(tree, new ObjectMapper());

    assertThat(agentContext.conversation()).isInstanceOf(InProcessConversationContext.class);
  }

  @Test
  void failsFastWhenListedSubtypeLacksJsonTypeName() {
    ConversationContextSubtypes provider = () -> List.of(WithoutTypeName.class);

    assertThatThrownBy(() -> ConversationContextTypeResolverBuilder.toNamedTypes(Set.of(provider)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(WithoutTypeName.class.getName())
        .hasMessageContaining(provider.getClass().getName())
        .hasMessageContaining("@JsonTypeName");
  }

  private static void assertRoundTrip(
      ObjectMapper objectMapper, ConversationContext context, String expectedType)
      throws Exception {
    var json = objectMapper.writeValueAsString(context);
    assertThat(objectMapper.readTree(json).get("type").asText()).isEqualTo(expectedType);

    var deserialized = objectMapper.readValue(json, ConversationContext.class);
    assertThat(deserialized).isEqualTo(context);
  }
}
