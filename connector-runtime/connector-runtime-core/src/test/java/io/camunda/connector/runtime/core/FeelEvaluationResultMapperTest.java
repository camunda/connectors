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
package io.camunda.connector.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.TestDocumentFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for security-testing-findings#275: {@link FeelEvaluationResultMapper} binds
 * arbitrary evaluation results — process data, not model text — so it must not dispatch {@code
 * camunda.function.type}, matching its own documented invariant.
 */
class FeelEvaluationResultMapperTest {

  private final TestDocumentFactory documentFactory = new TestDocumentFactory();

  private record ValueHolder(Object value) {}

  private static Map<String, Object> exploitNode() {
    return Map.of(
        "camunda.function.type",
        "createLink",
        "params",
        List.of(Map.of("camunda.document.type", "camunda"), "PT1H"));
  }

  @Test
  void singleTenantMapperRefusesIntrinsicFunctionDispatch() {
    // ObjectMapper#convertValue wraps whatever the deserializer throws as an
    // IllegalArgumentException; the message it carries over is what proves dispatch — not
    // evaluation — is what was refused.
    ObjectMapper mapper = FeelEvaluationResultMapper.create(documentFactory);
    var payload = Map.of("value", exploitNode());

    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> mapper.convertValue(payload, ValueHolder.class));

    assertThat(exception).hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void multiTenantMapperRefusesIntrinsicFunctionDispatch() {
    ObjectMapper mapper = FeelEvaluationResultMapper.create(Map.of("tenant-a", documentFactory));
    var payload = Map.of("value", exploitNode());

    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> mapper.convertValue(payload, ValueHolder.class));

    assertThat(exception).hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void aDocumentReferenceInAnEvaluationResultStillMaterializes() {
    ObjectMapper mapper = FeelEvaluationResultMapper.create(documentFactory);
    Document document =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());
    JsonNode reference = mapper.valueToTree(document);

    var result = mapper.convertValue(Map.of("value", reference), ValueHolder.class);

    assertThat(result.value()).isInstanceOf(Document.class);
    assertThat(((Document) result.value()).asByteArray())
        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void plainDataInAnEvaluationResultStillBinds() {
    ObjectMapper mapper = FeelEvaluationResultMapper.create(documentFactory);

    var result = mapper.convertValue(Map.of("value", "just a string"), ValueHolder.class);

    assertThat(result.value()).isEqualTo("just a string");
  }
}
