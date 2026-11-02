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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Execution contract version 1 request, with the script it runs.
 *
 * @param executionId the job key
 * @param script the source of the script, sent as data to the executor
 * @param variables the script input
 * @param context where the job comes from; engine keys are strings
 * @param timeoutMs the deadline of the script, enforced by the executor
 */
public record ExecutionRequest(
    String executionId,
    String script,
    Map<String, Object> variables,
    Map<String, Object> context,
    long timeoutMs) {

  public static final String CONTRACT_VERSION = "1";

  public String contractVersion() {
    return CONTRACT_VERSION;
  }

  /** The request as an executor reads it, for a provider to serialize. */
  public Map<String, Object> payload() {
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("contractVersion", contractVersion());
    payload.put("executionId", executionId);
    payload.put("script", script);
    payload.put("variables", variables);
    payload.put("context", context);
    payload.put("timeoutMs", timeoutMs);
    return payload;
  }
}
