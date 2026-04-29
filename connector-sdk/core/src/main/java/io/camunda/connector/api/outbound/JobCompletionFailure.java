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
 * <p>The hierarchy separates two concerns:
 *
 * <ul>
 *   <li>{@link CommandFailure} — Zeebe rejected or ignored the command we sent.
 *   <li>{@link BpmnErrorThrown} / {@link JobErrorRaised} — the connector decided not to complete
 *       normally (via an error expression). They optionally carry a {@link CommandFailure} to
 *       surface the case where the throwBpmnError / failJob command itself was rejected by Zeebe.
 * </ul>
 */
public sealed interface JobCompletionFailure {

  /** Failure modes originating from the Zeebe command outcome. */
  sealed interface CommandFailure extends JobCompletionFailure {

    /**
     * The job could not be completed successfully. This covers Zeebe command failures (network
     * error, internal error after retries) as well as runtime-level rejections (e.g., unsupported
     * error expression for the connector type).
     */
    record CommandFailed(Throwable cause) implements CommandFailure {}

    /** The job was superseded — the command got NOT_FOUND (e.g., job was already completed). */
    record CommandIgnored(Throwable cause) implements CommandFailure {}
  }

  /**
   * A failJob command was issued (e.g., from error expression evaluation).
   *
   * @param commandFailure {@code null} if Zeebe accepted the failJob command; populated if the
   *     command itself was rejected.
   */
  record JobErrorRaised(
      String errorMessage, Map<String, Object> variables, @Nullable CommandFailure commandFailure)
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
}
