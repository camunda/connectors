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
import java.time.Duration;
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
  private static final String DEFAULT_CODE_INTERPRETER_ID = "aws.codeinterpreter.v1";
  private static final String SESSION_NAME_PREFIX = "camunda-";
  private static final String ERROR_CODE_INTERPRETER_FAILED = "CODE_INTERPRETER_FAILED";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final int DEFAULT_MAX_FILES = 10;
  private static final long DEFAULT_MAX_TOTAL_FILE_SIZE = 10L * 1024 * 1024;
  private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(5);

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

  public CodeInterpreterResponse execute(CodeInterpreterRequest request, long elementInstanceKey) {
    var input = request.getInput();
    if (input == null || input.getCode() == null || input.getCode().isBlank()) {
      throw new ConnectorException(ERROR_CODE_INTERPRETER_FAILED, "Code must not be empty");
    }
    try (var session = startSession(input, elementInstanceKey)) {
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

  private CodeInterpreterSession startSession(CodeInterpreterInput input, long elementInstanceKey) {
    var ciId = resolveCodeInterpreterIdentifier(input);
    var timeout =
        input.getSessionTimeout() != null ? input.getSessionTimeout() : DEFAULT_SESSION_TIMEOUT;

    var builder =
        StartCodeInterpreterSessionRequest.builder()
            .codeInterpreterIdentifier(ciId)
            .name(SESSION_NAME_PREFIX + elementInstanceKey)
            .sessionTimeoutSeconds((int) timeout.getSeconds());

    var sessionId = syncClient.startCodeInterpreterSession(builder.build()).sessionId();
    return new CodeInterpreterSession(sessionId, ciId, timeout);
  }

  // --- Code execution ---

  private CodeInterpreterResponse invokeCode(
      CodeInterpreterSession session, CodeInterpreterInput input) {
    var existingFiles = listGeneratedFiles(session);
    var language = input.getLanguage().getValue();

    var execResult =
        invokeTool(
            session,
            ToolName.EXECUTE_CODE,
            ToolArguments.builder().language(language).code(input.getCode()).build());

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

    int maxFiles =
        Math.max(1, input.getMaxFiles() != null ? input.getMaxFiles() : DEFAULT_MAX_FILES);
    long maxFileSize =
        Math.max(
            0,
            input.getMaxTotalFileSize() != null
                ? input.getMaxTotalFileSize()
                : DEFAULT_MAX_TOTAL_FILE_SIZE);
    var files = retrieveNewFiles(session, existingFiles, maxFiles, maxFileSize);

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(), stderrBuilder.toString(), exitCode, executionTime, files);
  }

  // --- File retrieval ---

  private List<Document> retrieveNewFiles(
      CodeInterpreterSession session, List<String> existingFiles, int maxFiles, long maxFileSize) {
    var newFiles = filterNewFiles(listGeneratedFiles(session), existingFiles, maxFiles);
    if (newFiles.isEmpty()) {
      return List.of();
    }
    LOG.debug("New files generated: {}", newFiles);
    return readFilesWithSizeLimit(session, newFiles, maxFileSize);
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
      CodeInterpreterSession session, List<String> files, long maxFileSize) {
    var documents = new ArrayList<Document>();
    long totalBytes = 0;
    for (var file : files) {
      try {
        var blocks = readFileBlocks(session, file);
        var blocksToConvert = new ArrayList<ContentBlock>();
        for (var block : blocks) {
          var blockSize = getBlockSize(block);
          if (totalBytes + blockSize > maxFileSize) {
            LOG.warn("Total file size exceeds {} bytes, stopping retrieval", maxFileSize);
            documents.addAll(convertToDocuments(blocksToConvert, file));
            return documents;
          }
          totalBytes += blockSize;
          blocksToConvert.add(block);
        }
        documents.addAll(convertToDocuments(blocksToConvert, file));
      } catch (Exception e) {
        LOG.warn("Failed to read file {}: {}", file, e.getMessage());
      }
    }
    return documents;
  }

  /** Gets the size of a content block. Package-private for testing. */
  long getBlockSize(ContentBlock block) {
    if (block.size() != null) {
      return block.size();
    }
    if (block.data() != null) {
      return block.data().asByteArray().length;
    }
    if (block.resource() != null) {
      if (block.resource().blob() != null) {
        return block.resource().blob().asByteArray().length;
      }
      if (block.resource().text() != null) {
        return block.resource().text().getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return 0L;
  }

  /** Lists files in the sandbox working directory. */
  private List<String> listGeneratedFiles(CodeInterpreterSession session) {
    var listResult =
        invokeTool(session, ToolName.LIST_FILES, ToolArguments.builder().directoryPath("").build());

    return listResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .map(this::extractFileNameFromListResult)
        .filter(Objects::nonNull)
        .toList();
  }

  private List<ContentBlock> readFileBlocks(CodeInterpreterSession session, String path) {
    var readResult =
        invokeTool(
            session, ToolName.READ_FILES, ToolArguments.builder().paths(List.of(path)).build());

    return readResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .toList();
  }

  /** Converts content blocks to documents. Package-private for testing. */
  List<Document> convertToDocuments(List<ContentBlock> blocks, String fileName) {
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
      CodeInterpreterSession session, ToolName toolName, ToolArguments arguments) {
    var request =
        InvokeCodeInterpreterRequest.builder()
            .codeInterpreterIdentifier(session.codeInterpreterIdentifier())
            .sessionId(session.id())
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
            .build();

    try {
      asyncClient
          .invokeCodeInterpreter(request, handler)
          .get(session.timeout().toMinutes(), TimeUnit.MINUTES);
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

  // --- Helpers ---

  private String resolveCodeInterpreterIdentifier(CodeInterpreterInput input) {
    return input.getCodeInterpreterIdentifier() != null
        ? input.getCodeInterpreterIdentifier()
        : DEFAULT_CODE_INTERPRETER_ID;
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

  /** Extracts binary data from a content block. */
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

  /** Extracts the MIME type from a content block. */
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
    private final String ciIdentifier;
    private final Duration sessionTimeout;

    CodeInterpreterSession(String sessionId, String ciIdentifier, Duration sessionTimeout) {
      this.sessionId = sessionId;
      this.ciIdentifier = ciIdentifier;
      this.sessionTimeout = sessionTimeout;
    }

    String id() {
      return sessionId;
    }

    String codeInterpreterIdentifier() {
      return ciIdentifier;
    }

    Duration timeout() {
      return sessionTimeout;
    }

    @Override
    public void close() {
      try {
        syncClient.stopCodeInterpreterSession(
            StopCodeInterpreterSessionRequest.builder()
                .codeInterpreterIdentifier(ciIdentifier)
                .sessionId(sessionId)
                .build());
      } catch (Exception e) {
        LOG.warn("Failed to stop Code Interpreter session {}: {}", sessionId, e.getMessage());
      }
    }
  }
}
