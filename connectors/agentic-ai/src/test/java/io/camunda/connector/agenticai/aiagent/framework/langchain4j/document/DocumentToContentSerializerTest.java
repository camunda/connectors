/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.VideoContent;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class DocumentToContentSerializerTest {

  @Spy
  private final DocumentToContentConverter contentConverter = new DocumentToContentConverterImpl();

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new DocumentToContentModule(contentConverter));

    documentStore.clear();
  }

  @ParameterizedTest
  @CsvSource({
    "test.txt,text/plain",
    "test.csv,text/csv",
    "test.json,application/json",
    "test.xml,application/xml",
    "test.yaml,application/yaml"
  })
  void serializesTextTypesAsText(String filename, String contentType) throws Exception {
    final var document = createDocument("Hello, world!", contentType, filename);
    final var serializedDocument = objectMapper.writeValueAsString(document);

    JSONAssert.assertEquals(
        """
            {
              "type" : "text",
              "media_type" : "%s",
              "data" : "Hello, world!"
            }
            """
            .formatted(contentType),
        serializedDocument,
        true);
  }

  @ParameterizedTest
  @CsvSource({
    "test.gif,image/gif",
    "test.jpg,image/jpeg",
    "test.pdf,application/pdf",
    "test.png,image/png",
    "test.webp,image/webp"
  })
  void serializesFileContentAsBase64(String filename, String contentType) throws Exception {
    final var document = createDocument("Hello, world!", contentType, filename);
    final var serializedDocument = objectMapper.writeValueAsString(document);

    JSONAssert.assertEquals(
        """
            {
              "type" : "base64",
              "media_type" : "%s",
              "data" : "SGVsbG8sIHdvcmxkIQ=="
            }
            """
            .formatted(contentType),
        serializedDocument,
        true);
  }

  @Test
  void supportsSerializingDocumentsInNestedStructures() throws Exception {
    final var input = new LinkedHashMap<String, Object>();
    input.put("hello", "world");
    input.put("document1", createDocument("Hello, world!", "text/plain", "test.txt"));
    input.put(
        "documents",
        List.of(
            createDocument("<PDF CONTENT>", "application/pdf", "test.pdf"),
            createDocument("<IMAGE CONTENT>", "image/png", "image.png")));
    input.put(
        "map",
        Map.of(
            "some_json", createDocument("{\"foo\": \"bar\"}", "application/json", "test.json"),
            "some_csv", createDocument("foo,bar", "text/csv", "test.csv")));

    final var serialized = objectMapper.writeValueAsString(input);

    JSONAssert.assertEquals(
        """
            {
              "hello": "world",
              "document1": {
                "type": "text",
                "media_type": "text/plain",
                "data": "Hello, world!"
              },
              "documents": [
                {
                  "type": "base64",
                  "media_type": "application/pdf",
                  "data": "PFBERiBDT05URU5UPg=="
                },
                {
                  "type": "base64",
                  "media_type": "image/png",
                  "data": "PElNQUdFIENPTlRFTlQ+"
                }
              ],
              "map": {
                "some_csv": {
                  "type": "text",
                  "media_type": "text/csv",
                  "data": "foo,bar"
                },
                "some_json": {
                  "type": "text",
                  "media_type": "application/json",
                  "data": "{\\"foo\\": \\"bar\\"}"
                }
              }
            }
            """,
        serialized,
        true);
  }

  @Test
  void throwsExceptionOnUnsupportedContentType() {
    final var document = createDocument("Hello, world!", "text/plain", "test.txt");
    when(contentConverter.convert(document))
        .thenReturn(VideoContent.from("<VIDEO CONTENT>", "video/mp4"));

    assertThatThrownBy(() -> objectMapper.writeValueAsString(document))
        .isInstanceOf(JsonMappingException.class)
        .hasMessageStartingWith(
            "Unsupported content block type 'VideoContent' for document with reference");
  }

  private Document createDocument(String content, String contentType, String filename) {
    return createDocument(content.getBytes(StandardCharsets.UTF_8), contentType, filename);
  }

  private Document createDocument(byte[] content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content).contentType(contentType).fileName(filename).build());
  }
}
