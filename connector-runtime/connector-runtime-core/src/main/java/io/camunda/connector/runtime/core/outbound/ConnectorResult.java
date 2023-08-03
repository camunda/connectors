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

import java.time.Duration;
import java.util.Map;

/** Result container for the {@link ConnectorJobHandler} */
public sealed interface ConnectorResult {

  Object responseValue();

  default boolean isSuccess() {
    return this instanceof SuccessResult;
  }

  record ErrorResult(Object responseValue, Exception exception, int retries, Duration retryBackoff)
      implements ConnectorResult {
    public ErrorResult(Object responseValue, Exception exception, int retries) {
      this(responseValue, exception, retries, null);
    }
  }

  record SuccessResult(Object rawResponse, Map<String, Object> variables)
      implements ConnectorResult {
    public Object responseValue() {
      if (variables == null || variables.isEmpty()) {
        return rawResponse;
      } else {
        return variables;
      }
    }
  }
}
