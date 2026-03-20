/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.response.CodeInterpreterResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolArguments;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

public class CodeInterpreterExecutor {

  private static final String CODE_INTERPRETER_ID = "aws.codeinterpreter.v1";

  private final BedrockAgentCoreClient syncClient;
  private final BedrockAgentCoreAsyncClient asyncClient;

  public CodeInterpreterExecutor(
      BedrockAgentCoreClient syncClient, BedrockAgentCoreAsyncClient asyncClient) {
    this.syncClient = syncClient;
    this.asyncClient = asyncClient;
  }

  public CodeInterpreterResponse execute(CodeInterpreterRequest request) {
    String sessionId = null;
    try {
      sessionId = startSession(request);
      return invokeCode(sessionId, request.getCode());
    } catch (ThrottlingException e) {
      throw ConnectorRetryException.builder()
          .errorCode("THROTTLED")
          .message("Code Interpreter request was throttled: " + e.getMessage())
          .cause(e)
          .build();
    } catch (BedrockAgentCoreException e) {
      var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException("CODE_INTERPRETER_FAILED", "Code Interpreter error: " + msg, e);
    } catch (Exception e) {
      throw new ConnectorException(
          "CODE_INTERPRETER_FAILED", "Code Interpreter error: " + e.getMessage(), e);
    } finally {
      if (sessionId != null) {
        stopSession(sessionId);
      }
    }
  }

  private String startSession(CodeInterpreterRequest request) {
    var builder =
        StartCodeInterpreterSessionRequest.builder()
            .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
            .name("camunda-ci-session");
    if (request.getSessionTimeoutSeconds() != null) {
      builder.sessionTimeoutSeconds(request.getSessionTimeoutSeconds());
    }
    return syncClient.startCodeInterpreterSession(builder.build()).sessionId();
  }

  private CodeInterpreterResponse invokeCode(String sessionId, String code) {
    var sdkRequest =
        InvokeCodeInterpreterRequest.builder()
            .codeInterpreterIdentifier(CODE_INTERPRETER_ID)
            .sessionId(sessionId)
            .name(ToolName.EXECUTE_CODE)
            .arguments(ToolArguments.builder().language("python").code(code).build())
            .build();

    var stdoutBuilder = new StringBuilder();
    var stderrBuilder = new StringBuilder();
    var images = new ArrayList<String>();
    var exitCode = new int[] {0};
    var executionTime = new double[] {0};

    var handler =
        InvokeCodeInterpreterResponseHandler.builder()
            .onEventStream(
                publisher ->
                    publisher.subscribe(
                        event ->
                            event.accept(
                                InvokeCodeInterpreterResponseHandler.Visitor.builder()
                                    .onResult(
                                        result -> {
                                          if (result.structuredContent() != null) {
                                            var sc = result.structuredContent();
                                            if (sc.stdout() != null)
                                              stdoutBuilder.append(sc.stdout());
                                            if (sc.stderr() != null)
                                              stderrBuilder.append(sc.stderr());
                                            if (sc.exitCode() != null) exitCode[0] = sc.exitCode();
                                            if (sc.executionTime() != null)
                                              executionTime[0] = sc.executionTime();
                                          }

                                          if (result.hasContent()) {
                                            for (var block : result.content()) {
                                              if (block.data() != null) {
                                                String mimeType =
                                                    block.mimeType() != null
                                                        ? block.mimeType()
                                                        : "image/png";
                                                String b64 =
                                                    Base64.getEncoder()
                                                        .encodeToString(block.data().asByteArray());
                                                images.add("data:" + mimeType + ";base64," + b64);
                                              }
                                            }
                                          }
                                        })
                                    .build())))
            .onResponse(resp -> {})
            .onError(
                t -> {
                  throw new ConnectorException(
                      "STREAM_ERROR", "Error reading code interpreter stream: " + t.getMessage());
                })
            .build();

    CompletableFuture<Void> future = asyncClient.invokeCodeInterpreter(sdkRequest, handler);
    future.join();

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(),
        stderrBuilder.toString(),
        exitCode[0],
        executionTime[0],
        images.isEmpty() ? List.of() : images);
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
