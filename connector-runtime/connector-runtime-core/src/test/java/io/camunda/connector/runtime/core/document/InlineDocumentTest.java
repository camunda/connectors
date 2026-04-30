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

import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InlineDocumentTest {

  @Test
  void textContent_isReturnedAsUtf8Bytes() {
    InlineDocument doc = new InlineDocument("hello world", "greeting.txt", null);

    assertThat(doc.asByteArray()).isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void rawJsonContent_isReturnedAsBytes() {
    // Content already in its captured-by-deserializer form (raw JSON text).
    InlineDocument doc = new InlineDocument("{\"name\":\"Jane\",\"age\":28}", "me.json", null);

    assertThat(new String(doc.asByteArray(), StandardCharsets.UTF_8))
        .isEqualTo("{\"name\":\"Jane\",\"age\":28}");
  }

  @Test
  void emptyStringContent_producesEmptyByteArray() {
    InlineDocument doc = new InlineDocument("", "empty.txt", null);

    assertThat(doc.asByteArray()).isEmpty();
  }

  @Test
  void constructor_throwsConnectorInputExceptionOnNullContent() {
    assertThatThrownBy(() -> new InlineDocument(null, "broken.txt", null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Inline document content must not be null");
  }

  @Test
  void contentTypeInferredFromExtensionWhenContentTypeNotProvided() {
    InlineDocument doc = new InlineDocument("{}", "data.json", null);

    assertThat(doc.metadata().getContentType()).isEqualTo("application/json");
  }

  @Test
  void explicitContentTypeOverridesInferredOne() {
    InlineDocument doc = new InlineDocument("{}", "data.json", "application/x-custom");

    assertThat(doc.metadata().getContentType()).isEqualTo("application/x-custom");
  }

  @Test
  void noNameAndNoContentType_fallsBackToOctetStream() {
    InlineDocument doc = new InlineDocument("anything", null, null);

    assertThat(doc.metadata().getContentType()).isEqualTo("application/octet-stream");
  }

  @Test
  void nameWithoutExtension_fallsBackToOctetStream() {
    InlineDocument doc = new InlineDocument("anything", "noextension", null);

    assertThat(doc.metadata().getContentType()).isEqualTo("application/octet-stream");
  }

  @Test
  void nullName_generatesUuidThatIsStableWithinInstance() {
    InlineDocument doc = new InlineDocument("anything", null, null);

    String first = doc.metadata().getFileName();
    String second = doc.metadata().getFileName();
    assertThat(first).isEqualTo(second).isNotBlank();
  }

  @Test
  void differentInstances_generateDifferentUuids() {
    InlineDocument a = new InlineDocument("x", null, null);
    InlineDocument b = new InlineDocument("x", null, null);

    assertThat(a.metadata().getFileName()).isNotEqualTo(b.metadata().getFileName());
  }

  @Test
  void metadata_sizeMatchesByteArrayLength() {
    InlineDocument doc = new InlineDocument("hello", "msg.txt", null);

    assertThat(doc.metadata().getSize()).isEqualTo((long) doc.asByteArray().length).isEqualTo(5L);
  }

  @Test
  void reference_returnsOriginalContentString() {
    InlineDocument doc = new InlineDocument("{\"foo\":\"bar\"}", "x.json", null);

    InlineDocumentReference ref = (InlineDocumentReference) doc.reference();
    assertThat(ref.content()).isEqualTo("{\"foo\":\"bar\"}");
  }

  @Test
  void reference_carriesGeneratedUuidWhenNoNameProvided() {
    InlineDocument doc = new InlineDocument("x", null, null);

    InlineDocumentReference ref = (InlineDocumentReference) doc.reference();
    // No name provided -> UUID was generated at construction and is now the document's name.
    // It appears in both metadata().getFileName() and reference().name() so a round-trip
    // serialization preserves the same name.
    assertThat(ref.name()).isEqualTo(doc.metadata().getFileName()).isNotBlank();
  }

  @Test
  void generateLink_returnsNull() {
    InlineDocument doc = new InlineDocument("x", "x.txt", null);

    assertThat(doc.generateLink(null)).isNull();
  }
}
