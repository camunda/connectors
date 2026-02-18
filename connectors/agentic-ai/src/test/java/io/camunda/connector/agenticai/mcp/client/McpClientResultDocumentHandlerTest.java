/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.content.McpBlobContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpDocumentContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpEmbeddedResourceContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpResourceLinkContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.*;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.test.document.TestDocument;
import io.camunda.connector.runtime.test.document.TestDocumentMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientResultDocumentHandlerTest {

  @Mock private DocumentFactory documentFactory;

  @InjectMocks private McpClientResultDocumentHandler testee;

  @ParameterizedTest
  @MethodSource("mcpClientResultsWithoutBinaryDocumentContainers")
  void passesThrough_whenNoBinaryDocumentContainer(
      McpClientResult givenResult, McpClientResult expectedAfterTransformation) {
    final var transformedResult = testee.convertBinariesToDocumentsIfPresent(givenResult);

    assertThat(expectedAfterTransformation).isEqualTo(transformedResult);
  }

  @ParameterizedTest
  @MethodSource("mcpClientResultsWithBinaryDocumentContainers")
  void transformsBinaryDocumentContainers_whenPresent(
      McpClientResult givenResult, McpClientResult expectedAfterTransformation) {
    var callCount = new AtomicInteger(0);
    when(documentFactory.create(any()))
        .thenAnswer(
            i -> {
              var request = i.getArgument(0, DocumentCreationRequest.class);

              try (var is = request.content()) {
                var id = "doc-id-" + callCount.getAndIncrement();
                return new TestDocument(
                    is.readAllBytes(), createDocumentMetadata(request), null, id);
              }
            });

    final var transformedResult = testee.convertBinariesToDocumentsIfPresent(givenResult);

    assertThat(transformedResult).usingRecursiveComparison().isEqualTo(expectedAfterTransformation);
  }

  static Stream<Arguments> mcpClientResultsWithoutBinaryDocumentContainers() {
    return Stream.of(
        argumentSet(
            "List tools",
            new McpClientListToolsResult(
                List.of(
                    new McpToolDefinition(
                        "get-commits",
                        "Get Commits",
                        Map.of("owner", "string", "repo", "string")))),
            new McpClientListToolsResult(
                List.of(
                    new McpToolDefinition(
                        "get-commits",
                        "Get Commits",
                        Map.of("owner", "string", "repo", "string"))))),
        argumentSet(
            "List resource templates",
            new McpClientListResourceTemplatesResult(
                List.of(
                    new ResourceTemplate(
                        "uri-{name}",
                        "Resource Template",
                        "A resource template",
                        "application/json"))),
            new McpClientListResourceTemplatesResult(
                List.of(
                    new ResourceTemplate(
                        "uri-{name}",
                        "Resource Template",
                        "A resource template",
                        "application/json")))),
        argumentSet(
            "List resources",
            new McpClientListResourcesResult(
                List.of(
                    new ResourceDescription(
                        "uri", "resource-1", "A resource", "application/json"))),
            new McpClientListResourcesResult(
                List.of(
                    new ResourceDescription(
                        "uri", "resource-1", "A resource", "application/json")))),
        argumentSet(
            "Read resource - with text content",
            new McpClientReadResourceResult(
                List.of(new ResourceData.TextResourceData("uri", "text/plain", "Some text"))),
            new McpClientReadResourceResult(
                List.of(new ResourceData.TextResourceData("uri", "text/plain", "Some text")))),
        argumentSet(
            "List prompts",
            new McpClientListPromptsResult(
                List.of(
                    new PromptDescription(
                        "code_review",
                        "Code review",
                        List.of(
                            new PromptDescription.PromptArgument(
                                "file", "File to review", true))))),
            new McpClientListPromptsResult(
                List.of(
                    new PromptDescription(
                        "code_review",
                        "Code review",
                        List.of(
                            new PromptDescription.PromptArgument(
                                "file", "File to review", true)))))),
        argumentSet(
            "Get single prompt - empty result",
            new McpClientGetPromptResult("Code review", List.of()),
            new McpClientGetPromptResult("Code review", List.of())),
        argumentSet(
            "Get single prompt - no storable mcp data",
            new McpClientGetPromptResult(
                "Code review",
                List.of(
                    new McpClientGetPromptResult.PromptMessage(
                        "user",
                        new McpClientGetPromptResult.TextMessage(
                            "Please review the following code.")))),
            new McpClientGetPromptResult(
                "Code review",
                List.of(
                    new McpClientGetPromptResult.PromptMessage(
                        "user",
                        new McpClientGetPromptResult.TextMessage(
                            "Please review the following code."))))),
        argumentSet(
            "Call tool - with embedded text resource",
            new McpClientCallToolResult(
                "get-resource",
                List.of(
                    new McpEmbeddedResourceContent(
                        new McpEmbeddedResourceContent.TextResource(
                            "uri://resource", "text/plain", "text content"),
                        null)),
                false),
            new McpClientCallToolResult(
                "get-resource",
                List.of(
                    new McpEmbeddedResourceContent(
                        new McpEmbeddedResourceContent.TextResource(
                            "uri://resource", "text/plain", "text content"),
                        null)),
                false)),
        argumentSet(
            "Call tool - with resource link",
            new McpClientCallToolResult(
                "get-link",
                List.of(
                    new McpResourceLinkContent(
                        "uri://external-resource",
                        "a link",
                        "A link!",
                        "application/octet-stream",
                        Map.of("linkMeta", "value"))),
                false),
            new McpClientCallToolResult(
                "get-link",
                List.of(
                    new McpResourceLinkContent(
                        "uri://external-resource",
                        "a link",
                        "A link!",
                        "application/octet-stream",
                        Map.of("linkMeta", "value"))),
                false)));
  }

  static Stream<Arguments> mcpClientResultsWithBinaryDocumentContainers() {
    return Stream.of(
        getSinglePromptWithAllPossibleMessageTypes(),
        readResourceWithBinaryContent(),
        callToolWithBinaryContent(),
        callToolWithEmbeddedBlobResource());
  }

  private static Arguments callToolWithBinaryContent() {
    return argumentSet(
        "Call tool - with binary content",
        new McpClientCallToolResult(
            "get-commits",
            List.of(
                new McpTextContent("text", Map.of()),
                new McpBlobContent("blob".getBytes(StandardCharsets.UTF_8), "image/png", Map.of())),
            false),
        new McpClientCallToolResult(
            "get-commits",
            List.of(
                new McpTextContent("text", Map.of()),
                new McpDocumentContent(
                    new TestDocument(
                        "blob".getBytes(StandardCharsets.UTF_8),
                        new TestDocumentMetadata("image/png", null, null, null, null, null, null),
                        null,
                        "doc-id-0"),
                    Map.of())),
            false));
  }

  private static Arguments callToolWithEmbeddedBlobResource() {
    return argumentSet(
        "Call tool - with embedded blob resource",
        new McpClientCallToolResult(
            "get-resource",
            List.of(
                new McpEmbeddedResourceContent(
                    new McpEmbeddedResourceContent.BlobResource(
                        "uri://resource",
                        "application/pdf",
                        "document data".getBytes(StandardCharsets.UTF_8)),
                    Map.of("meta", "value"))),
            false),
        new McpClientCallToolResult(
            "get-resource",
            List.of(
                new McpEmbeddedResourceContent(
                    new McpEmbeddedResourceContent.BlobDocumentResource(
                        "uri://resource",
                        "application/pdf",
                        new TestDocument(
                            "document data".getBytes(StandardCharsets.UTF_8),
                            createDocumentMetadata("application/pdf"),
                            null,
                            "doc-id-0")),
                    Map.of("meta", "value"))),
            false));
  }

  private static Arguments readResourceWithBinaryContent() {
    return argumentSet(
        "Read resource - with binary content",
        new McpClientReadResourceResult(
            List.of(
                new ResourceData.BlobResourceData(
                    "uri",
                    "application/octet-stream",
                    "Some text".getBytes(StandardCharsets.UTF_8)))),
        new McpClientReadResourceResult(
            List.of(
                new ResourceData.CamundaDocumentResourceData(
                    "uri",
                    "application/octet-stream",
                    new TestDocument(
                        "Some text".getBytes(StandardCharsets.UTF_8),
                        createDocumentMetadata("application/octet-stream"),
                        null,
                        "doc-id-0")))));
  }

  static Arguments getSinglePromptWithAllPossibleMessageTypes() {
    var input =
        new McpClientGetPromptResult(
            "Code review",
            List.of(
                new McpClientGetPromptResult.PromptMessage(
                    "user",
                    new McpClientGetPromptResult.TextMessage("Please review the following code.")),
                new McpClientGetPromptResult.PromptMessage(
                    "assistant",
                    new McpClientGetPromptResult.BlobMessage(
                        "application/pdf", "byte data".getBytes())),
                new McpClientGetPromptResult.PromptMessage(
                    "user",
                    new McpClientGetPromptResult.EmbeddedResourceContent(
                        new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource
                            .TextResource("uri", "text/plain", "Some text"))),
                new McpClientGetPromptResult.PromptMessage(
                    "assistant",
                    new McpClientGetPromptResult.EmbeddedResourceContent(
                        new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource
                            .BlobResource(
                            "uri", "application/octet-stream", "blob data".getBytes())))));

    var expected =
        new McpClientGetPromptResult(
            "Code review",
            List.of(
                new McpClientGetPromptResult.PromptMessage(
                    "user",
                    new McpClientGetPromptResult.TextMessage("Please review the following code.")),
                new McpClientGetPromptResult.PromptMessage(
                    "assistant",
                    new McpClientGetPromptResult.CamundaDocumentReference(
                        new TestDocument(
                            "byte data".getBytes(),
                            createDocumentMetadata("application/pdf"),
                            null,
                            "doc-id-0"))),
                new McpClientGetPromptResult.PromptMessage(
                    "user",
                    new McpClientGetPromptResult.EmbeddedResourceContent(
                        new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource
                            .TextResource("uri", "text/plain", "Some text"))),
                new McpClientGetPromptResult.PromptMessage(
                    "assistant",
                    new McpClientGetPromptResult.EmbeddedResourceContent(
                        new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource
                            .CamundaDocumentReference(
                            "uri",
                            new TestDocument(
                                "blob data".getBytes(),
                                createDocumentMetadata("application/octet-stream"),
                                null,
                                "doc-id-1"))))));

    return argumentSet("Get single prompt - all message types", input, expected);
  }

  private static @NonNull TestDocumentMetadata createDocumentMetadata(
      DocumentCreationRequest request) {
    return createDocumentMetadata(request.contentType());
  }

  private static @NonNull TestDocumentMetadata createDocumentMetadata(String mimeType) {
    return new TestDocumentMetadata(mimeType, null, null, null, null, null, null);
  }
}
