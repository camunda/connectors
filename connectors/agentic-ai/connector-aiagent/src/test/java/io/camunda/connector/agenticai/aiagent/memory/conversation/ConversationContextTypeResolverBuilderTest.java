/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.camunda.connector.agenticai.aiagent.chatmodel.LogEventsTestSupport;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.versioning.AgentContextSchemaMigration;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConversationContextTypeResolverBuilderTest {

  @JsonTypeName("my-custom")
  record CustomConversationContext(String conversationId, String customField)
      implements ConversationContext {}

  record WithoutTypeName(String conversationId) implements ConversationContext {}

  @JsonTypeName("provider-a")
  record ProviderAContext(String conversationId) implements ConversationContext {}

  @JsonTypeName("provider-b")
  record ProviderBContext(String conversationId) implements ConversationContext {}

  @JsonTypeName("provider-a")
  record DuplicateTypeIdContext(String conversationId) implements ConversationContext {}

  public static class FailingConstructorProvider implements ConversationContextSubtypes {
    public FailingConstructorProvider() {
      throw new IllegalStateException("constructor failure");
    }

    @Override
    public List<Class<? extends ConversationContext>> subtypes() {
      return List.of(ProviderBContext.class);
    }
  }

  public static class ClassLoaderProvider implements ConversationContextSubtypes {
    @Override
    public List<Class<? extends ConversationContext>> subtypes() {
      return List.of(ProviderAContext.class);
    }
  }

  private static final String BUILT_IN_TYPE_IDS = "[aws-agentcore, camunda-document, in-process]";

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
  void failsOnUnknownTypeIdListingKnownTypeIds(String name, Supplier<ObjectMapper> mapperSupplier) {
    var objectMapper = mapperSupplier.get();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    "{\"type\":\"my-store\",\"conversationId\":\"conv-1\"}",
                    ConversationContext.class))
        .isInstanceOf(InvalidTypeIdException.class)
        .hasMessageContaining("Could not resolve type id 'my-store' as a subtype of")
        .hasMessageContaining(ConversationContext.class.getName())
        .hasMessageContaining("known type ids = " + BUILT_IN_TYPE_IDS)
        .hasMessageNotContaining("ConversationContextSubtypes");
  }

  @Test
  void failsOnUnknownTypeIdNestedInAgentContext() {
    var objectMapper = new ObjectMapper();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    "{\"conversation\":{\"type\":\"my-store\",\"conversationId\":\"conv-1\"}}",
                    AgentContext.class))
        .isInstanceOf(InvalidTypeIdException.class)
        .hasMessageContaining("Could not resolve type id 'my-store'")
        .hasMessageContaining("known type ids = " + BUILT_IN_TYPE_IDS)
        .hasMessageNotContaining("ConversationContextSubtypes");
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
  void skipsSubtypeWithoutJsonTypeNameAndKeepsOtherSubtypes() {
    ConversationContextSubtypes broken =
        () -> List.of(WithoutTypeName.class, ProviderAContext.class);
    ConversationContextSubtypes other = () -> List.of(ProviderBContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(broken, other).iterator());

    assertThat(typeIds(result)).containsExactly("provider-a", "provider-b");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getLevel()).isEqualTo(Level.ERROR);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains(WithoutTypeName.class.getName())
        .contains(broken.getClass().getName())
        .contains("@JsonTypeName")
        .contains("Skipping");
  }

  @Test
  void skipsNullSubtype() {
    ConversationContextSubtypes provider = () -> Arrays.asList(null, ProviderAContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(provider).iterator());

    assertThat(typeIds(result)).containsExactly("provider-a");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains("null")
        .contains(provider.getClass().getName());
  }

  @Test
  void skipsSubtypeWithTypeIdAlreadyUsedByAnotherClass() {
    ConversationContextSubtypes first = () -> List.of(ProviderAContext.class);
    ConversationContextSubtypes second =
        () -> List.of(DuplicateTypeIdContext.class, ProviderBContext.class);
    ConversationContextSubtypes repeated = () -> List.of(ProviderAContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(first, second, repeated).iterator());

    assertThat(result)
        .extracting(NamedType::getType, NamedType::getName)
        .containsExactly(
            tuple(ProviderAContext.class, "provider-a"),
            tuple(ProviderBContext.class, "provider-b"));
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains(DuplicateTypeIdContext.class.getName())
        .contains("'provider-a'")
        .contains(ProviderAContext.class.getName());
  }

  @Test
  void skipsProviderWhoseSubtypesFail() {
    ConversationContextSubtypes failing =
        () -> {
          throw new IllegalStateException("boom");
        };
    ConversationContextSubtypes other = () -> List.of(ProviderBContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(failing, other).iterator());

    assertThat(typeIds(result)).containsExactly("provider-b");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains(failing.getClass().getName())
        .contains("boom");
  }

  @Test
  void skipsProviderReturningNullSubtypes() {
    ConversationContextSubtypes nullList = nullSubtypesProvider();
    ConversationContextSubtypes other = () -> List.of(ProviderBContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(nullList, other).iterator());

    assertThat(typeIds(result)).containsExactly("provider-b");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getLevel()).isEqualTo(Level.ERROR);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains(nullList.getClass().getName())
        .contains("null");
  }

  @Test
  void skipsProviderWhoseSubtypeListFailsWhileIterating() {
    ConversationContextSubtypes failing =
        () ->
            new AbstractList<>() {
              @Override
              public Class<? extends ConversationContext> get(int index) {
                throw new IllegalStateException("broken list");
              }

              @Override
              public int size() {
                return 1;
              }
            };
    ConversationContextSubtypes other = () -> List.of(ProviderBContext.class);

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, List.of(failing, other).iterator());

    assertThat(typeIds(result)).containsExactly("provider-b");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains(failing.getClass().getName())
        .contains("broken list");
  }

  @Test
  void skipsProvidersFailingToLoadAndKeepsOthers() {
    ConversationContextSubtypes before = () -> List.of(ProviderAContext.class);
    ConversationContextSubtypes after = () -> List.of(ProviderBContext.class);
    var iterator =
        new FailingIterator(
            List.of(
                before,
                new ServiceConfigurationError("Provider com.example.Missing not found"),
                after));

    var logs = new ArrayList<ILoggingEvent>();
    var result = discover(logs, iterator);

    assertThat(typeIds(result)).containsExactly("provider-a", "provider-b");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getLevel()).isEqualTo(Level.ERROR);
    assertThat(logs.getFirst().getFormattedMessage())
        .contains("com.example.Missing")
        .contains("META-INF/services/");
  }

  @Test
  void stopsWhenProviderIteratorNeverRecovers() {
    Iterator<ConversationContextSubtypes> iterator =
        new Iterator<>() {
          @Override
          public boolean hasNext() {
            throw new ServiceConfigurationError("broken services file");
          }

          @Override
          public ConversationContextSubtypes next() {
            throw new IllegalStateException();
          }
        };

    var result = discover(new ArrayList<>(), iterator);

    assertThat(result).isEmpty();
  }

  @Test
  void skipsBrokenProviderEntriesInServicesFileAndKeepsOthers(@TempDir Path tempDir)
      throws Exception {
    var servicesDir = Files.createDirectories(tempDir.resolve("META-INF/services"));
    Files.writeString(
        servicesDir.resolve(ConversationContextSubtypes.class.getName()),
        "com.example.DoesNotExist\n"
            + FailingConstructorProvider.class.getName()
            + "\n"
            + ClassLoaderProvider.class.getName()
            + "\n");

    try (var classLoader =
        new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
      for (int i = 0; i < 3; i++) {
        var logs = new ArrayList<ILoggingEvent>();
        var result =
            logsOf(
                logs, () -> ConversationContextTypeResolverBuilder.discoverSubtypes(classLoader));

        assertThat(typeIds(result))
            .containsExactlyInAnyOrder(
                "in-process", "camunda-document", "aws-agentcore", "provider-a");
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log -> assertThat(log.getLevel()).isEqualTo(Level.ERROR));
        assertThat(logs)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anySatisfy(message -> assertThat(message).contains("com.example.DoesNotExist"))
            .anySatisfy(
                message ->
                    assertThat(message).contains(FailingConstructorProvider.class.getName()));
      }
    }
  }

  @Test
  void staysConsistentAfterBrokenProvider() throws Exception {
    ConversationContextSubtypes broken = () -> List.of(WithoutTypeName.class);
    var objectMapper = new ObjectMapper();
    var inProcess = InProcessConversationContext.builder("conv-1").messages(List.of()).build();

    for (int i = 0; i < 3; i++) {
      var result = discover(new ArrayList<>(), List.of(broken).iterator());
      assertThat(result).isEmpty();

      assertRoundTrip(objectMapper, inProcess, "in-process");
      assertThatCode(
              () ->
                  objectMapper.readValue(
                      "{\"type\":\"my-store\",\"conversationId\":\"conv-1\"}",
                      ConversationContext.class))
          .isInstanceOf(InvalidTypeIdException.class);
    }
  }

  @SuppressWarnings("NullAway")
  private static ConversationContextSubtypes nullSubtypesProvider() {
    return () -> null;
  }

  private static List<NamedType> discover(
      List<ILoggingEvent> logs, Iterator<ConversationContextSubtypes> providers) {
    return logsOf(logs, () -> ConversationContextTypeResolverBuilder.discoverSubtypes(providers));
  }

  private static List<NamedType> logsOf(
      List<ILoggingEvent> logs, Supplier<List<NamedType>> discovery) {
    var result = new ArrayList<List<NamedType>>();
    logs.addAll(
        LogEventsTestSupport.logsOf(
            ConversationContextTypeResolverBuilder.class, () -> result.add(discovery.get())));
    return result.getFirst();
  }

  private static List<String> typeIds(List<NamedType> namedTypes) {
    return namedTypes.stream().map(NamedType::getName).toList();
  }

  /** Iterator returning the given providers, throwing for every element that is an error. */
  private static final class FailingIterator implements Iterator<ConversationContextSubtypes> {
    private final Iterator<Object> elements;

    FailingIterator(List<Object> elements) {
      this.elements = elements.iterator();
    }

    @Override
    public boolean hasNext() {
      return elements.hasNext();
    }

    @Override
    public ConversationContextSubtypes next() {
      var element = elements.next();
      if (element instanceof ServiceConfigurationError error) {
        throw error;
      }
      return (ConversationContextSubtypes) element;
    }
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
