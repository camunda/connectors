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
import io.camunda.connector.feel.FeelConnectorFunctionProvider;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultDocumentResolverTest {

  private static final String CREATE_DOCUMENT =
      FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE;

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
    JsonNode tree = treeOf(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", base64));

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
                CREATE_DOCUMENT,
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
            Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", Map.of("content", base64)));

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
                List.of(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", base64)),
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
            Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", Map.of("name", "x.txt")));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void acceptsExplicitlyEmptyContentInObjectForm() {
    // "" is a valid (if unusual) base64 encoding of zero bytes. createDocument("") — the bare
    // string form — already accepts it; the object form must not reject the same value just
    // because "content" is present but blank.
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction",
                CREATE_DOCUMENT,
                "value",
                Map.of("content", "", "name", "empty.txt")));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.asByteArray()).isEmpty();
  }

  @Test
  void throwsWhenContentIsNotValidBase64() {
    JsonNode tree =
        treeOf(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", "not-base64!!"));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void toleratesLineWrappedBase64() {
    // 76-char MIME-wrapped base64 (line breaks inserted) is common from third-party APIs and must
    // still decode successfully.
    String raw =
        Base64.getEncoder()
            .encodeToString("hello world".repeat(10).getBytes(StandardCharsets.UTF_8));
    String lineWrapped =
        Base64.getMimeEncoder(20, "\r\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(Base64.getDecoder().decode(raw));
    JsonNode tree =
        treeOf(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", lineWrapped));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.asByteArray())
        .isEqualTo("hello world".repeat(10).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsBase64WithEmbeddedPunctuation() {
    // Base64.getMimeDecoder() alone would silently ignore stray punctuation anywhere in the
    // string, masking genuinely corrupt input; the resolver must still reject it.
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    String corrupted = base64.substring(0, 2) + "!!" + base64.substring(2);
    JsonNode tree = treeOf(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", corrupted));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void leavesNonSentinelValuesUntouched() {
    JsonNode tree = treeOf(Map.of("a", 1, "b", List.of("x", "y"), "c", true));

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isEqualTo(Map.of("a", 1, "b", List.of("x", "y"), "c", true));
  }

  @Test
  void doesNotResolveAForgedSentinelThatGuessesTheOldHardcodedDiscriminator() {
    // Simulates attacker-controlled data (e.g. an HTTP response body) that happens to be shaped
    // exactly like the sentinel, guessing the discriminator's pre-nonce literal value
    // ("createDocument",
    // with no runtime-generated suffix). Since the real discriminator is nonce-suffixed at
    // class-load
    // time, this must NOT match and must be left untouched as ordinary data, not resolved into a
    // Document — proving the sentinel cannot be forged by data arriving from outside the process.
    String base64 =
        Base64.getEncoder().encodeToString("attacker payload".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> forgedSentinel =
        Map.of("connectorResultFunction", "createDocument", "value", base64);
    JsonNode tree = treeOf(forgedSentinel);

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isEqualTo(forgedSentinel);
    assertThat(resolved).isNotInstanceOf(Document.class);
  }

  @Test
  void preservesBigNumberPrecisionForSiblingsOfSentinel() {
    // A tree that contains a createDocument sentinel elsewhere must still preserve full numeric
    // precision for its OTHER (non-sentinel) values when walked: scalarValue() must not silently
    // wrap a huge integer through longValue()/doubleValue().
    BigInteger bigNumber = new BigInteger("123456789012345678901234567890");
    BigDecimal bigDecimal = new BigDecimal("123456789012345678901234567890.123456789");
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "bigNumber",
                bigNumber,
                "bigDecimal",
                bigDecimal,
                "files",
                List.of(Map.of("connectorResultFunction", CREATE_DOCUMENT, "value", base64))));

    @SuppressWarnings("unchecked")
    Map<String, Object> resolved = (Map<String, Object>) resolver.resolve(tree);

    assertThat(resolved.get("bigNumber")).isEqualTo(bigNumber);
    assertThat(resolved.get("bigDecimal")).isEqualTo(bigDecimal);
    @SuppressWarnings("unchecked")
    List<Object> files = (List<Object>) resolved.get("files");
    assertThat(files.get(0)).isInstanceOf(Document.class);
  }
}
