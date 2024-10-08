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
package io.camunda.connector.http.base.model;

import io.camunda.connector.generator.java.annotation.DataExample;
import java.util.Map;

public record HttpCommonResult(
    int status, Map<String, Object> headers, Object body, String reason) {

  public HttpCommonResult(int status, Map<String, Object> headers, Object body) {
    this(status, headers, body, null);
  }

  @DataExample(id = "basic", feel = "= body.order.id")
  public static HttpCommonResult exampleResult() {
    Map<String, Object> headers = Map.of("Content-Type", "application/json");
    var body = Map.of("order", Map.of("id", "123", "total", "100.00€"));
    return new HttpCommonResult(200, headers, body);
  }
}
