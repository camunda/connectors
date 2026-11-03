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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsTheScriptLogsOfAnExecutor() throws Exception {
    final var response =
        mapper.readValue(
            """
            {"contractVersion": "1", "outcome": "FAILED",
             "error": {"code": "SCRIPT_ERROR", "message": "KeyError: 'total'", "retryable": false},
             "logs": "loaded 3 order lines\\n", "logsTruncated": true}
            """,
            ExecutionResponse.class);

    assertThat(response.logs()).isEqualTo("loaded 3 order lines\n");
    assertThat(response.logsTruncated()).isTrue();
    assertThat(response.error().code()).isEqualTo("SCRIPT_ERROR");
  }

  @Test
  void hasNoLogsWhenTheExecutorSendsNone() throws Exception {
    final var response =
        mapper.readValue(
            """
            {"contractVersion": "1", "outcome": "COMPLETED", "variables": {"sum": 5},
             "durationMs": 12}
            """,
            ExecutionResponse.class);

    assertThat(response.logs()).isNull();
    assertThat(response.logsTruncated()).isFalse();
    assertThat(response.variables()).isEqualTo(Map.of("sum", 5));
  }

  @Test
  void hasNoLogsInTheResponsesOfTheFactoryMethods() {
    assertThat(ExecutionResponse.completed(Map.of()).logs()).isNull();
    assertThat(ExecutionResponse.failed(ErrorCode.TIMEOUT, "late").logs()).isNull();
    assertThat(ExecutionResponse.failed(ErrorCode.TIMEOUT, "late").logsTruncated()).isFalse();
  }
}
