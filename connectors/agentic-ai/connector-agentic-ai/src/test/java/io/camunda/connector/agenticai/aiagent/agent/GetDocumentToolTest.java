/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.localtool.GetDocumentTool;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolCall;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolContext;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The {@code c8__get_document} tool run by the framework, the way the agent loop runs it. */
class GetDocumentToolTest {

  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
          .withFeatures(AgentContext.newInstanceFeatures());

  private static final long TEXT_LIMIT = 256L * 1024;
  private static final long BINARY_LIMIT = 10L * 1024 * 1024;

  /**
   * A document store whose documents are endless streams of zeros: it counts the bytes read from
   * them, and fails on a read of a document marked as not readable.
   */
  private static final class CountingStore implements CamundaDocumentStore {
    final AtomicLong bytesRead = new AtomicLong();
    final AtomicLong opened = new AtomicLong();
    volatile boolean readable = true;

    @Override
    public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getDocumentContent(CamundaDocumentReference reference) {
      opened.incrementAndGet();
      if (!readable) {
        throw new AssertionError("the document must not be read");
      }
      return new InputStream() {
        @Override
        public int read() {
          bytesRead.incrementAndGet();
          return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
          bytesRead.addAndGet(length);
          java.util.Arrays.fill(buffer, offset, offset + length, (byte) 0);
          return length;
        }
      };
    }

    @Override
    public void deleteDocument(CamundaDocumentReference reference) {}

    @Override
    public String generateLink(
        CamundaDocumentReference reference, DocumentLinkParameters parameters) {
      return "";
    }
  }

  private final CountingStore store = new CountingStore();
  private final ObjectMapper objectMapper = objectMapperFor(store);
  private final LocalToolCallHandler handler =
      new LocalToolCallHandlerImpl(
          new LocalToolRegistryImpl(List.of(new ReturnDocumentTool(), new GetDocumentTool())),
          objectMapper,
          mock(DocumentFactory.class));

  private static ObjectMapper objectMapperFor(CamundaDocumentStore store) {
    final var copy = ConnectorsObjectMapperSupplier.getCopy();
    return copy.registerModules(
        new JacksonModuleDocumentDeserializer(
            new DocumentFactoryImpl(store),
            new DefaultIntrinsicFunctionExecutor(copy),
            DocumentModuleSettings.create()),
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }

  private static DocumentRegistryEntry camundaEntry(
      String id, String contentType, @Nullable Long size) {
    final var metadata = new LinkedHashMap<String, Object>();
    metadata.put("contentType", contentType);
    metadata.put("fileName", id);
    if (size != null) {
      metadata.put("size", size);
    }
    final var reference = new LinkedHashMap<String, Object>();
    reference.put("camunda.document.type", "camunda");
    reference.put("storeId", "in-memory");
    reference.put("documentId", id);
    reference.put("metadata", metadata);
    return new DocumentRegistryEntry(id, DocumentSource.userMessage(null), reference);
  }

  @Test
  void definitionHasASingleRequiredDocumentParameterAndTellsAboutLargeDocuments() {
    final var definition = new GetDocumentTool().definition();

    assertThat(GetDocumentTool.NAME).isEqualTo("c8__get_document");
    assertThat(definition.description())
        .contains("Loads a known document")
        .contains("too large")
        .contains("pass the id of a large document to a tool that processes it")
        .contains("sandbox");
    assertThat(definition.inputSchema().get("required")).isEqualTo(List.of("id"));
    assertThat(definition.inputSchema().get("properties"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("id");
    assertThat(definition.inputSchema().toString())
        .contains("format=camunda-document-id")
        .contains("Pass the id of a known document");
  }

  @Test
  void isOfferedToEveryAgentWithTheDocumentsFeatureOnly() {
    assertThat(handler.availableToolDefinitions(CONFIGURATION))
        .extracting(definition -> definition.name())
        .containsExactly("c8__return_document", "c8__get_document");
    assertThat(handler.availableToolDefinitions(CONFIGURATION.withFeatures(java.util.Set.of())))
        .isEmpty();
  }

  @Test
  void loadsAKnownDocumentAndMarksTheResultWithItsId() {
    final var split =
        run(List.of(camundaEntry("doc-1", "text/plain", 10L)), List.of(), call("1", "doc-1"));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.content())
                  .isEqualTo(
                      "Loaded the document <doc id=\"doc-1\" contentType=\"text/plain\" size=\"10\" source=\"user_message\" />. Its content follows in the next message.");
              assertThat(result.properties())
                  .containsEntry(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID, "doc-1");
            });
    assertThat(split.remainingCalls()).isEmpty();
  }

  @Test
  void answersAnUnknownIdWithAnErrorListingTheKnownDocuments() {
    final var split =
        run(List.of(camundaEntry("doc-1", "text/plain", 10L)), List.of(), call("1", "bogus"));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.content().toString())
                  .startsWith(
                      "Parameter 'id' must be the exact id of a known document (the id attribute of a <doc/> tag in the conversation), got 'bogus'. Known documents:")
                  .contains("doc-1");
              assertThat(result.properties()).isEmpty();
            });
  }

  @Test
  void doesNotLoadADocumentThatIsAlreadyLoadedInTheConversation() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var loadedTag = DocumentReferenceXmlTag.from(entry).toLoadedContent();

    final var split =
        run(
            List.of(entry),
            List.of(UserMessage.builder().content(List.of(loadedTag)).build()),
            call("1", "doc-1"));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.content())
                  .isEqualTo("Document doc-1 is already in the conversation.");
              assertThat(result.properties()).isEmpty();
            });
    assertThat(store.opened.get()).isZero();
  }

  @Test
  void loadsADocumentWhoseTagIsOnlyAReference() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var referenceTag = DocumentReferenceXmlTag.from(entry).toContent();

    final var split =
        run(
            List.of(entry),
            List.of(UserMessage.builder().content(List.of(referenceTag)).build()),
            call("1", "doc-1"));

    assertThat(split.localResults().getFirst().properties())
        .containsEntry(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID, "doc-1");
  }

  @Test
  void rejectsADocumentAboveTheTextLimitByItsKnownSizeWithoutReadingIt() {
    store.readable = false;

    final var split =
        run(List.of(camundaEntry("doc-1", "text/csv", 300L * 1024)), List.of(), call("1", "doc-1"));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.content())
                  .isEqualTo(
                      "The document is 300 KB, too large to load into the conversation (limit 256 KB); pass its id to a tool that processes it.");
              assertThat(result.properties()).isEmpty();
            });
    assertThat(store.opened.get()).isZero();
  }

  @Test
  void acceptsADocumentAtTheLimitByItsKnownSizeWithoutReadingIt() {
    store.readable = false;

    final var split =
        run(
            List.of(camundaEntry("doc-1", "text/plain", TEXT_LIMIT)),
            List.of(),
            call("1", "doc-1"));

    assertThat(split.localResults().getFirst().content())
        .isEqualTo(
            "Loaded the document <doc id=\"doc-1\" contentType=\"text/plain\" size=\"262144\" source=\"user_message\" />. Its content follows in the next message.");
    assertThat(store.opened.get()).isZero();
  }

  @Test
  void aBinaryDocumentGetsTheHigherLimit() {
    store.readable = false;

    final var split =
        run(
            List.of(
                camundaEntry("pdf-ok", "application/pdf", 5L * 1024 * 1024),
                camundaEntry("pdf-big", "application/pdf", BINARY_LIMIT + 1)),
            List.of(),
            call("1", "pdf-ok"),
            call("2", "pdf-big"));

    assertThat(split.localResults().get(0).content())
        .isEqualTo(
            "Loaded the document <doc id=\"pdf-ok\" contentType=\"application/pdf\" size=\"5242880\" source=\"user_message\" />. Its content follows in the next message.");
    assertThat(split.localResults().get(1).content().toString())
        .startsWith(
            "The document is 10241 KB, too large to load into the conversation (limit 10240 KB)");
    assertThat(store.opened.get()).isZero();
  }

  @Test
  void rejectsADocumentOfATypeOutsideTheAllowlistWithoutReadingIt() {
    store.readable = false;

    final var split =
        run(
            List.of(
                camundaEntry("zip-1", "application/zip", 10L),
                camundaEntry("tiff-1", "image/tiff", null),
                camundaEntry("png-1", "image/png", 10L)),
            List.of(),
            call("1", "zip-1"),
            call("2", "tiff-1"),
            call("3", "png-1"));

    assertThat(split.localResults().get(0).content())
        .isEqualTo(
            "The document is of type application/zip which cannot be loaded into the conversation; pass its id to a tool that processes it.");
    assertThat(split.localResults().get(0).properties()).isEmpty();
    assertThat(split.localResults().get(1).content().toString())
        .startsWith("The document is of type image/tiff which cannot be loaded");
    assertThat(split.localResults().get(2).content())
        .isEqualTo(
            "Loaded the document <doc id=\"png-1\" contentType=\"image/png\" size=\"10\" source=\"user_message\" />. Its content follows in the next message.");
    assertThat(store.opened.get()).isZero();
  }

  @Test
  void readsAtMostOneByteMoreThanTheTextLimitWhenTheSizeIsUnknown() {
    final var split =
        run(List.of(camundaEntry("doc-1", "text/plain", null)), List.of(), call("1", "doc-1"));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.content())
                  .isEqualTo(
                      "The document is larger than 256 KB, too large to load into the conversation (limit 256 KB); pass its id to a tool that processes it.");
              assertThat(result.properties()).isEmpty();
            });
    assertThat(store.bytesRead.get()).isEqualTo(TEXT_LIMIT + 1);
  }

  @Test
  void readsAtMostOneByteMoreThanTheBinaryLimitWhenTheSizeIsUnknown() {
    final var split =
        run(List.of(camundaEntry("doc-1", "application/pdf", null)), List.of(), call("1", "doc-1"));

    assertThat(split.localResults().getFirst().content().toString())
        .startsWith("The document is larger than 10240 KB");
    assertThat(store.bytesRead.get()).isEqualTo(BINARY_LIMIT + 1);
  }

  @Test
  void loadsADocumentOfUnknownSizeThatIsWithinTheLimit() {
    final var smallStore =
        new CamundaDocumentStore() {
          @Override
          public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public InputStream getDocumentContent(CamundaDocumentReference reference) {
            return new java.io.ByteArrayInputStream(new byte[1000]);
          }

          @Override
          public void deleteDocument(CamundaDocumentReference reference) {}

          @Override
          public String generateLink(
              CamundaDocumentReference reference, DocumentLinkParameters parameters) {
            return "";
          }
        };
    final var smallHandler =
        new LocalToolCallHandlerImpl(
            new LocalToolRegistryImpl(List.of(new GetDocumentTool())),
            objectMapperFor(smallStore),
            mock(DocumentFactory.class));

    final var split =
        smallHandler.handle(
            conversation(List.of(camundaEntry("doc-1", "text/plain", null)), List.of()),
            List.of(call("1", "doc-1")));

    assertThat(split.localResults().getFirst().content())
        .isEqualTo(
            "Loaded the document <doc id=\"doc-1\" contentType=\"text/plain\" source=\"user_message\" />. Its content follows in the next message.");
  }

  @Test
  void answersAnUnreadableDocumentOfUnknownSizeWithAnError() {
    final var failingStore =
        new CamundaDocumentStore() {
          @Override
          public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public InputStream getDocumentContent(CamundaDocumentReference reference) {
            throw new IllegalStateException("document expired");
          }

          @Override
          public void deleteDocument(CamundaDocumentReference reference) {}

          @Override
          public String generateLink(
              CamundaDocumentReference reference, DocumentLinkParameters parameters) {
            return "";
          }
        };
    final var failingHandler =
        new LocalToolCallHandlerImpl(
            new LocalToolRegistryImpl(List.of(new GetDocumentTool())),
            objectMapperFor(failingStore),
            mock(DocumentFactory.class));

    final var split =
        failingHandler.handle(
            conversation(List.of(camundaEntry("doc-1", "text/plain", null)), List.of()),
            List.of(call("1", "doc-1")));

    assertThat(split.localResults().getFirst().content().toString())
        .startsWith("The document could not be read to check its size: document expired");
    assertThat(split.localResults().getFirst().properties()).isEmpty();
  }

  @Test
  void tagsADocumentWithoutARegistryEntryFromTheDocumentItself() {
    final var reference = camundaEntry("doc-2", "application/pdf", 20L).reference();
    final var call =
        new LocalToolCall(
            "1", GetDocumentTool.NAME, Map.of("id", "doc-2"), Map.of("id", reference));
    final var context =
        new LocalToolContext(
            CONFIGURATION,
            Map.of(),
            DocumentRegistry.empty(),
            Set.of(),
            null,
            objectMapper,
            mock(DocumentFactory.class));

    final var result = new GetDocumentTool().call(call, context);

    assertThat(result.content())
        .isEqualTo(
            "Loaded the document <doc id=\"doc-2\" contentType=\"application/pdf\" size=\"20\" />. Its content follows in the next message.");
    assertThat(result.properties())
        .containsEntry(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID, "doc-2");
  }

  @Test
  void answersTheCallsOfAMixedBatchAndLeavesTheRealCallToTheProcess() {
    final var split =
        run(
            List.of(camundaEntry("doc-1", "text/plain", 10L)),
            List.of(),
            call("1", "doc-1"),
            new ToolCall("2", "getWeather", Map.of("location", "MUC")));

    assertThat(split.localResults()).extracting(ToolCallResult::id).containsExactly("1");
    assertThat(split.remainingCalls()).extracting(ToolCall::id).containsExactly("2");
  }

  private static ToolCall call(String id, String documentId) {
    return new ToolCall(id, GetDocumentTool.NAME, Map.of("id", documentId));
  }

  private AgentConversation conversation(
      List<DocumentRegistryEntry> entries, List<Message> messages) {
    final var inputMessages = new java.util.ArrayList<Message>();
    inputMessages.add(userMessage("hi"));
    inputMessages.addAll(messages);
    return AgentConversation.rehydrate(
            CONFIGURATION,
            AgentContext.builder()
                .state(AgentState.READY)
                .features(AgentContext.newInstanceFeatures())
                .documents(entries)
                .build(),
            TurnReconstructor.reconstruct(List.of()),
            null,
            inputMessages)
        .ingest(assistantMessage("calling"), AgentMetrics.empty());
  }

  private LocalToolCallHandler.LocalToolCallSplit run(
      List<DocumentRegistryEntry> entries, List<Message> messages, ToolCall... toolCalls) {
    return handler.handle(conversation(entries, messages), List.of(toolCalls));
  }
}
