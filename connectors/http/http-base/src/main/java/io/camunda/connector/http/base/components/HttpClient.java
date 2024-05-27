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
package io.camunda.connector.http.base.components;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;

public interface HttpClient {

  /**
   * Executes the given {@link HttpCommonRequest} and returns the result as a {@link
   * HttpCommonResult}.
   *
   * @param request the {@link HttpCommonRequest} to execute
   * @param remoteExecutionEnabled whether to use the internal Google Function to execute the
   *     request remotely
   * @return the result of the request as a {@link HttpCommonResult}
   * @throws Exception if an error occurs during the execution of the request
   */
  HttpCommonResult execute(HttpCommonRequest request, boolean remoteExecutionEnabled)
      throws Exception;

  /**
   * Executes the given {@link HttpCommonRequest} and returns the result as a {@link
   * HttpCommonResult}.
   *
   * @param request the {@link HttpCommonRequest} to execute
   * @return the result of the request as a {@link HttpCommonResult}
   * @throws Exception if an error occurs during the execution of the request
   */
  default HttpCommonResult execute(HttpCommonRequest request) throws Exception {
    return execute(request, false);
  }
}
