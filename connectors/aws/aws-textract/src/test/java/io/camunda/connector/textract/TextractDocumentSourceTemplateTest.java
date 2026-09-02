/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class TextractDocumentSourceTemplateTest {

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private static final File TEMPLATE_FILE =
      new File("element-templates/aws-textract-outbound-connector.json");

  private static JsonNode template() throws Exception {
    return MAPPER.readTree(TEMPLATE_FILE);
  }

  private static JsonNode propertyById(String id) throws Exception {
    return StreamSupport.stream(template().get("properties").spliterator(), false)
        .filter(p -> p.has("id"))
        .filter(p -> id.equals(p.get("id").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(id + " property not found"));
  }

  private static List<String> propertyIds() throws Exception {
    return StreamSupport.stream(template().get("properties").spliterator(), false)
        .filter(p -> p.has("id"))
        .map(p -> p.get("id").asText())
        .toList();
  }

  @Test
  void documentLocationDropdownDistinguishesS3FromUploaded() throws Exception {
    JsonNode dropdown = propertyById("input.documentLocationType");

    assertThat(dropdown.get("label").asText()).isEqualTo("Document location");
    assertThat(dropdown.get("choices"))
        .extracting(c -> c.get("value").asText())
        .containsExactly("UPLOADED", "S3");
  }

  @Test
  void uploadedSourceOffersCamundaAndUrlButNotInline() throws Exception {
    JsonNode dropdown = propertyById("input.document_documentSource");

    assertThat(dropdown.get("choices"))
        .extracting(c -> c.get("value").asText())
        .containsExactly("camunda", "external");
    assertThat(dropdown.get("condition").get("property").asText())
        .isEqualTo("input.documentLocationType");
    assertThat(dropdown.get("condition").get("equals").asText()).isEqualTo("UPLOADED");
  }

  @Test
  void inlineAndFileNameSubPropertiesAreNotGenerated() throws Exception {
    assertThat(propertyIds())
        .doesNotContain(
            "input.document_inline_content",
            "input.document_inline_fileName",
            "input.document_inline_contentType",
            "input.document_external_fileName")
        .contains("input.document_camundaReference", "input.document_external_url");
  }

  @Test
  void composerStillBindsToTheDocumentVariableTheRuntimeReads() throws Exception {
    JsonNode composer = propertyById("input.document__composer");

    assertThat(composer.get("binding").get("name").asText()).isEqualTo("input.document");
    assertThat(composer.get("value").asText()).doesNotContain("\"inline\"");
  }

  @Test
  void responseFormatDropdownDefaultsToJsonAndHidesOnTheAsyncPath() throws Exception {
    JsonNode dropdown = propertyById("documentReturnFormat");

    assertThat(dropdown.get("binding").get("name").asText())
        .isEqualTo("documentReturnFormat.choice");
    assertThat(dropdown.get("value").asText()).isEqualTo("JSON");
    assertThat(dropdown.get("choices"))
        .extracting(c -> c.get("value").asText())
        .containsExactly("JSON", "DOCUMENT");
    assertThat(dropdown.get("condition").get("property").asText())
        .isEqualTo("input.outputConfigS3Bucket");
    assertThat(dropdown.get("condition").get("isActive").asBoolean()).isFalse();
    assertThat(propertyIds()).doesNotContain("documentReturnFormatEncoding");
  }
}
