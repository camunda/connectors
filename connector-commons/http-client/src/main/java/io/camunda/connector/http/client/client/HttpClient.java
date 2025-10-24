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
package io.camunda.connector.http.client.client;

import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMapper;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.HttpClientRequest;

public interface HttpClient {

  /**
   * Executes the given {@link HttpClientRequest} and maps the response body using the provided
   * {@code bodyMapper} function. The response is automatically closed after the mapping is done to
   * prevent resource leaks.
   *
   * @param request the {@link HttpClientRequest} to execute
   * @param responseMapper a function that maps the response to the desired type
   * @param <T> the type of the mapped body
   * @return the result of the request with the mapped body
   * @see ResponseMappers for common body mappers
   */
  <T> HttpResponse<T> execute(HttpClientRequest request, ResponseMapper<T> responseMapper);
}
