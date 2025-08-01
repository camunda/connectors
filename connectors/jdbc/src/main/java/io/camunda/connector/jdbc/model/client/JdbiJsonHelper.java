/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.result.UnableToProduceResultException;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.json.Json;

public class JdbiJsonHelper {
  static Map<String, Set<String>> dbJsonTypeMapping =
      Map.of(
          "Microsoft SQL Server",
          Set.of(),
          "MySQL",
          Set.of("JSON"),
          "PostgreSQL",
          Set.of("json", "jsonb"),
          "MariaDB",
          Set.of("JSON"),
          "Oracle",
          Set.of("CLOB", "VARCHAR2"));

  public static ResultIterable<Map<String, Object>> mapToParsedMap(
      String databaseProductName, Query query) {
    return query.map(
        (rs, ctx) -> {
          Map<String, Object> row = new HashMap<>();
          ColumnMapper<JsonNode> jsonMapper =
              ctx.findColumnMapperFor(QualifiedType.of(JsonNode.class).with(Json.class))
                  .orElseThrow();
          // Java SQL ResultSet and ResultSetMetadata columns start with index 1:
          // https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/ResultSetMetaData.html#getColumnTypeName(int)
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            String columnName = rs.getMetaData().getColumnLabel(i);
            Object value = rs.getObject(i);
            if (isJsonColumn(databaseProductName, rs.getMetaData().getColumnTypeName(i))) {
              try {
                value = jsonMapper.map(rs, i, ctx);
              } catch (UnableToProduceResultException ignored) {
                row.put(columnName, value);
              }
            }
            row.put(columnName, value);
          }
          return row;
        });
  }

  private static boolean isJsonColumn(String databaseProductName, String columnTypeName) {
    return dbJsonTypeMapping.getOrDefault(databaseProductName, Set.of()).contains(columnTypeName);
  }
}
