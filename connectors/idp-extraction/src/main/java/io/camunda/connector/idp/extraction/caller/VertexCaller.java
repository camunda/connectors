/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.VertexRequestConfiguration;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertexCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(VertexCaller.class);

  public String generateContent(ExtractionRequestData input, GcpProvider baseRequest)
      throws Exception {
    var configuration = (VertexRequestConfiguration) baseRequest.getConfiguration();
    LlmModel llmModel = LlmModel.fromId(input.converseData().modelId());

    try {
      // Build the LangChain4j VertexAI Gemini chat model
      var chatModelBuilder =
          VertexAiGeminiChatModel.builder()
              .project(configuration.getProjectId())
              .location(configuration.getRegion())
              .modelName(input.converseData().modelId())
              .temperature(input.converseData().temperature())
              .topP(input.converseData().topP())
              .responseMimeType("application/json");

      // Set up authentication if needed
      if (baseRequest.getAuthentication() != null) {
        var credentials = GcsUtil.getCredentials(baseRequest.getAuthentication());
        if (credentials instanceof GoogleCredentials googleCredentials) {
          chatModelBuilder.credentials(googleCredentials);
        }
      }

      var chatModel = chatModelBuilder.build();

      // Create user message with document content using LangChain4j's native document handling
      String userMessageText = llmModel.getMessage(input.taxonomyItems());

      List<dev.langchain4j.data.message.Content> messageContents = new ArrayList<>();
      messageContents.add(new TextContent(userMessageText));

      // Convert the document to appropriate LangChain4j content based on content type
      dev.langchain4j.data.message.Content documentContent =
          convertDocumentToContent(input.document());
      if (documentContent != null) {
        messageContents.add(documentContent);
      }

      UserMessage userMessage = UserMessage.from(messageContents);

      ChatResponse response;
      if (llmModel.isSystemPromptAllowed()) {
        response =
            chatModel.chat(List.of(SystemMessage.from(llmModel.getSystemPrompt()), userMessage));
      } else {
        // For models that don't support system prompts, combine system and user messages
        String combinedMessage =
            String.format("%s\n\n%s", llmModel.getSystemPrompt(), userMessageText);
        List<dev.langchain4j.data.message.Content> combinedContents = new ArrayList<>();
        combinedContents.add(new TextContent(combinedMessage));
        if (documentContent != null) {
          combinedContents.add(documentContent);
        }
        response = chatModel.chat(UserMessage.from(combinedContents));
      }

      String output = response.aiMessage().text();
      LOGGER.debug("LangChain4j Vertex AI Gemini response: {}", output);
      return output;

    } catch (Exception e) {
      LOGGER.error("Error while processing document with LangChain4j", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Converts a Camunda document to LangChain4j content based on its content type. Package-visible
   * for testing.
   */
  Content convertDocumentToContent(io.camunda.connector.api.document.Document document) {
    try {
      String contentType = document.metadata().getContentType();

      if (contentType == null) {
        LOGGER.warn("No content type specified for document, treating as PDF");
        contentType = "application/pdf";
      }

      return switch (contentType) {
        case "application/pdf" -> {
          // Handle PDF files - works for both text-based and image-based (scanned) PDFs
          // Gemini can analyze the visual content of scanned documents
          LOGGER.debug("Processing PDF document (may contain images/scans)");
          yield PdfFileContent.from(PdfFile.builder().base64Data(document.asBase64()).build());
        }

        case "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp" ->
            ImageContent.from(
                Image.builder().mimeType(contentType).base64Data(document.asBase64()).build(),
                ImageContent.DetailLevel.AUTO);

        case "text/plain",
                "text/html",
                "text/markdown",
                "application/json",
                "application/xml",
                "text/csv" ->
            new TextContent("Document content:\n" + new String(document.asByteArray()));

        default -> {
          LOGGER.warn("Unsupported content type: {}, treating as PDF", contentType);
          yield PdfFileContent.from(PdfFile.builder().base64Data(document.asBase64()).build());
        }
      };
    } catch (Exception e) {
      LOGGER.error("Error converting document to LangChain4j content", e);
      // Fallback to treating as text
      return new TextContent(
          "Document content (text extraction failed): " + document.reference().toString());
    }
  }
}
