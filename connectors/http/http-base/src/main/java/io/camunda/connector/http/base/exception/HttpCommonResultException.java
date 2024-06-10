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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.utils.JsonHelper;
import java.io.Serial;
import java.util.Optional;

public class HttpCommonResultException extends ConnectorException {

  @Serial private static final long serialVersionUID = 1L;

  public HttpCommonResultException(HttpCommonResult result) {
    super(
        String.valueOf(result.status()),
        Optional.ofNullable(result.body())
            .map(
                body -> {
                  try {
                    return JsonHelper.isJsonValid(body)
                        ? ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(body)
                        : String.valueOf(body);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .orElse(result.reason()));
  }
}
