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
package io.camunda.connector.runtime.managedcode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** Execution contract version 1 response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionResponse(
    String contractVersion, String outcome, Map<String, Object> variables, Error error) {

  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";
  public static final String DEPLOYMENT_MISSING = "DEPLOYMENT_MISSING";

  public static ExecutionResponse completed(Map<String, Object> variables) {
    return new ExecutionResponse(ExecutionRequest.CONTRACT_VERSION, COMPLETED, variables, null);
  }

  public static ExecutionResponse failed(String code, String message, boolean retryable) {
    return new ExecutionResponse(
        ExecutionRequest.CONTRACT_VERSION,
        FAILED,
        null,
        new Error(code, message, null, retryable, false));
  }

  public boolean isDeploymentMissing() {
    return FAILED.equals(outcome) && error != null && DEPLOYMENT_MISSING.equals(error.code());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Error(
      String code, String message, String stackTrace, boolean retryable, boolean truncated) {}
}
