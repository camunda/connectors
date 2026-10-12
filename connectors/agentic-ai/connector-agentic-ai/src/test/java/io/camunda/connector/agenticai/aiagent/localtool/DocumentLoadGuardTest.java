/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.localtool.DocumentLoadGuard.Rejection.Reason;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DocumentLoadGuardTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document document(byte[] content, String contentType) {
    return documentFactory.create(
        DocumentCreationRequest.from(content).contentType(contentType).fileName("a").build());
  }

  /** A document whose reference metadata has no size: its size is only known by reading it. */
  private static Document documentOfUnknownSize(String contentType, InputStream content) {
    final var metadata = mock(DocumentMetadata.class);
    when(metadata.getContentType()).thenReturn(contentType);
    final var document = mock(Document.class);
    when(document.metadata()).thenReturn(metadata);
    when(document.asInputStream()).thenReturn(content);
    return document;
  }

  /** A Camunda document whose reference metadata carries the size. */
  private static Document camundaDocument(String contentType, long size) {
    final var metadata = mock(DocumentMetadata.class);
    when(metadata.getContentType()).thenReturn(contentType);
    when(metadata.getSize()).thenReturn(size);
    final var document = mock(Document.class);
    when(document.reference()).thenReturn(mock(CamundaDocumentReference.class));
    when(document.metadata()).thenReturn(metadata);
    return document;
  }

  @Test
  void letsALoadableDocumentWithinTheLimitPass() {
    assertThat(DocumentLoadGuard.check(document(new byte[10], "text/plain"))).isEmpty();
    assertThat(DocumentLoadGuard.check(document(new byte[1024 * 1024], "application/pdf")))
        .isEmpty();
  }

  @Test
  void rejectsADocumentOfATypeOutsideTheAllowlistWithoutReadingIt() {
    final var document = document(new byte[10], "application/zip");

    final var rejection = DocumentLoadGuard.check(document).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNSUPPORTED_TYPE);
    assertThat(rejection.problem())
        .isEqualTo("is of type application/zip which cannot be loaded into the conversation");
    assertThat(rejection.retryable()).isFalse();
  }

  @Test
  void rejectsADocumentAboveTheLimitOfItsTypeByItsKnownSizeWithoutReadingIt() {
    final var text = camundaDocument("text/plain", 257L * 1024);
    final var pdf = camundaDocument("application/pdf", 257L * 1024);

    final var rejection = DocumentLoadGuard.check(text).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.TOO_LARGE);
    assertThat(rejection.problem())
        .isEqualTo("is 257 KB, too large to load into the conversation (limit 256 KB)");
    assertThat(rejection.retryable()).isFalse();
    verify(text, never()).asInputStream();
    // the limit of a PDF is higher
    assertThat(DocumentLoadGuard.check(pdf)).isEmpty();
  }

  @Test
  void readsAtMostOneByteMoreThanTheLimitWhenTheSizeIsUnknown() throws IOException {
    final var content = mock(InputStream.class);
    when(content.readNBytes(256 * 1024 + 1)).thenReturn(new byte[256 * 1024 + 1]);
    final var document = documentOfUnknownSize("text/plain", content);

    final var rejection = DocumentLoadGuard.check(document).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.TOO_LARGE);
    assertThat(rejection.problem())
        .isEqualTo("is larger than 256 KB, too large to load into the conversation (limit 256 KB)");
    verify(content).readNBytes(256 * 1024 + 1);
    verify(content, never()).readAllBytes();
  }

  @Test
  void letsAnUnknownSizeWithinTheLimitPass() {
    final var document =
        documentOfUnknownSize("text/plain", new ByteArrayInputStream(new byte[100]));

    assertThat(DocumentLoadGuard.check(document)).isEmpty();
  }

  @Test
  void reportsADocumentThatCannotBeReadAsRetryable() {
    final var failure = new IllegalStateException("store unavailable");
    final var document = documentOfUnknownSize("text/plain", null);
    when(document.asInputStream()).thenThrow(failure);

    final var rejection = DocumentLoadGuard.check(document).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNREADABLE);
    assertThat(rejection.problem())
        .isEqualTo("could not be read to check its size: store unavailable");
    assertThat(rejection.cause()).isSameAs(failure);
    assertThat(rejection.retryable()).isTrue();
  }
}
