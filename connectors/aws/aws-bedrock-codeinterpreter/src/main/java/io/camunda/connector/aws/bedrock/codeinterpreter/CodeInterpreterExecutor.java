/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.response.CodeInterpreterResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterResult;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolArguments;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

public class CodeInterpreterExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(CodeInterpreterExecutor.class);
  private static final String CODE_INTERPRETER_ID = "aws.codeinterpreter.v1";
  private static final String SESSION_NAME_PREFIX = "camunda-";
  private static final String ERROR_CODE_INTERPRETER_FAILED = "CODE_INTERPRETER_FAILED";
  private static final String ERROR_STREAM = "STREAM_ERROR";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final int DEFAULT_MAX_FILES = 10;
  private static final long DEFAULT_MAX_TOTAL_BYTES = 10L * 1024 * 1024;
  private static final long INVOCATION_TIMEOUT_MINUTES = 5;

  private final BedrockAgentCoreClient syncClient;
  private final BedrockAgentCoreAsyncClient asyncClient;
  private final Function<DocumentCreationRequest, Document> createDocument;

  public CodeInterpreterExecutor(
      BedrockAgentCoreClient syncClient,
      BedrockAgentCoreAsyncClient asyncClient,
      Function<DocumentCreationRequest, Document> createDocument) {
    this.syncClient = syncClient;
    this.asyncClient = asyncClient;
    this.createDocument = createDocument;
  }

  public CodeInterpreterResponse execute(CodeInterpreterRequest request, long jobKey) {
    var input = request.getInput();
    if (input == null || input.getCode() == null || input.getCode().isBlank()) {
      throw new ConnectorException(ERROR_CODE_INTERPRETER_FAILED, "Code must not be empty");
    }
    try (var session = startSession(input, jobKey)) {
      return invokeCode(session, input);
    } catch (BedrockAgentCoreException e) {
      var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter error: " + msg, e);
    } catch (ConnectorException e) {
      throw e;
    } catch (Exception e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter error: " + e.getMessage(), e);
    }
  }

  // --- Session management ---

  private CodeInterpreterSession startSession(CodeInterpreterInput input, long jobKey) {
    var builder =
        StartCodeInterpreterSessionRequest.builder()
            .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
            .name(SESSION_NAME_PREFIX + jobKey);
    if (input.getSessionTimeoutSeconds() != null) {
      builder.sessionTimeoutSeconds(input.getSessionTimeoutSeconds());
    }
    var sessionId = syncClient.startCodeInterpreterSession(builder.build()).sessionId();
    return new CodeInterpreterSession(sessionId);
  }

  // --- Code execution ---

  private CodeInterpreterResponse invokeCode(
      CodeInterpreterSession session, CodeInterpreterInput input) {
    var existingFiles = listGeneratedFiles(session.id());

    var execResult =
        invokeTool(
            session.id(),
            ToolName.EXECUTE_CODE,
            ToolArguments.builder().language("python").code(input.getCode()).build());

    var stdoutBuilder = new StringBuilder();
    var stderrBuilder = new StringBuilder();
    int exitCode = 0;
    double executionTime = 0;

    for (var result : execResult) {
      if (result.structuredContent() != null) {
        var sc = result.structuredContent();
        if (sc.stdout() != null) {
          stdoutBuilder.append(sc.stdout());
        }
        if (sc.stderr() != null) {
          stderrBuilder.append(sc.stderr());
        }
        if (sc.exitCode() != null) {
          exitCode = sc.exitCode();
        }
        if (sc.executionTime() != null) {
          executionTime = sc.executionTime();
        }
      }
    }

    int maxFiles = input.getMaxFiles() != null ? input.getMaxFiles() : DEFAULT_MAX_FILES;
    long maxBytes =
        input.getMaxTotalBytes() != null ? input.getMaxTotalBytes() : DEFAULT_MAX_TOTAL_BYTES;
    var files = retrieveNewFiles(session.id(), existingFiles, maxFiles, maxBytes);

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(), stderrBuilder.toString(), exitCode, executionTime, files);
  }

  // --- File retrieval ---

  private List<Document> retrieveNewFiles(
      String sessionId, List<String> existingFiles, int maxFiles, long maxBytes) {
    var newFiles = filterNewFiles(listGeneratedFiles(sessionId), existingFiles, maxFiles);
    if (newFiles.isEmpty()) {
      return List.of();
    }
    LOG.debug("New files generated: {}", newFiles);
    return readFilesWithSizeLimit(sessionId, newFiles, maxBytes);
  }

  private List<String> filterNewFiles(
      List<String> allFiles, List<String> existingFiles, int maxFiles) {
    var newFiles = new ArrayList<>(allFiles);
    newFiles.removeAll(existingFiles);
    newFiles.removeIf(f -> f.startsWith("."));
    if (newFiles.size() > maxFiles) {
      LOG.warn("Found {} new files, limiting retrieval to {}", newFiles.size(), maxFiles);
      return new ArrayList<>(newFiles.subList(0, maxFiles));
    }
    return newFiles;
  }

  private List<Document> readFilesWithSizeLimit(
      String sessionId, List<String> files, long maxBytes) {
    var documents = new ArrayList<Document>();
    long totalBytes = 0;
    for (var file : files) {
      try {
        var blocks = readFileBlocks(sessionId, List.of(file));
        for (var doc : convertToDocuments(blocks, file)) {
          totalBytes += doc.metadata().getSize();
          if (totalBytes > maxBytes) {
            LOG.warn("Total file size exceeds {} bytes, stopping retrieval", maxBytes);
            return documents;
          }
          documents.add(doc);
        }
      } catch (Exception e) {
        LOG.warn("Failed to read file {}: {}", file, e.getMessage());
      }
    }
    return documents;
  }

  /**
   * Lists files in the sandbox working directory. Uses block.name() or block.text() as filename.
   */
  private List<String> listGeneratedFiles(String sessionId) {
    var listResult =
        invokeTool(
            sessionId, ToolName.LIST_FILES, ToolArguments.builder().directoryPath("").build());

    return listResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .map(this::extractFileNameFromListResult)
        .filter(Objects::nonNull)
        .toList();
  }

  private List<ContentBlock> readFileBlocks(String sessionId, List<String> paths) {
    var readResult =
        invokeTool(sessionId, ToolName.READ_FILES, ToolArguments.builder().paths(paths).build());

    return readResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .toList();
  }

  private List<Document> convertToDocuments(List<ContentBlock> blocks, String fileName) {
    var documents = new ArrayList<Document>();
    for (var block : blocks) {
      var data = extractData(block);
      if (data != null) {
        var mimeType = extractMimeType(block);
        documents.add(
            createDocument.apply(
                DocumentCreationRequest.from(data)
                    .contentType(mimeType)
                    .fileName(fileName)
                    .build()));
      }
    }
    return documents;
  }

  // --- Tool invocation ---

  private List<CodeInterpreterResult> invokeTool(
      String sessionId, ToolName toolName, ToolArguments arguments) {
    var request =
        InvokeCodeInterpreterRequest.builder()
            .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
            .sessionId(sessionId)
            .name(toolName)
            .arguments(arguments)
            .build();

    var results = new ArrayList<CodeInterpreterResult>();
    var handler =
        InvokeCodeInterpreterResponseHandler.builder()
            .onEventStream(
                publisher ->
                    publisher.subscribe(
                        event ->
                            event.accept(
                                InvokeCodeInterpreterResponseHandler.Visitor.builder()
                                    .onResult(results::add)
                                    .build())))
            .onResponse(ignored -> {})
            .onError(
                t -> {
                  throw new ConnectorException(
                      ERROR_STREAM, "Error in code interpreter stream: " + t.getMessage());
                })
            .build();

    try {
      asyncClient
          .invokeCodeInterpreter(request, handler)
          .get(INVOCATION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    } catch (TimeoutException e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter invocation timed out", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter invocation interrupted", e);
    } catch (ExecutionException e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED,
          "Code Interpreter error: " + e.getCause().getMessage(),
          e.getCause());
    }
    return results;
  }

  // --- Content block extraction helpers ---

  /**
   * Extracts a filename from a listFiles result block. Uses block.name() if available, otherwise
   * falls back to block.text() which contains the filename in listFiles responses.
   */
  private String extractFileNameFromListResult(ContentBlock block) {
    if (block.name() != null) {
      return block.name();
    }
    if (block.text() != null && !block.text().isBlank()) {
      return block.text().trim();
    }
    return null;
  }

  /** Extracts binary data from a content block, checking data, resource.blob, and resource.text. */
  private byte[] extractData(ContentBlock block) {
    if (block.data() != null) {
      return block.data().asByteArray();
    }
    if (block.resource() != null) {
      if (block.resource().blob() != null) {
        return block.resource().blob().asByteArray();
      }
      if (block.resource().text() != null) {
        return block.resource().text().getBytes(StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  /** Extracts the MIME type from a content block, with fallbacks for different block structures. */
  private String extractMimeType(ContentBlock block) {
    if (block.mimeType() != null) {
      return block.mimeType();
    }
    if (block.resource() != null && block.resource().mimeType() != null) {
      return block.resource().mimeType();
    }
    if (block.resource() != null && block.resource().text() != null) {
      return "text/plain";
    }
    return DEFAULT_MIME_TYPE;
  }

  // --- Session wrapper ---

  /** AutoCloseable wrapper for Code Interpreter sessions. */
  private class CodeInterpreterSession implements AutoCloseable {
    private final String sessionId;

    CodeInterpreterSession(String sessionId) {
      this.sessionId = sessionId;
    }

    String id() {
      return sessionId;
    }

    @Override
    public void close() {
      try {
        syncClient.stopCodeInterpreterSession(
            StopCodeInterpreterSessionRequest.builder()
                .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
                .sessionId(sessionId)
                .build());
      } catch (Exception e) {
        LOG.warn("Failed to stop Code Interpreter session {}: {}", sessionId, e.getMessage());
      }
    }
  }
}
