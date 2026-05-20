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
import org.junit.jupiter.api.Test;

class AgentInstanceErrorClassifierTest {

  // --- Direct ClientHttpException cases ---

  @Test
  void http400_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(400, "Bad Request")))
        .isEqualTo(PERMANENT);
  }

  @Test
  void http401_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(401, "Unauthorized")))
        .isEqualTo(PERMANENT);
  }

  @Test
  void http403_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(403, "Forbidden")))
        .isEqualTo(PERMANENT);
  }

  @Test
  void http404_isRetryable() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(404, "Not Found")))
        .isEqualTo(RETRYABLE);
  }

  @Test
  void http409_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(409, "Conflict")))
        .isEqualTo(PERMANENT);
  }

  @Test
  void http500_isRetryable() {
    assertThat(
            AgentInstanceErrorClassifier.classify(
                new ClientHttpException(500, "Internal Server Error")))
        .isEqualTo(RETRYABLE);
  }

  @Test
  void http503_isRetryable() {
    assertThat(
            AgentInstanceErrorClassifier.classify(
                new ClientHttpException(503, "Service Unavailable")))
        .isEqualTo(RETRYABLE);
  }

  @Test
  void http502_isRetryable() {
    assertThat(AgentInstanceErrorClassifier.classify(new ClientHttpException(502, "Bad Gateway")))
        .isEqualTo(RETRYABLE);
  }

  // --- Wrapped ClientHttpException cases ---

  @Test
  void wrappedHttp404_isRetryable() {
    final var wrapped = new RuntimeException("wrapped", new ClientHttpException(404, "Not Found"));
    assertThat(AgentInstanceErrorClassifier.classify(wrapped)).isEqualTo(RETRYABLE);
  }

  @Test
  void wrappedHttp400_isPermanent() {
    final var wrapped =
        new RuntimeException("wrapped", new ClientHttpException(400, "Bad Request"));
    assertThat(AgentInstanceErrorClassifier.classify(wrapped)).isEqualTo(PERMANENT);
  }

  // --- IO exception cases ---

  @Test
  void ioException_isRetryable() {
    assertThat(AgentInstanceErrorClassifier.classify(new IOException("connection refused")))
        .isEqualTo(RETRYABLE);
  }

  @Test
  void interruptedIoException_isRetryable() {
    assertThat(AgentInstanceErrorClassifier.classify(new InterruptedIOException("timeout")))
        .isEqualTo(RETRYABLE);
  }

  @Test
  void runtimeExceptionWrappingIoException_isRetryable() {
    assertThat(
            AgentInstanceErrorClassifier.classify(
                new RuntimeException(new IOException("transport"))))
        .isEqualTo(RETRYABLE);
  }

  // --- Default fallback ---

  @Test
  void unknownRuntimeException_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new RuntimeException("unknown")))
        .isEqualTo(PERMANENT);
  }

  @Test
  void illegalArgumentException_isPermanent() {
    assertThat(AgentInstanceErrorClassifier.classify(new IllegalArgumentException("bad arg")))
        .isEqualTo(PERMANENT);
  }
}
