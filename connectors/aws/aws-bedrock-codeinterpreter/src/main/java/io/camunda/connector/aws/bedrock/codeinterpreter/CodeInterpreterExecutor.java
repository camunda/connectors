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
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.response.CodeInterpreterResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import software.amazon.awssdk.services.bedrockagentcore.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolArguments;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

public class CodeInterpreterExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(CodeInterpreterExecutor.class);
  private static final String CODE_INTERPRETER_ID = "aws.codeinterpreter.v1";
  private static final String SESSION_NAME_PREFIX = "camunda-";
  private static final String ERROR_THROTTLED = "THROTTLED";
  private static final String ERROR_CODE_INTERPRETER_FAILED = "CODE_INTERPRETER_FAILED";
  private static final String ERROR_STREAM = "STREAM_ERROR";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final int RETRY_COUNT = 3;
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);
  private static final int MAX_FILES_TO_RETRIEVE = 10;
  private static final long MAX_TOTAL_BYTES = 10 * 1024 * 1024; // 10 MB

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
    try (var session = startSession(request, jobKey)) {
      return invokeCode(session, request.getInput().getCode());
    } catch (ThrottlingException e) {
      throw ConnectorRetryException.builder()
          .errorCode(ERROR_THROTTLED)
          .message("Code Interpreter request was throttled: " + e.getMessage())
          .retries(RETRY_COUNT)
          .backoffDuration(RETRY_BACKOFF)
          .cause(e)
          .build();
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

  private CodeInterpreterSession startSession(CodeInterpreterRequest request, long jobKey) {
    var input = request.getInput();
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

  private CodeInterpreterResponse invokeCode(CodeInterpreterSession session, String code) {
    // List pre-existing files before execution
    var existingFiles = listGeneratedFiles(session.id());

    var execResult =
        invokeTool(
            session.id(),
            ToolName.EXECUTE_CODE,
            ToolArguments.builder().language("python").code(code).build());

    var stdoutBuilder = new StringBuilder();
    var stderrBuilder = new StringBuilder();
    int exitCode = 0;
    double executionTime = 0;

    for (var result : execResult) {
      if (result.structuredContent() != null) {
        var sc = result.structuredContent();
        if (sc.stdout() != null) stdoutBuilder.append(sc.stdout());
        if (sc.stderr() != null) stderrBuilder.append(sc.stderr());
        if (sc.exitCode() != null) exitCode = sc.exitCode();
        if (sc.executionTime() != null) executionTime = sc.executionTime();
      }
    }

    var files = retrieveNewFiles(session.id(), existingFiles);

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(), stderrBuilder.toString(), exitCode, executionTime, files);
  }

  private List<Document> retrieveNewFiles(String sessionId, List<String> existingFiles) {
    var allFiles = listGeneratedFiles(sessionId);
    var newFiles = new ArrayList<>(allFiles);
    newFiles.removeAll(existingFiles);
    newFiles.removeIf(f -> f.startsWith("."));
    if (newFiles.isEmpty()) {
      return List.of();
    }
    LOG.debug("New files generated: {}", newFiles);
    if (newFiles.size() > MAX_FILES_TO_RETRIEVE) {
      LOG.warn(
          "Found {} new files, limiting retrieval to {}", newFiles.size(), MAX_FILES_TO_RETRIEVE);
      newFiles = new ArrayList<>(newFiles.subList(0, MAX_FILES_TO_RETRIEVE));
    }
    var documents = new ArrayList<Document>();
    long totalBytes = 0;
    for (var file : newFiles) {
      try {
        var blocks = readFiles(sessionId, List.of(file));
        var docs = convertToDocuments(blocks, file);
        for (var doc : docs) {
          totalBytes += doc.metadata().getSize();
          if (totalBytes > MAX_TOTAL_BYTES) {
            LOG.warn("Total file size exceeds {} bytes, stopping retrieval", MAX_TOTAL_BYTES);
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

  private List<String> listGeneratedFiles(String sessionId) {
    var listResult =
        invokeTool(
            sessionId, ToolName.LIST_FILES, ToolArguments.builder().directoryPath("").build());

    var paths = new ArrayList<String>();
    for (var result : listResult) {
      if (result.hasContent()) {
        for (var block : result.content()) {
          if (block.name() != null) {
            paths.add(block.name());
          } else if (block.text() != null && !block.text().isBlank()) {
            paths.add(block.text().trim());
          }
        }
      }
    }
    LOG.debug("Files found in sandbox: {}", paths);
    return paths;
  }

  private List<ContentBlock> readFiles(String sessionId, List<String> paths) {
    LOG.debug("Reading files: {}", paths);
    var readResult =
        invokeTool(sessionId, ToolName.READ_FILES, ToolArguments.builder().paths(paths).build());

    var blocks = new ArrayList<ContentBlock>();
    for (var result : readResult) {
      if (result.hasContent()) {
        blocks.addAll(result.content());
      }
    }
    return blocks;
  }

  private List<Document> convertToDocuments(List<ContentBlock> blocks, String fileName) {
    var documents = new ArrayList<Document>();
    for (var block : blocks) {
      byte[] bytes = null;
      String mimeType = DEFAULT_MIME_TYPE;
      if (block.data() != null) {
        bytes = block.data().asByteArray();
        if (block.mimeType() != null) mimeType = block.mimeType();
      } else if (block.resource() != null && block.resource().blob() != null) {
        bytes = block.resource().blob().asByteArray();
        if (block.resource().mimeType() != null) mimeType = block.resource().mimeType();
      } else if (block.resource() != null && block.resource().text() != null) {
        bytes = block.resource().text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (block.resource().mimeType() != null) mimeType = block.resource().mimeType();
        else mimeType = "text/plain";
      }
      if (bytes != null) {
        var name = block.name() != null ? block.name() : fileName;
        documents.add(
            createDocument.apply(
                DocumentCreationRequest.from(bytes).contentType(mimeType).fileName(name).build()));
      }
    }
    return documents;
  }

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

    CompletableFuture<Void> future = asyncClient.invokeCodeInterpreter(request, handler);
    future.join();
    return results;
  }

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
