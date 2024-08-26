/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.document.CamundaDocument;
import io.camunda.document.DocumentMetadata;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DocumentHelperTest {

  @AfterEach
  public void tearDown() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @Test
  public void shouldCreateDocuments_whenMapInput() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    CamundaDocument document =
        new CamundaDocument(
            new DocumentMetadata(Map.of()),
            new CamundaDocumentReferenceImpl("store", "id1", new DocumentMetadata(Map.of())),
            InMemoryDocumentStore.INSTANCE);
    Map<String, Object> input =
        Map.of("body", Map.of("content", Arrays.asList(document, document, document)));
    Function<CamundaDocument, Object> transformer = mock(Function.class);
    when(transformer.apply(document)).thenReturn("transformed".getBytes(StandardCharsets.UTF_8));

    // when
    Object res = documentHelper.createDocuments(input, transformer);

    // then
    assertThat(res).isInstanceOf(Map.class);
    verify(transformer, times(3)).apply(document);
    assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isInstanceOf(List.class);
    assertThat((List<byte[]>) ((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content"))
        .containsAll(
            Arrays.asList(
                "transformed".getBytes(StandardCharsets.UTF_8),
                "transformed".getBytes(StandardCharsets.UTF_8),
                "transformed".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void shouldCreateDocuments_whenListInput() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    CamundaDocument document =
        new CamundaDocument(
            new DocumentMetadata(Map.of()),
            new CamundaDocumentReferenceImpl("store", "id1", new DocumentMetadata(Map.of())),
            InMemoryDocumentStore.INSTANCE);
    List<Object> input = Arrays.asList(document, document, document);
    Function<CamundaDocument, Object> transformer = mock(Function.class);
    when(transformer.apply(document)).thenReturn("transformed".getBytes(StandardCharsets.UTF_8));

    // when
    Object res = documentHelper.createDocuments(input, transformer);

    // then
    assertThat(res).isInstanceOf(List.class);
    verify(transformer, times(3)).apply(document);
    assertThat((List<byte[]>) res)
        .containsAll(
            Arrays.asList(
                "transformed".getBytes(StandardCharsets.UTF_8),
                "transformed".getBytes(StandardCharsets.UTF_8),
                "transformed".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void shouldNotCreateDocuments_whenNoDocumentProvided() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    Map<String, Object> input = Map.of("body", Map.of("content", "no document"));
    Function<CamundaDocument, Object> transformer = mock(Function.class);

    // when
    Object res = documentHelper.createDocuments(input, transformer);

    // then
    assertThat(res).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isEqualTo("no document");
  }
}
