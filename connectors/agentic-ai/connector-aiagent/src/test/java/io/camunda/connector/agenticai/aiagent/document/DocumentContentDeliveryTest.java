/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.chatmodel.LogEventsTestSupport.logsOf;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Decision;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Handled;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Outcome;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentContentDeliveryTest {

  private final DocumentContentDelivery delivery = TestDocuments.documentContentDelivery();

  private final Document text = createDocument("text", "text.txt");
  private final Document other = createDocument("other", "other.txt");
  private final Document archive =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("PK".getBytes(StandardCharsets.UTF_8))
                  .contentType("application/zip")
                  .fileName("archive.zip")
                  .build());

  private final AgentDocuments known =
      AgentDocuments.empty()
          .register(text, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(other, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(archive, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

  private static AgentDocumentId idOf(Document document) {
    return AgentDocumentId.of(document);
  }

  private Decision decide(List<AgentDocumentId> requested, Handled handled) {
    return delivery.deliver(known, requested, handled, TestDocuments.documentFactory());
  }

  private static Outcome delivered(Document document) {
    return new Outcome.Delivered(
        AgentDocumentContent.of(idOf(document)).withDelivery(DocumentDelivery.CONTENT));
  }

  private Outcome.Withheld withheldArchive() {
    return new Outcome.Withheld(
        idOf(archive),
        "Documents of type application/zip cannot be loaded. If one of your tools can process the document, pass the handle to it.");
  }

  @Test
  void deliversTheContentOfAnAllowedDocument() {
    assertThat(decide(List.of(idOf(text)), Handled.of(Set.of())))
        .isEqualTo(
            new Decision(List.of(delivered(text)), new Handled(Set.of(idOf(text)), Map.of())));
  }

  @Test
  void restoresTheDocumentWithTheDocumentFactoryOfTheJob() {
    final var restored = new ArrayList<AgentDocumentId>();
    final var jobDocumentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE) {
          @Override
          public Document resolve(DocumentReference reference) {
            restored.add(AgentDocumentId.of(reference));
            return super.resolve(reference);
          }
        };

    assertThat(
            delivery
                .deliver(known, List.of(idOf(text)), Handled.of(Set.of()), jobDocumentFactory)
                .outcomes())
        .containsExactly(delivered(text));
    assertThat(restored).containsExactly(idOf(text));
  }

  @Test
  void withholdsTheContentOfARejectedDocumentWithAStandaloneNote() {
    assertThat(decide(List.of(idOf(archive)), Handled.of(Set.of())))
        .isEqualTo(
            new Decision(
                List.of(withheldArchive()),
                new Handled(Set.of(), Map.of(idOf(archive), withheldArchive().note()))));
  }

  @Test
  void decidesInTheOrderOfTheFirstRequestAndOnceForEveryDocument() {
    assertThat(
            decide(
                    List.of(idOf(other), idOf(archive), idOf(other), idOf(text)),
                    Handled.of(Set.of()))
                .outcomes())
        .containsExactly(delivered(other), withheldArchive(), delivered(text));
  }

  @Test
  void reportsADocumentThatIsHandledAlreadyAsInTheConversation() {
    assertThat(
            decide(
                List.of(idOf(text), idOf(archive), idOf(other)),
                Handled.of(Set.of(idOf(text), idOf(archive)))))
        .isEqualTo(
            new Decision(
                List.of(
                    new Outcome.AlreadyInConversation(idOf(text)),
                    new Outcome.AlreadyInConversation(idOf(archive)),
                    delivered(other)),
                new Handled(Set.of(idOf(text), idOf(archive), idOf(other)), Map.of())));
  }

  @Test
  void reportsADocumentThatAnEarlierRequestDeliveredAsInTheConversation() {
    final var first = decide(List.of(idOf(text)), Handled.of(Set.of()));

    assertThat(decide(List.of(idOf(text)), first.handled()))
        .isEqualTo(
            new Decision(List.of(new Outcome.AlreadyInConversation(idOf(text))), first.handled()));
  }

  @Test
  void withholdsADocumentThatAnEarlierRequestWithheldAgainWithTheSameNote() {
    final var first = decide(List.of(idOf(archive)), Handled.of(Set.of()));

    assertThat(decide(List.of(idOf(archive)), first.handled()))
        .isEqualTo(new Decision(List.of(withheldArchive()), first.handled()));
  }

  @Test
  void aRequestForADocumentTheAgentDoesNotKnowIsADefect() {
    final var unknown = AgentDocumentId.of("unknown");

    assertThatThrownBy(() -> decide(List.of(unknown), Handled.of(Set.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The content of the document 'unknown' is asked for, but the agent does not know it");
  }

  @Test
  void logsAWithheldDocumentThatIsTooLargeAtInfoWithTheLimitPropertyAndMetadataOnly() {
    final var tinyLimits =
        new DocumentContentDelivery(new DocumentContentGuard(new DocumentContentLimits(1, 1, 1)));

    final var events =
        logsOf(
            DocumentContentDelivery.class,
            () ->
                tinyLimits
                    .deliver(
                        known,
                        List.of(idOf(text)),
                        Handled.of(Set.of()),
                        TestDocuments.documentFactory())
                    .outcomes());

    assertThat(events)
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(
            tuple(
                Level.INFO,
                "The content of the document %s (content type text/plain, size unknown) is not loaded: it is above the limit of 1 bytes. Raise the limit of its kind with the property camunda.connector.agenticai.aiagent.document.content-limits.<text|pdf|image>."
                    .formatted(idOf(text))));
  }

  @Test
  void logsAWithheldDocumentThatCannotBeReadAtWarnWithTheCause() {
    final var failure = new IllegalStateException("store unavailable");
    final var metadata = mock(DocumentMetadata.class);
    when(metadata.getContentType()).thenReturn("text/plain");
    when(metadata.getSize()).thenReturn(-1L);
    final var unreadable = mock(Document.class);
    when(unreadable.metadata()).thenReturn(metadata);
    when(unreadable.asInputStream()).thenThrow(failure);
    final var jobDocumentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE) {
          @Override
          public Document resolve(DocumentReference reference) {
            return unreadable;
          }
        };

    final var events =
        logsOf(
            DocumentContentDelivery.class,
            () ->
                delivery.deliver(
                    known, List.of(idOf(text)), Handled.of(Set.of()), jobDocumentFactory));

    assertThat(events)
        .extracting(
            ILoggingEvent::getLevel,
            ILoggingEvent::getFormattedMessage,
            event -> event.getThrowableProxy().getMessage())
        .containsExactly(
            tuple(
                Level.WARN,
                "The content of the document %s (content type text/plain, size unknown) is not loaded: it cannot be read."
                    .formatted(idOf(text)),
                "store unavailable"));
  }
}
