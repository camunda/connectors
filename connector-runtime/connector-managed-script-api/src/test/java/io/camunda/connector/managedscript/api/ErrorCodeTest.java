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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.connector.managedscript.api.ErrorCode.Retries;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void hasTheRetriesOfTheTableOfAdr0008() {
    assertThat(
            Arrays.stream(ErrorCode.values())
                .collect(Collectors.toMap(code -> code, ErrorCode::retries)))
        .containsOnly(
            entry(ErrorCode.SCRIPT_ERROR, Retries.NONE_LEFT),
            entry(ErrorCode.TIMEOUT, Retries.NONE_LEFT),
            entry(ErrorCode.RESOURCE_LIMIT, Retries.NONE_LEFT),
            entry(ErrorCode.INVALID_RESULT, Retries.NONE_LEFT),
            entry(ErrorCode.INVALID_INPUT, Retries.NONE_LEFT),
            entry(ErrorCode.INVALID_JOB, Retries.NONE_LEFT),
            entry(ErrorCode.UNSUPPORTED_DEPENDENCIES, Retries.NONE_LEFT),
            entry(ErrorCode.UNSUPPORTED_CONTRACT_VERSION, Retries.NONE_LEFT),
            entry(ErrorCode.DEPLOYMENT_MISSING, Retries.CONSUME_ONE),
            entry(ErrorCode.PROVISIONING_PENDING, Retries.UNCHANGED),
            entry(ErrorCode.PLATFORM_THROTTLED, Retries.CONSUME_ONE),
            entry(ErrorCode.PLATFORM_UNAVAILABLE, Retries.CONSUME_ONE),
            entry(ErrorCode.PLATFORM_REJECTED, Retries.NONE_LEFT),
            entry(ErrorCode.PLATFORM_EXECUTION_FAILED, Retries.NONE_LEFT),
            entry(ErrorCode.PLATFORM_INTERRUPTED, Retries.UNCHANGED),
            entry(ErrorCode.PLATFORM_ERROR, Retries.CONSUME_ONE));
  }

  @Test
  void flagsAsRetryableTheCodesThatConsumeOneRetryExceptAMissingExecutor() {
    assertThat(ErrorCode.values())
        .filteredOn(ErrorCode::retryable)
        .containsExactlyInAnyOrder(
            ErrorCode.PLATFORM_THROTTLED, ErrorCode.PLATFORM_UNAVAILABLE, ErrorCode.PLATFORM_ERROR);
  }

  @Test
  void resolvesOnlyCodesOfTheClosedSet() {
    assertThat(ErrorCode.of("SCRIPT_ERROR")).contains(ErrorCode.SCRIPT_ERROR);
    assertThat(ErrorCode.of("BUILD_FAILED")).isEmpty();
    assertThat(ErrorCode.of(null)).isEmpty();
  }
}
