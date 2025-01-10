/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.base.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.document.CamundaDocument;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
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
  public void shouldReturnBody_whenMapInputWithoutDocumentAndWithNullValues() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    Map<String, Object> input = new HashMap<>();
    input.put("body", new HashMap<>());
    ((Map<String, Object>) input.get("body")).put("content", null);

    // when
    Object res = documentHelper.parseDocumentsInBody(input, mock(Function.class));

    // then
    assertThat(res).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).containsKey("content")).isTrue();
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isNull();
  }

  @Test
  public void shouldReturnBody_whenMapInputWithoutDocument() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    Map<String, Object> input = Map.of("body", Map.of("content", "no document"));

    // when
    Object res = documentHelper.parseDocumentsInBody(input, mock(Function.class));

    // then
    assertThat(res).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isEqualTo("no document");
  }

  @Test
  public void shouldParseDocuments_InBody_whenMapInput() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    var metadata = mock(DocumentMetadata.class);
    CamundaDocument document =
        new CamundaDocument(
            metadata,
            new CamundaDocumentReferenceImpl("store", "id1", metadata),
            InMemoryDocumentStore.INSTANCE);
    Map<String, Object> input =
        Map.of("body", Map.of("content", Arrays.asList(document, document, document)));
    Function<CamundaDocument, Object> transformer = mock(Function.class);
    when(transformer.apply(document)).thenReturn("transformed".getBytes(StandardCharsets.UTF_8));

    // when
    Object res = documentHelper.parseDocumentsInBody(input, transformer);

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
  public void shouldParseDocuments_InBody_whenListInput() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    var metadata = mock(DocumentMetadata.class);
    CamundaDocument document =
        new CamundaDocument(
            metadata,
            new CamundaDocumentReferenceImpl("store", "id1", metadata),
            InMemoryDocumentStore.INSTANCE);
    List<Object> input = Arrays.asList(document, document, document);
    Function<CamundaDocument, Object> transformer = mock(Function.class);
    when(transformer.apply(document)).thenReturn("transformed".getBytes(StandardCharsets.UTF_8));

    // when
    Object res = documentHelper.parseDocumentsInBody(input, transformer);

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
  public void shouldNotParseDocuments_InBody_whenNoDocumentProvided() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    Map<String, Object> input = Map.of("body", Map.of("content", "no document"));
    Function<CamundaDocument, Object> transformer = mock(Function.class);

    // when
    Object res = documentHelper.parseDocumentsInBody(input, transformer);

    // then
    assertThat(res).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isEqualTo("no document");
  }
}
