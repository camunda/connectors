/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.model.message.content.ObjectContent.objectContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ContentConverterTest {

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  private final ContentConverter contentConverter =
      new ContentConverterImpl(new ObjectMapper(), new DocumentToContentConverterImpl());

  @BeforeEach
  void setUp() {
    documentStore.clear();
  }

  @Nested
  public class ConvertToContent {
    @Test
    void supportsTextContent() throws JsonProcessingException {
      final var result =
          contentConverter.convertToContent(TextContent.textContent("Test text content"));

      assertThat(result).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) result).text())
          .isEqualTo("Test text content");
    }

    @Test
    void supportsDocumentContent() throws JsonProcessingException {
      final Document document = createDocument("<PDF CONTENT>", "application/pdf", "test.pdf");
      final DocumentContent documentContent = DocumentContent.documentContent(document);

      final var content = contentConverter.convertToContent(documentContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.PdfFileContent.class);
      assertThat(((dev.langchain4j.data.message.PdfFileContent) content).pdfFile())
          .satisfies(
              pdfFile -> {
                assertThat(pdfFile.mimeType()).isEqualTo("application/pdf");
                assertThat(pdfFile.base64Data()).isEqualTo("PFBERiBDT05URU5UPg==");
              });
    }

    @Test
    void supportsObjectContent() throws JsonProcessingException {
      final ObjectContent objectContent = objectContent(Map.of("key", "value"));

      final var content = contentConverter.convertToContent(objectContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo("{\"key\":\"value\"}");
    }
  }

  @Nested
  public class ConvertToString {
    @Test
    void supportsNullContent() throws JsonProcessingException {
      final var stringResult = contentConverter.convertToString(null);
      assertThat(stringResult).isEqualTo(null);
    }

    @Test
    void supportsStringContent() throws JsonProcessingException {
      final var stringResult = contentConverter.convertToString("result");
      assertThat(stringResult).isEqualTo("result");
    }

    @Test
    void supportsObjectContent() throws JsonProcessingException, JSONException {
      final Map<String, Object> content = new LinkedHashMap<>();
      content.put("foo", "bar");
      content.put("list", List.of("A", "B", "C"));

      final var stringResult = contentConverter.convertToString(content);
      JSONAssert.assertEquals(
          """
          {
            "foo": "bar",
            "list": [
              "A",
              "B",
              "C"
            ]
          }
          """,
          stringResult,
          true);
    }

    @Test
    void supportsObjectContentContainingCamundaDocuments()
        throws JsonProcessingException, JSONException {
      final var content = new LinkedHashMap<String, Object>();
      content.put("hello", "world");
      content.put("document1", createDocument("Hello, world!", "text/plain", "test.txt"));
      content.put("document2", createDocument("<PDF CONTENT>", "application/pdf", "test.pdf"));

      final var stringResult = contentConverter.convertToString(content);
      JSONAssert.assertEquals(
          """
          {
            "hello": "world",
            "document1": {
              "type": "text",
              "media_type": "text/plain",
              "data": "Hello, world!"
            },
            "document2": {
              "type": "base64",
              "media_type": "application/pdf",
              "data": "PFBERiBDT05URU5UPg=="
            }
          }
          """,
          stringResult,
          true);
    }
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }
}
