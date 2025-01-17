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
package io.camunda.connector.http.base.exception;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConnectorExceptionMapper {

  public static ConnectorException from(HttpCommonResult result) {
    String status = String.valueOf(result.status());
    String reason = Optional.ofNullable(result.reason()).orElse("[no reason]");
    Map<String, Object> headers = result.headers();
    Object body = result.body();
    Map<String, Object> response = new HashMap<>();
    response.put("headers", headers);
    response.put("body", body);
    return new ConnectorExceptionBuilder()
        .errorCode(status)
        .message(reason)
        .errorVariables(Map.of("response", response))
        .build();
  }
}
