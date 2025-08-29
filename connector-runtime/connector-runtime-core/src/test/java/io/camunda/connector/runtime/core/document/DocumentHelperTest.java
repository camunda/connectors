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
package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.http.client.utils.DocumentHelper;
import io.camunda.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DocumentHelperTest {

  private final TestDocumentFactory factory = new TestDocumentFactory();

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
    Object res = documentHelper.parseDocumentsInBody(input, Mockito.mock(Function.class));

    // then
    Assertions.assertThat(res).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).containsKey("content"))
        .isTrue();
    Assertions.assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content")).isNull();
  }

  @Test
  public void shouldReturnBody_whenMapInputWithoutDocument() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    Map<String, Object> input = Map.of("body", Map.of("content", "no document"));

    // when
    Object res = documentHelper.parseDocumentsInBody(input, Mockito.mock(Function.class));

    // then
    Assertions.assertThat(res).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content"))
        .isEqualTo("no document");
  }

  @Test
  public void shouldParseDocuments_InBody_whenMapInput() {
    // given
    DocumentHelper documentHelper = new DocumentHelper();
    var document =
        factory.create(
            DocumentCreationRequest.from("transformed".getBytes(StandardCharsets.UTF_8))
                .documentId("theId")
                .fileName("theFileName")
                .contentType("text/plain")
                .build());
    Map<String, Object> input =
        Map.of("body", Map.of("content", Arrays.asList(document, document, document)));

    // when
    Object res = documentHelper.parseDocumentsInBody(input, Document::asByteArray);

    // then
    Assertions.assertThat(res).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content"))
        .isInstanceOf(List.class);
    Assertions.assertThat((List<byte[]>) ((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content"))
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
    var document =
        factory.create(
            DocumentCreationRequest.from("transformed".getBytes(StandardCharsets.UTF_8))
                .documentId("theId")
                .fileName("theFileName")
                .contentType("text/plain")
                .build());
    List<Object> input = Arrays.asList(document, document, document);

    // when
    Object res = documentHelper.parseDocumentsInBody(input, Document::asByteArray);

    // then
    Assertions.assertThat(res).isInstanceOf(List.class);
    Assertions.assertThat((List<byte[]>) res)
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
    Function<Document, Object> transformer = Mockito.mock(Function.class);

    // when
    Object res = documentHelper.parseDocumentsInBody(input, transformer);

    // then
    Assertions.assertThat(res).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) res).get("body")).isInstanceOf(Map.class);
    Assertions.assertThat(((Map<?, ?>) ((Map<?, ?>) res).get("body")).get("content"))
        .isEqualTo("no document");
  }
}
