/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.salesforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Guards against Salesforce silently drifting from HTTP JSON's auth model: {@link
 * GenerateElementTemplate} isn't wired into the Maven build (unlike connectors that use {@code
 * element-template-generator-maven-plugin}), so nothing previously re-ran it to catch a stale
 * committed {@code element-templates/salesforce-connector.json} after an HTTP JSON change.
 */
class GenerateElementTemplateTest {

  @Test
  void generatedTemplateMatchesCommittedJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new ElementTemplateModule());

    JsonNode generated =
        mapper.readTree(mapper.writeValueAsString(GenerateElementTemplate.generate()));
    JsonNode committed =
        mapper.readTree(Files.readString(GenerateElementTemplate.templateOutputPath()));

    assertThat(generated)
        .as(
            "element-templates/salesforce-connector.json is stale -- rerun `mvn -pl"
                + " connectors/salesforce test-compile exec:java"
                + " -Dexec.mainClass=io.camunda.connector.salesforce.GenerateElementTemplate"
                + " -Dexec.classpathScope=test` and commit the result")
        .isEqualTo(committed);
  }
}
