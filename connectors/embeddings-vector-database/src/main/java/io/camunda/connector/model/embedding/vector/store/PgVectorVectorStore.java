/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "PGVector", id = PgVectorVectorStore.STORE_PG_VECTOR)
public record PgVectorVectorStore(
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.pgvector.baseUrl",
            label = "Base URL",
            description = "PGVector base URL, i.e. host:port")
        String baseUrl,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.pgvector.databaseName",
            label = "Database",
            description = "PGVector database name")
        String databaseName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.pgvector.userName",
            label = "Username",
            description = "PGVector username")
        String userName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.pgvector.password",
            label = "Password",
            description = "PGVector password")
        String password,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "embeddingsStore.pgvector.tableName",
            label = "Table",
            description = "PGVector table name")
        String tableName)
    implements EmbeddingsVectorStore {
  @TemplateProperty(ignore = true)
  public static final String STORE_PG_VECTOR = "STORE_PG_VECTOR";
}
