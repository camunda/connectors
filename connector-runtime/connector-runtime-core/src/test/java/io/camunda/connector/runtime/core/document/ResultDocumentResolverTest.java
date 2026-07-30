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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorInputException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultDocumentResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private TestDocumentFactory documentFactory;
  private ResultDocumentResolver resolver;

  @BeforeEach
  void setUp() {
    documentFactory = new TestDocumentFactory();
    resolver = new ResultDocumentResolver(documentFactory);
  }

  private JsonNode treeOf(Object value) {
    return objectMapper.valueToTree(value);
  }

  @Test
  void resolvesBareStringArgument() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree = treeOf(Map.of("connectorResultFunction", "createDocument", "value", base64));

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isInstanceOf(Document.class);
    assertThat(((Document) resolved).asByteArray())
        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void resolvesObjectArgumentWithNameAndContentType() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction",
                "createDocument",
                "value",
                Map.of("content", base64, "name", "hello.txt", "contentType", "text/plain")));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.asByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(resolved.metadata().getFileName()).isEqualTo("hello.txt");
    assertThat(resolved.metadata().getContentType()).isEqualTo("text/plain");
  }

  @Test
  void generatesRandomFileNameAndDefaultsContentTypeWhenOmitted() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction", "createDocument", "value", Map.of("content", base64)));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.metadata().getFileName()).isNotBlank();
    assertThat(resolved.metadata().getContentType()).isEqualTo("application/octet-stream");
  }

  @Test
  void resolvesNestedSentinelsInsideObjectsAndArrays() {
    String base64 = Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "files",
                List.of(Map.of("connectorResultFunction", "createDocument", "value", base64)),
                "label",
                "unrelated"));

    @SuppressWarnings("unchecked")
    Map<String, Object> resolved = (Map<String, Object>) resolver.resolve(tree);

    assertThat(resolved.get("label")).isEqualTo("unrelated");
    @SuppressWarnings("unchecked")
    List<Object> files = (List<Object>) resolved.get("files");
    assertThat(files).hasSize(1);
    assertThat(files.get(0)).isInstanceOf(Document.class);
  }

  @Test
  void throwsWhenContentIsMissing() {
    JsonNode tree =
        treeOf(
            Map.of("connectorResultFunction", "createDocument", "value", Map.of("name", "x.txt")));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void throwsWhenContentIsNotValidBase64() {
    JsonNode tree =
        treeOf(Map.of("connectorResultFunction", "createDocument", "value", "not-base64!!"));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void leavesNonSentinelValuesUntouched() {
    JsonNode tree = treeOf(Map.of("a", 1, "b", List.of("x", "y"), "c", true));

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isEqualTo(Map.of("a", 1L, "b", List.of("x", "y"), "c", true));
  }
}
