/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TemplateMigrationTest {

  @ParameterizedTest
  @CsvSource({
    "embeddings-vector-database-outbound-connector.json,bedrock.migrates-from.json",
    "hybrid/embeddings-vector-database-outbound-connector-hybrid.json,bedrock-hybrid.migrates-from.json"
  })
  void embedsTheMigrationRecipeInEachTemplate(String templateFile, String recipeFile)
      throws IOException {
    var mapper = ConnectorsObjectMapperSupplier.getCopy();
    var recipe =
        (ObjectNode) mapper.readTree(Path.of("element-template-overrides", recipeFile).toFile());
    assertThat(recipe.has("$schema")).isTrue();
    recipe.remove("$schema");

    var template = mapper.readTree(Path.of("element-templates", templateFile).toFile());
    assertThat(template.path("metadata").path("migratesFrom")).isEqualTo(recipe);
  }
}
