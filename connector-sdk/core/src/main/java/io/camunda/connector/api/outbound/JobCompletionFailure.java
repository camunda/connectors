/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.api.outbound;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Describes why a job completion did not succeed. Used as the parameter for {@link
 * JobCompletionListener#onJobCompletionFailed}.
 *
 * <p>The hierarchy separates "things went wrong" from "the connector deliberately chose to fail":
 *
 * <ul>
 *   <li>{@link CommandFailure} — Zeebe rejected or ignored the command we sent.
 *   <li>{@link ExecutionFailed} — the connector or runtime hit an error before/while producing a
 *       usable response (function exception, error-expression evaluation failure, runtime-
 *       synthesized rejection).
 *   <li>{@link BpmnErrorThrown} / {@link JobErrorRaised} — the connector deliberately chose not to
 *       complete normally (via an error expression).
 * </ul>
 *
 * <p>{@link ExecutionFailed}, {@link BpmnErrorThrown} and {@link JobErrorRaised} each carry an
 * optional {@link CommandFailure} that surfaces whether the failJob / throwBpmnError command sent
 * in response was itself accepted by Zeebe.
 */
public sealed interface JobCompletionFailure {

  /** Failure modes originating from the Zeebe command outcome. */
  sealed interface CommandFailure extends JobCompletionFailure {

    /**
     * Zeebe rejected the command we sent (network error, internal error after retries, or other
     * server-side rejection).
     */
    record CommandFailed(Throwable cause) implements CommandFailure {}

    /** The job was superseded — the command got NOT_FOUND (e.g., job was already completed). */
    record CommandIgnored(Throwable cause) implements CommandFailure {}
  }

  /**
   * Job completion failed because the connector or runtime hit an error before/while producing a
   * usable response. Covers connector function exceptions, error-expression evaluation failures,
   * and runtime-synthesized rejections (e.g., {@code IgnoreError} used by a connector that doesn't
   * support it).
   *
   * @param commandFailure {@code null} if Zeebe accepted the failJob command sent in response;
   *     populated if the command itself was rejected.
   */
  record ExecutionFailed(Throwable cause, @Nullable CommandFailure commandFailure)
      implements JobCompletionFailure {}

  /**
   * A throwBpmnError command was issued (e.g., from error expression evaluation).
   *
   * @param commandFailure {@code null} if Zeebe accepted the throwBpmnError command; populated if
   *     the command itself was rejected.
   */
  record BpmnErrorThrown(
      String errorCode,
      String errorMessage,
      Map<String, Object> variables,
      @Nullable CommandFailure commandFailure)
      implements JobCompletionFailure {}

  /**
   * A failJob command was issued (e.g., from error expression evaluation).
   *
   * @param commandFailure {@code null} if Zeebe accepted the failJob command; populated if the
   *     command itself was rejected.
   */
  record JobErrorRaised(
      String errorMessage, Map<String, Object> variables, @Nullable CommandFailure commandFailure)
      implements JobCompletionFailure {}
}
