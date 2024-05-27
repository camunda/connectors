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

package io.camunda.connector.http.base.request;

import io.camunda.connector.http.base.model.HttpCommonRequest;

public interface RequestFactory<T> {

  /**
   * Create a request from a {@link HttpCommonRequest}. This method is used to convert a domain
   * model to a request object that can be executed by the HTTP client of your choice.
   *
   * @param request the domain model
   * @return the request object
   * @throws Exception You can throw any exception you want
   */
  T createHttpRequest(HttpCommonRequest request) throws Exception;
}
