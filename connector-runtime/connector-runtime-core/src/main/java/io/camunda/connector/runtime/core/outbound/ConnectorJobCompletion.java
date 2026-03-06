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
package io.camunda.connector.runtime.core.outbound;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;

/**
 * Extended connector response that takes full control of the Zeebe complete command. When returned
 * from {@link io.camunda.connector.api.outbound.OutboundConnectorFunction#execute}, the runtime
 * delegates job completion to {@link #prepareCompleteCommand} instead of using the standard
 * complete command.
 */
public interface ConnectorJobCompletion extends ConnectorResponse {

  /**
   * Builds the Zeebe complete command. The runtime passes the variables computed from result
   * expression evaluation; the implementation may use or ignore them.
   */
  FinalCommandStep<?> prepareCompleteCommand(
      JobClient client, ActivatedJob job, Map<String, Object> variables);

  /**
   * If {@code true}, an {@link io.camunda.connector.runtime.core.error.IgnoreError} from error
   * expression evaluation will be rejected (the job will be failed instead of completed).
   */
  default boolean rejectIgnoreError() {
    return false;
  }
}
