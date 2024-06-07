/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
