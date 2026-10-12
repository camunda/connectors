/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.ReferenceRenderedDocument;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelFacingJsonTest {

  private final ModelFacingJson modelFacingJson =
      new ModelFacingJson(TestObjectMapperSupplier.INSTANCE);

  private final Document document =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .fileName("report.txt")
                  .build());

  @Test
  void rendersADocumentAsItsTagAtAnyDepth() throws JsonProcessingException {
    final var tag = "<doc id=\"%s\" fileName=\"report.txt\" contentType=\"text/plain\" />";

    assertThat(modelFacingJson.write(document))
        .isEqualTo(tag.formatted(DocumentHandle.idFor(document)));
    assertThat(modelFacingJson.write(Map.of("files", List.of(document))))
        .isEqualTo(
            "{\"files\":[\"%s\"]}"
                .formatted(tag.formatted(DocumentHandle.idFor(document)).replace("\"", "\\\"")));
  }

  @Test
  void rendersAReferenceRenderedDocumentAsItsDocumentReferenceAtAnyDepth()
      throws JsonProcessingException {
    final var rendered = new ReferenceRenderedDocument(document);
    final var objectMapper = TestObjectMapperSupplier.INSTANCE;

    assertThat(modelFacingJson.write(rendered))
        .isEqualTo(objectMapper.writeValueAsString(document));
    assertThat(modelFacingJson.write(Map.of("files", List.of(rendered))))
        .isEqualTo(objectMapper.writeValueAsString(Map.of("files", List.of(document))))
        .doesNotContain("<doc");
    final JsonNode tree = modelFacingJson.mapper().valueToTree(Map.of("file", rendered));
    final JsonNode expected = objectMapper.valueToTree(Map.of("file", document));
    assertThat(tree).isEqualTo(expected);
  }
}
