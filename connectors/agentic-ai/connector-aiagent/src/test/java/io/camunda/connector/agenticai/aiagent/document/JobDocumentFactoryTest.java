/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobDocumentFactoryTest {

  private final DocumentFactory runtimeFactory = mock(DocumentFactory.class);

  @BeforeEach
  void setUp() {
    // every resolve of the runtime factory returns a new document, as for an external document
    when(runtimeFactory.resolve(any())).thenAnswer(invocation -> mock(Document.class));
  }

  @Test
  void resolvesAReferenceOnceAndReturnsTheSameDocumentForIt() {
    final var factory = new JobDocumentFactory(runtimeFactory);
    final var reference = external("https://example.com/a.pdf");

    final var first = factory.resolve(reference);
    final var second = factory.resolve(external("https://example.com/a.pdf"));

    assertThat(second).isSameAs(first);
    verify(runtimeFactory, times(1)).resolve(reference);
  }

  @Test
  void resolvesDifferentReferencesToDifferentDocuments() {
    final var factory = new JobDocumentFactory(runtimeFactory);

    final var a = factory.resolve(external("https://example.com/a.pdf"));
    final var b = factory.resolve(external("https://example.com/b.pdf"));

    assertThat(b).isNotSameAs(a);
    verify(runtimeFactory, times(2)).resolve(any());
  }

  @Test
  void sharesNoDocumentWithTheFactoryOfAnotherJob() {
    final var reference = external("https://example.com/a.pdf");

    final var ofFirstJob = new JobDocumentFactory(runtimeFactory).resolve(reference);
    final var ofSecondJob = new JobDocumentFactory(runtimeFactory).resolve(reference);

    assertThat(ofSecondJob).isNotSameAs(ofFirstJob);
    verify(runtimeFactory, times(2)).resolve(reference);
  }

  @Test
  void createsADocumentWithTheRuntimeFactory() {
    final var request = mock(DocumentCreationRequest.class);
    final var created = mock(Document.class);
    when(runtimeFactory.create(request)).thenReturn(created);

    assertThat(new JobDocumentFactory(runtimeFactory).create(request)).isSameAs(created);
  }

  private static DocumentReference external(String url) {
    return new TestExternalReference(url, "a.pdf");
  }

  private record TestExternalReference(String url, String name)
      implements ExternalDocumentReference {}
}
