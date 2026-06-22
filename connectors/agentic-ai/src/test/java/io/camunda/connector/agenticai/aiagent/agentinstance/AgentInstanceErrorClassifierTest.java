/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.PERMANENT;
import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.RETRYABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ClientHttpException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AgentInstanceErrorClassifierTest {

  @ParameterizedTest
  @MethodSource("retryableExceptions")
  void forCreate_shouldClassifyAsRetryable(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_CREATE.classify(exception)).isEqualTo(RETRYABLE);
  }

  @ParameterizedTest
  @MethodSource("permanentExceptions")
  void forCreate_shouldClassifyAsPermanent(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_CREATE.classify(exception)).isEqualTo(PERMANENT);
  }

  @ParameterizedTest
  @MethodSource("retryableExceptions")
  void forUpdate_shouldClassifyAsRetryable(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_UPDATE.classify(exception)).isEqualTo(RETRYABLE);
  }

  @ParameterizedTest
  @MethodSource("permanentExceptions")
  void forUpdate_shouldClassifyAsPermanent(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_UPDATE.classify(exception)).isEqualTo(PERMANENT);
  }

  @ParameterizedTest
  @MethodSource("retryableExceptions")
  void forHistoryItem_shouldClassifyAsRetryable(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_HISTORY_ITEM.classify(exception))
        .isEqualTo(RETRYABLE);
  }

  @ParameterizedTest
  @MethodSource("permanentExceptions")
  void forHistoryItem_shouldClassifyAsPermanent(Throwable exception) {
    assertThat(AgentInstanceErrorClassifier.FOR_HISTORY_ITEM.classify(exception))
        .isEqualTo(PERMANENT);
  }

  static Stream<Throwable> retryableExceptions() {
    return Stream.of(
        new ClientHttpException(404, "Not Found"),
        new ClientHttpException(500, "Internal Server Error"),
        new ClientHttpException(502, "Bad Gateway"),
        new ClientHttpException(503, "Service Unavailable"),
        new RuntimeException("wrapped", new ClientHttpException(404, "Not Found")),
        new IOException("connection refused"),
        new InterruptedIOException("timeout"),
        new RuntimeException(new IOException("transport")));
  }

  static Stream<Throwable> permanentExceptions() {
    return Stream.of(
        new ClientHttpException(400, "Bad Request"),
        new ClientHttpException(401, "Unauthorized"),
        new ClientHttpException(403, "Forbidden"),
        new ClientHttpException(409, "Conflict"),
        new RuntimeException("wrapped", new ClientHttpException(400, "Bad Request")),
        new RuntimeException("unknown"),
        new IllegalArgumentException("bad arg"));
  }
}
