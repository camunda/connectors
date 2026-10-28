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
package io.camunda.connector.managedscript.api;

import java.util.Optional;

/**
 * The closed set of managed-script error codes of ADR-0008, with what each one does to the job's
 * retries. This table decides the retries; the {@code retryable} flag of a response must agree with
 * it.
 */
public enum ErrorCode {
  SCRIPT_ERROR(Retries.NONE_LEFT),
  TIMEOUT(Retries.NONE_LEFT),
  RESOURCE_LIMIT(Retries.NONE_LEFT),
  INVALID_RESULT(Retries.NONE_LEFT),
  INVALID_INPUT(Retries.NONE_LEFT),
  INVALID_JOB(Retries.NONE_LEFT),
  UNSUPPORTED_DEPENDENCIES(Retries.NONE_LEFT),
  UNSUPPORTED_CONTRACT_VERSION(Retries.NONE_LEFT),
  /** Consumes one retry only when creating the executor again within the job did not help. */
  DEPLOYMENT_MISSING(Retries.CONSUME_ONE),
  PROVISIONING_PENDING(Retries.UNCHANGED),
  PLATFORM_THROTTLED(Retries.CONSUME_ONE),
  PLATFORM_UNAVAILABLE(Retries.CONSUME_ONE),
  PLATFORM_REJECTED(Retries.NONE_LEFT),
  PLATFORM_EXECUTION_FAILED(Retries.NONE_LEFT),
  PLATFORM_INTERRUPTED(Retries.UNCHANGED),
  PLATFORM_ERROR(Retries.CONSUME_ONE);

  /** What a failure does to the job's retries. */
  public enum Retries {
    /** The job loses one retry and waits for the backoff. */
    CONSUME_ONE,
    /** The job keeps its retries and waits for the backoff. */
    UNCHANGED,
    /** The retries are set to 0, which raises an incident. */
    NONE_LEFT
  }

  private final Retries retries;

  ErrorCode(Retries retries) {
    this.retries = retries;
  }

  public Retries retries() {
    return retries;
  }

  /** Whether a response with this code must be flagged {@code retryable}. */
  public boolean retryable() {
    return retries == Retries.CONSUME_ONE && this != DEPLOYMENT_MISSING;
  }

  /** The code with this name, or empty for a code outside the closed set. */
  public static Optional<ErrorCode> of(String name) {
    if (name == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
