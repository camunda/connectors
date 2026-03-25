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
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.response.CodeInterpreterResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterResult;
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
  private static final String SESSION_NAME = "camunda-ci-session";
  private static final String ERROR_THROTTLED = "THROTTLED";
  private static final String ERROR_CODE_INTERPRETER_FAILED = "CODE_INTERPRETER_FAILED";
  private static final String ERROR_STREAM = "STREAM_ERROR";
  private static final int RETRY_COUNT = 3;
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);
  private static final Set<String> IMAGE_EXTENSIONS =
      Set.of(".png", ".jpg", ".jpeg", ".svg", ".gif", ".bmp", ".webp", ".pdf");

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

  public CodeInterpreterResponse execute(CodeInterpreterRequest request) {
    String sessionId = null;
    try {
      sessionId = startSession(request.getInput());
      return invokeCode(sessionId, request.getInput().getCode());
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
    } catch (Exception e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter error: " + e.getMessage(), e);
    } finally {
      if (sessionId != null) {
        stopSession(sessionId);
      }
    }
  }

  private String startSession(CodeInterpreterInput input) {
    var builder =
        StartCodeInterpreterSessionRequest.builder()
            .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
            .name(SESSION_NAME);
    if (input.getSessionTimeoutSeconds() != null) {
      builder.sessionTimeoutSeconds(input.getSessionTimeoutSeconds());
    }
    return syncClient.startCodeInterpreterSession(builder.build()).sessionId();
  }

  private CodeInterpreterResponse invokeCode(String sessionId, String code) {
    // Step 1: Execute the code
    var execResult =
        invokeTool(
            sessionId,
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

    // Step 2: List files and retrieve any generated images
    var images = retrieveGeneratedFiles(sessionId);

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(), stderrBuilder.toString(), exitCode, executionTime, images);
  }

  private List<Document> retrieveGeneratedFiles(String sessionId) {
    // List files in the working directory
    var listResult =
        invokeTool(
            sessionId, ToolName.LIST_FILES, ToolArguments.builder().directoryPath("").build());

    var imagePaths = new ArrayList<String>();
    for (var result : listResult) {
      if (result.hasContent()) {
        for (var block : result.content()) {
          var name = block.name();
          if (name != null && isImageFile(name)) {
            imagePaths.add(name);
          }
          // Also check text for file listings
          if (block.text() != null && isImageFile(block.text().trim())) {
            imagePaths.add(block.text().trim());
          }
        }
      }
    }

    if (imagePaths.isEmpty()) {
      LOG.debug("No image files found in sandbox");
      return List.of();
    }

    LOG.debug("Found image files to retrieve: {}", imagePaths);

    // Read the image files
    var readResult =
        invokeTool(
            sessionId, ToolName.READ_FILES, ToolArguments.builder().paths(imagePaths).build());

    var documents = new ArrayList<Document>();
    for (var result : readResult) {
      if (result.hasContent()) {
        for (var block : result.content()) {
          byte[] bytes = null;
          String mimeType = "image/png";
          if (block.data() != null) {
            bytes = block.data().asByteArray();
            if (block.mimeType() != null) mimeType = block.mimeType();
          } else if (block.resource() != null && block.resource().blob() != null) {
            bytes = block.resource().blob().asByteArray();
            if (block.resource().mimeType() != null) mimeType = block.resource().mimeType();
          }
          if (bytes != null) {
            var fileName =
                block.name() != null ? block.name() : "image-" + documents.size() + ".png";
            documents.add(
                createDocument.apply(
                    DocumentCreationRequest.from(bytes)
                        .contentType(mimeType)
                        .fileName(fileName)
                        .build()));
          }
        }
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
            .onResponse(resp -> {})
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

  private static boolean isImageFile(String name) {
    var lower = name.toLowerCase();
    return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
  }

  private void stopSession(String sessionId) {
    try {
      syncClient.stopCodeInterpreterSession(
          StopCodeInterpreterSessionRequest.builder()
              .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
              .sessionId(sessionId)
              .build());
    } catch (Exception ignored) {
      // Best-effort cleanup
    }
  }
}
