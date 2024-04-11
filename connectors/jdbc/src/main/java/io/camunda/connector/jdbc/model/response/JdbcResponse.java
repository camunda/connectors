/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JdbcResponse(Integer modifiedRows, List<Map<String, Object>> resultSet) {
  public static JdbcResponse of(Integer modifiedRows) {
    Objects.requireNonNull(modifiedRows, "modifiedRows must not be null");
    return new JdbcResponse(modifiedRows, null);
  }

  public static JdbcResponse of(List<Map<String, Object>> resultSet) {
    Objects.requireNonNull(resultSet, "resultSet must not be null");
    return new JdbcResponse(null, resultSet);
  }
}
