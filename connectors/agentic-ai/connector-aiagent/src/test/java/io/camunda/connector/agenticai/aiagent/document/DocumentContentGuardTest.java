/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Rejection.Reason;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentContentGuardTest {

  private final DocumentContentGuard guard =
      new DocumentContentGuard(DocumentContentLimits.defaults());

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
  void rejectsAnExternalDocumentWithoutDownloadingIt() {
    final var downloads = new AtomicInteger();
    final var external =
        new ExternalDocument(
            "https://files.example.com/report.pdf?token=secret",
            "report.pdf",
            url -> {
              downloads.incrementAndGet();
              return null;
            });

    final var rejection = guard.check(external).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.EXTERNAL);
    assertThat(rejection.problem())
        .isEqualTo("is an external document, which cannot be loaded into the conversation");
    assertThat(rejection.retryable()).isFalse();
    assertThat(downloads).hasValue(0);
  }

  @Test
  void letsALoadableDocumentWithinTheLimitPass() {
    assertThat(guard.check(document(new byte[10], "text/plain"))).isEmpty();
    assertThat(guard.check(document(new byte[1024 * 1024], "application/pdf"))).isEmpty();
  }

  @Test
  void rejectsADocumentOfATypeOutsideTheAllowlistWithoutReadingIt() {
    final var document = document(new byte[10], "application/zip");

    final var rejection = guard.check(document).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNSUPPORTED_TYPE);
    assertThat(rejection.problem())
        .isEqualTo("is of type application/zip which cannot be loaded into the conversation");
    assertThat(rejection.retryable()).isFalse();
  }

  @Test
  void rejectsADocumentAboveTheLimitOfItsTypeByItsKnownSizeWithoutReadingIt() {
    final var text = camundaDocument("text/plain", 257L * 1024);
    final var pdf = camundaDocument("application/pdf", 257L * 1024);
    final var image = camundaDocument("image/png", 3585L * 1024);

    final var rejection = guard.check(text).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.TOO_LARGE);
    assertThat(rejection.problem())
        .isEqualTo("is 257 KB, too large to load into the conversation (limit 256 KB)");
    assertThat(rejection.retryable()).isFalse();
    verify(text, never()).asInputStream();
    // the limit of a PDF is higher
    assertThat(guard.check(pdf)).isEmpty();
    assertThat(guard.check(image).orElseThrow().problem())
        .isEqualTo("is 3585 KB, too large to load into the conversation (limit 3584 KB)");
    assertThat(guard.check(camundaDocument("application/pdf", 3585L * 1024))).isPresent();
  }

  @Test
  void appliesTheConfiguredLimits() {
    final var strict = new DocumentContentGuard(new DocumentContentLimits(10, 20, 30));

    assertThat(strict.check(camundaDocument("text/plain", 11)).orElseThrow().problem())
        .isEqualTo("is 1 KB, too large to load into the conversation (limit 1 KB)");
    assertThat(strict.check(camundaDocument("text/plain", 10))).isEmpty();
    assertThat(strict.check(camundaDocument("application/pdf", 21))).isPresent();
    assertThat(strict.check(camundaDocument("image/png", 31))).isPresent();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain",
        "text/csv",
        "text/html; charset=utf-8",
        "TEXT/Markdown",
        "application/json",
        "application/json; charset=utf-8",
        "application/xml",
        "application/yaml",
        "application/x-yaml",
        "application/ld+json",
        "application/problem+json",
        "application/xhtml+xml",
        "image/svg+xml",
        "application/pdf",
        "Application/PDF; name=a.pdf",
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "IMAGE/PNG"
      })
  void letsTheContentTypesPass_theProvidersRender(String contentType) {
    assertThat(guard.check(camundaDocument(contentType, 10))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/octet-stream",
        "application/zip",
        "application/msword",
        "application/csv",
        "application/sql",
        "image/tiff",
        "image/bmp",
        "audio/mpeg",
        "video/mp4"
      })
  void rejectsTheContentTypesNoProviderRenders(String contentType) {
    final var rejection = guard.check(camundaDocument(contentType, 10)).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNSUPPORTED_TYPE);
    assertThat(rejection.problem())
        .isEqualTo(
            "is of type %s which cannot be loaded into the conversation".formatted(contentType));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void rejectsADocumentWithoutAContentType(String contentType) {
    final var rejection = guard.check(camundaDocument(contentType, 10)).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNSUPPORTED_TYPE);
    assertThat(rejection.problem())
        .isEqualTo("has no content type, so it cannot be loaded into the conversation");
    assertThat(rejection.retryable()).isFalse();
  }

  @Test
  void rejectsADocumentWithAContentTypeThatCannotBeRead() {
    final var rejection = guard.check(camundaDocument(";;;", 10)).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNSUPPORTED_TYPE);
    assertThat(rejection.problem())
        .isEqualTo(
            "has a content type that cannot be read, so it cannot be loaded into the conversation");
  }

  @Test
  void readsAtMostOneByteMoreThanTheLimitWhenTheSizeIsUnknown() throws IOException {
    final var content = mock(InputStream.class);
    when(content.readNBytes(256 * 1024 + 1)).thenReturn(new byte[256 * 1024 + 1]);
    final var document = documentOfUnknownSize("text/plain", content);

    final var rejection = guard.check(document).orElseThrow();

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

    assertThat(guard.check(document)).isEmpty();
  }

  @Test
  void reportsADocumentThatCannotBeReadAsRetryable() {
    final var failure = new IllegalStateException("store unavailable");
    final var document = documentOfUnknownSize("text/plain", null);
    when(document.asInputStream()).thenThrow(failure);

    final var rejection = guard.check(document).orElseThrow();

    assertThat(rejection.reason()).isEqualTo(Reason.UNREADABLE);
    // the failure is for the log: the text for the model holds no detail of it
    assertThat(rejection.problem()).isEqualTo("could not be read to check its size");
    assertThat(rejection.cause()).isSameAs(failure);
    assertThat(rejection.retryable()).isTrue();
  }
}
