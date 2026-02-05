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
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.model.message.content.BlobContent;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceBlobDocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.BlobResource;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.TextResource;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.ResourceLinkContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test
    void supportsBinaryContentWithTextMimeType() throws JsonProcessingException {
      final var binaryContent =
          new BlobContent("Hello, world!".getBytes(StandardCharsets.UTF_8), "text/plain", null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo("Hello, world!");
    }

    @Test
    void supportsBinaryContentWithJsonMimeType() throws JsonProcessingException {
      final var binaryContent =
          new BlobContent(
              "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8), "application/json", null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void supportsBinaryContentWithPdfMimeType() throws JsonProcessingException {
      final var pdfData = "<PDF CONTENT>".getBytes(StandardCharsets.UTF_8);
      final var binaryContent = new BlobContent(pdfData, "application/pdf", null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(PdfFileContent.class);
      assertThat(((PdfFileContent) content).pdfFile().base64Data())
          .isEqualTo(Base64.getEncoder().encodeToString(pdfData));
    }

    @Test
    void supportsBinaryContentWithImageMimeType() throws JsonProcessingException {
      final var imageData = "fake-image-data".getBytes(StandardCharsets.UTF_8);
      final var binaryContent = new BlobContent(imageData, "image/png", null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(ImageContent.class);
      assertThat(((ImageContent) content).image().mimeType()).isEqualTo("image/png");
      assertThat(((ImageContent) content).image().base64Data())
          .isEqualTo(Base64.getEncoder().encodeToString(imageData));
    }

    @Test
    void supportsBinaryContentWithNullMimeType() throws JsonProcessingException {
      final var data = "some-data".getBytes(StandardCharsets.UTF_8);
      final var binaryContent = new BlobContent(data, null, null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    void supportsBinaryContentWithUnsupportedMimeType() throws JsonProcessingException {
      final var data = "archive-content".getBytes(StandardCharsets.UTF_8);
      final var binaryContent = new BlobContent(data, "application/zip", null);

      final var content = contentConverter.convertToContent(binaryContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    void supportsEmbeddedResourceContentWithTextResource() throws JsonProcessingException {
      final var textResource = new TextResource("file://test.txt", "text/plain", "Hello, world!");
      final var embeddedContent = new EmbeddedResourceContent(textResource, null);

      final var content = contentConverter.convertToContent(embeddedContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo("Hello, world!");
    }

    @Test
    void supportsEmbeddedResourceContentWithBlobResourceTextMimeType()
        throws JsonProcessingException {
      final var blobResource =
          new BlobResource(
              "file://test.txt", "text/plain", "Hello, world!".getBytes(StandardCharsets.UTF_8));
      final var embeddedContent = new EmbeddedResourceContent(blobResource, null);

      final var content = contentConverter.convertToContent(embeddedContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo("Hello, world!");
    }

    @Test
    void supportsEmbeddedResourceContentWithBlobResourceImageMimeType()
        throws JsonProcessingException {
      final var imageData = "fake-image-data".getBytes(StandardCharsets.UTF_8);
      final var blobResource = new BlobResource("file://test.png", "image/png", imageData);
      final var embeddedContent = new EmbeddedResourceContent(blobResource, null);

      final var content = contentConverter.convertToContent(embeddedContent);

      assertThat(content).isInstanceOf(ImageContent.class);
      assertThat(((ImageContent) content).image().mimeType()).isEqualTo("image/png");
      assertThat(((ImageContent) content).image().base64Data())
          .isEqualTo(Base64.getEncoder().encodeToString(imageData));
    }

    @Test
    void supportsEmbeddedResourceContentWithBlobResourceNullMimeType()
        throws JsonProcessingException {
      final var data = "some-data".getBytes(StandardCharsets.UTF_8);
      final var blobResource = new BlobResource("file://test.bin", null, data);
      final var embeddedContent = new EmbeddedResourceContent(blobResource, null);

      final var content = contentConverter.convertToContent(embeddedContent);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    void supportsEmbeddedResourceContentWithDocumentContent() throws JsonProcessingException {
      final Document document = createDocument("<PDF CONTENT>", "application/pdf", "test.pdf");
      final var documentContent =
          new EmbeddedResourceBlobDocumentContent("file://test.pdf", "application/pdf", document);
      final var embeddedContent = new EmbeddedResourceContent(documentContent, null);

      final var content = contentConverter.convertToContent(embeddedContent);

      assertThat(content).isInstanceOf(PdfFileContent.class);
      assertThat(((PdfFileContent) content).pdfFile().base64Data())
          .isEqualTo("PFBERiBDT05URU5UPg==");
    }

    @Test
    void supportsResourceLinkContent() throws JsonProcessingException {
      final var resourceLink =
          new ResourceLinkContent("file://example.txt", "a-link", "A link", "text/plain", Map.of());

      final var content = contentConverter.convertToContent(resourceLink);

      assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
      assertThat(((dev.langchain4j.data.message.TextContent) content).text())
          .isEqualTo(
              "{\"type\":\"resource_link\",\"uri\":\"file://example.txt\",\"name\":\"a-link\",\"description\":\"A link\",\"mimeType\":\"text/plain\"}");
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
