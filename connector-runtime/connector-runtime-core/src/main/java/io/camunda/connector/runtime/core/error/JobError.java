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
package io.camunda.connector.runtime.core.error;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record JobError(
    String errorMessage, Map<String, Object> variables, Integer retries, Duration retryBackoff)
    implements ConnectorError {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobError.class);

  /**
   * Returns a map of variables including the error message.
   *
   * <p>This method combines the variables from the JobError with an additional "error" key
   * containing the error message. This is useful when creating ErrorResult instances that need to
   * include both custom variables and the error message.
   *
   * <p><b>Important:</b> If the variables map already contains an "error" key, it will be
   * overwritten with the error message from this JobError. This ensures the error message is always
   * available in the process variables under the "error" key for visibility in Operate and other
   * tools.
   *
   * @return a new mutable map containing all variables plus the error message under the "error" key
   */
  public Map<String, Object> variablesWithErrorMessage() {
    if (variables == null) {
      return Map.of("error", errorMessage);
    }
    var result = new HashMap<>(variables);
    if (variables.containsKey("error")) {
      LOGGER.debug(
          "User-provided 'error' key in variables will be overwritten with the error message. "
              + "Original value: {}, will be replaced with: {}",
          variables.get("error"),
          errorMessage);
    }
    result.put("error", errorMessage);
    return result;
  }
}
