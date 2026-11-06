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

import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Rejection;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Verdict;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
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

  private static final String PASS_THE_HANDLE =
      " If one of your tools can process the document, pass the handle to it.";

  private static final Verdict ALLOWED = new Verdict.Allowed();

  private Rejection rejectionOf(Document document) {
    return rejectionOf(guard, document);
  }

  private static Rejection rejectionOf(DocumentContentGuard guard, Document document) {
    final var verdict = guard.check(document);
    assertThat(verdict).isInstanceOf(Verdict.Rejected.class);
    return ((Verdict.Rejected) verdict).rejection();
  }

  /** A document whose reference metadata has no size: its size is only known by reading it. */
  private static Document documentOfUnknownSize(String contentType, InputStream content) {
    final var metadata = mock(DocumentMetadata.class);
    when(metadata.getContentType()).thenReturn(contentType);
    when(metadata.getSize()).thenReturn(-1L);
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

  /** An external document whose download answers with the given headers and body. */
  private static ExternalDocument externalDocument(
      Map<String, List<String>> headers, byte[] body, AtomicInteger downloads) {
    return new ExternalDocument(
        "https://files.example.com/report?token=secret",
        "report",
        url -> {
          downloads.incrementAndGet();
          return new HttpResponse<>(200, "OK", headers, body);
        });
  }

  @Test
  void letsAnExternalDocumentPassByTheTypeAndTheSizeOfItsResponse() {
    final var downloads = new AtomicInteger();
    final var external =
        externalDocument(
            Map.of("content-type", List.of("application/pdf"), "content-length", List.of("100")),
            new byte[100],
            downloads);

    assertThat(guard.check(external)).isEqualTo(ALLOWED);
    assertThat(downloads).hasValue(1);
  }

  @Test
  void rejectsAnExternalDocumentAboveTheLimitByItsContentLength() {
    final var external =
        externalDocument(
            Map.of("content-type", List.of("text/plain"), "content-length", List.of("300000")),
            new byte[10],
            new AtomicInteger());

    final var rejection = rejectionOf(external);

    assertThat(rejection).isInstanceOf(Rejection.TooLarge.class);
    assertThat(rejection.modelText())
        .isEqualTo("The document is too large to load (293 KB, limit 256 KB)." + PASS_THE_HANDLE);
  }

  @Test
  void measuresTheDownloadedBytesOfAnExternalDocumentWithoutContentLength() {
    final var body = new byte[256 * 1024 + 1];

    final var external =
        externalDocument(Map.of("content-type", List.of("text/plain")), body, new AtomicInteger());

    final var rejection = rejectionOf(external);

    assertThat(rejection).isEqualTo(new Rejection.TooLarge(null, 256 * 1024));
    assertThat(
            guard.check(
                externalDocument(
                    Map.of("content-type", List.of("text/plain")),
                    new byte[256 * 1024],
                    new AtomicInteger())))
        .isEqualTo(ALLOWED);
  }

  @Test
  void rejectsAnExternalDocumentOfATypeNoProviderRenders() {
    final var external =
        externalDocument(
            Map.of("content-type", List.of("application/zip"), "content-length", List.of("10")),
            new byte[10],
            new AtomicInteger());

    final var rejection = rejectionOf(external);

    assertThat(rejection).isInstanceOf(Rejection.UnsupportedType.class);
    assertThat(rejection.modelText())
        .isEqualTo("Documents of type application/zip cannot be loaded." + PASS_THE_HANDLE);
  }

  @Test
  void rejectsAnExternalDocumentWithoutAContentType() {
    final var external =
        externalDocument(
            Map.of("content-length", List.of("10")), new byte[10], new AtomicInteger());

    assertThat(rejectionOf(external).modelText())
        .isEqualTo("A document without a content type cannot be loaded." + PASS_THE_HANDLE);
  }

  @Test
  void reportsAnExternalDocumentThatCannotBeDownloadedAsRetryable() {
    final var failure = new IllegalStateException("connection refused");
    final var external =
        new ExternalDocument(
            "https://files.example.com/report",
            "report",
            url -> {
              throw failure;
            });

    final var rejection = rejectionOf(external);

    assertThat(rejection).isInstanceOf(Rejection.Unreadable.class);
    assertThat(rejection.modelText()).isEqualTo("The document could not be read.");
    assertThat(rejection.cause()).isSameAs(failure);
  }

  @Test
  void letsALoadableDocumentWithinTheLimitPass() {
    assertThat(guard.check(document(new byte[10], "text/plain"))).isEqualTo(ALLOWED);
    assertThat(guard.check(document(new byte[1024 * 1024], "application/pdf"))).isEqualTo(ALLOWED);
  }

  @Test
  void rejectsADocumentOfATypeOutsideTheAllowlistWithoutReadingIt() {
    final var document = document(new byte[10], "application/zip");

    final var rejection = rejectionOf(document);

    assertThat(rejection).isInstanceOf(Rejection.UnsupportedType.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "Documents of type application/zip cannot be loaded. If one of your tools can process the document, pass the handle to it.");
  }

  @Test
  void rejectsADocumentAboveTheLimitOfItsTypeByItsKnownSizeWithoutReadingIt() {
    final var text = camundaDocument("text/plain", 257L * 1024);
    final var pdf = camundaDocument("application/pdf", 257L * 1024);
    final var image = camundaDocument("image/png", 3585L * 1024);

    final var rejection = rejectionOf(text);

    assertThat(rejection).isInstanceOf(Rejection.TooLarge.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "The document is too large to load (257 KB, limit 256 KB). If one of your tools can process the document, pass the handle to it.");
    verify(text, never()).asInputStream();
    // the limit of a PDF is higher
    assertThat(guard.check(pdf)).isEqualTo(ALLOWED);
    assertThat(rejectionOf(image).modelText())
        .isEqualTo(
            "The document is too large to load (3585 KB, limit 3584 KB). If one of your tools can process the document, pass the handle to it.");
    assertThat(guard.check(camundaDocument("application/pdf", 3585L * 1024)))
        .isInstanceOf(Verdict.Rejected.class);
  }

  @Test
  void appliesTheConfiguredLimits() {
    final var strict = new DocumentContentGuard(new DocumentContentLimits(10, 20, 30));

    assertThat(rejectionOf(strict, camundaDocument("text/plain", 11)).modelText())
        .isEqualTo(
            "The document is too large to load (1 KB, limit 1 KB). If one of your tools can process the document, pass the handle to it.");
    assertThat(strict.check(camundaDocument("text/plain", 10))).isEqualTo(ALLOWED);
    assertThat(strict.check(camundaDocument("application/pdf", 21)))
        .isInstanceOf(Verdict.Rejected.class);
    assertThat(strict.check(camundaDocument("image/png", 31))).isInstanceOf(Verdict.Rejected.class);
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
    assertThat(guard.check(camundaDocument(contentType, 10))).isEqualTo(ALLOWED);
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
    final var rejection = rejectionOf(camundaDocument(contentType, 10));

    assertThat(rejection).isInstanceOf(Rejection.UnsupportedType.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "Documents of type %s cannot be loaded.".formatted(contentType) + PASS_THE_HANDLE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void rejectsADocumentWithoutAContentType(String contentType) {
    final var rejection = rejectionOf(camundaDocument(contentType, 10));

    assertThat(rejection).isInstanceOf(Rejection.UnsupportedType.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "A document without a content type cannot be loaded. If one of your tools can process the document, pass the handle to it.");
  }

  @Test
  void rejectsADocumentWithAContentTypeThatCannotBeRead() {
    final var rejection = rejectionOf(camundaDocument(";;;", 10));

    assertThat(rejection).isInstanceOf(Rejection.UnsupportedType.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "A document with a content type that cannot be read cannot be loaded. If one of your tools can process the document, pass the handle to it.");
  }

  @Test
  void readsAtMostOneByteMoreThanTheLimitWhenTheSizeIsUnknown() throws IOException {
    final var content = mock(InputStream.class);
    when(content.readNBytes(256 * 1024 + 1)).thenReturn(new byte[256 * 1024 + 1]);
    final var document = documentOfUnknownSize("text/plain", content);

    final var rejection = rejectionOf(document);

    assertThat(rejection).isInstanceOf(Rejection.TooLarge.class);
    assertThat(rejection.modelText())
        .isEqualTo(
            "The document is too large to load (limit 256 KB). If one of your tools can process the document, pass the handle to it.");
    verify(content).readNBytes(256 * 1024 + 1);
    verify(content, never()).readAllBytes();
  }

  @Test
  void letsAnUnknownSizeWithinTheLimitPass() {
    final var document =
        documentOfUnknownSize("text/plain", new ByteArrayInputStream(new byte[100]));

    assertThat(guard.check(document)).isEqualTo(ALLOWED);
  }

  @Test
  void reportsADocumentThatCannotBeReadAsRetryable() {
    final var failure = new IllegalStateException("store unavailable");
    final var document = documentOfUnknownSize("text/plain", null);
    when(document.asInputStream()).thenThrow(failure);

    final var rejection = rejectionOf(document);

    assertThat(rejection).isInstanceOf(Rejection.Unreadable.class);
    // the failure is for the log: the text for the model holds no detail of it
    assertThat(rejection.modelText()).isEqualTo("The document could not be read.");
    assertThat(rejection.cause()).isSameAs(failure);
  }
}
