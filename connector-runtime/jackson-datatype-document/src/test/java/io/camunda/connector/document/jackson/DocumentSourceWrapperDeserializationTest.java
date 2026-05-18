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
package io.camunda.connector.document.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.document.jackson.deserializer.DocumentSourceWrapperConverter;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@link DocumentSourceWrapperConverter} produces the canonical {@code
 * camunda.document.type}-discriminated shape from the wrapper shape emitted by the template
 * generator's {@code @TemplateDocumentProperty} sub-fields.
 */
class DocumentSourceWrapperDeserializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void inlineWrapper_convertsToInlineDocumentReference() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": "inline",
          "inline": {
            "content": "hello",
            "fileName": "greeting.txt",
            "contentType": "text/plain"
          }
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);

    JsonNode converted = DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper);

    assertThat(converted).isNotNull();
    assertThat(converted.get("camunda.document.type").asText()).isEqualTo("inline");
    assertThat(converted.get("content").asText()).isEqualTo("hello");
    assertThat(converted.get("name").asText()).isEqualTo("greeting.txt");
    assertThat(converted.get("contentType").asText()).isEqualTo("text/plain");
  }

  @Test
  void inlineWrapper_withNullFileName_preservesNull() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": "inline",
          "inline": {
            "content": "hello",
            "fileName": null,
            "contentType": null
          }
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);
    JsonNode converted = DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper);

    assertThat(converted.get("camunda.document.type").asText()).isEqualTo("inline");
    assertThat(converted.get("content").asText()).isEqualTo("hello");
    assertThat(converted.get("name").isNull()).isTrue();
    assertThat(converted.get("contentType").isNull()).isTrue();
  }

  @Test
  void externalWrapper_convertsToExternalDocumentReference() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": "external",
          "external": {
            "url": "https://example.com/doc.pdf",
            "fileName": "doc.pdf"
          }
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);

    JsonNode converted = DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper);

    assertThat(converted).isNotNull();
    assertThat(converted.get("camunda.document.type").asText()).isEqualTo("external");
    assertThat(converted.get("url").asText()).isEqualTo("https://example.com/doc.pdf");
    assertThat(converted.get("name").asText()).isEqualTo("doc.pdf");
  }

  @Test
  void camundaWrapper_unwrapsToCamundaReference() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": "camunda",
          "camundaReference": {
            "camunda.document.type": "camunda",
            "storeId": "in-memory",
            "documentId": "abc-123",
            "contentHash": "h",
            "metadata": { "fileName": "x.txt" }
          }
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);

    JsonNode converted = DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper);

    assertThat(converted).isNotNull();
    assertThat(converted.get("camunda.document.type").asText()).isEqualTo("camunda");
    assertThat(converted.get("documentId").asText()).isEqualTo("abc-123");
  }

  @Test
  void camundaWrapper_withNullReference_returnsNull() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": "camunda",
          "camundaReference": null
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);

    assertThat(DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper)).isNull();
  }

  @Test
  void emptyDocumentSource_returnsNull() throws Exception {
    String wrapperJson =
        """
        {
          "documentSource": null
        }
        """;
    JsonNode wrapper = mapper.readTree(wrapperJson);

    assertThat(DocumentSourceWrapperConverter.toDocumentReferenceNode(wrapper)).isNull();
  }
}
