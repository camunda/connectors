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
package io.camunda.connector.runtime.core.intrinsic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.document.jackson.IntrinsicFunctionParams;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.runtime.core.document.TestDocumentFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for security-testing-findings#275: a mapper built with this executor binds
 * data nothing marks as trusted model text (a webhook payload, a correlated variable, a FEEL
 * evaluation result), so it must refuse to dispatch {@code camunda.function.type} regardless of the
 * target field's declared type.
 */
class DisabledIntrinsicFunctionExecutorTest {

  private final TestDocumentFactory documentFactory = new TestDocumentFactory();
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(
              new JacksonModuleDocumentDeserializer(
                  documentFactory,
                  new DisabledIntrinsicFunctionExecutor(),
                  DocumentModuleSettings.create()))
          .registerModule(new JacksonModuleDocumentSerializer());

  private record ObjectTypedModel(Object value) {}

  private record StringTypedModel(String value) {}

  private static Map<String, Object> exploitNode() {
    // Shaped like the issue's PoC payload: createLink against an arbitrary document reference.
    return Map.of(
        "camunda.function.type",
        "createLink",
        "params",
        List.of(Map.of("camunda.document.type", "camunda"), "PT1H"));
  }

  @Test
  void executeAlwaysThrows() {
    var executor = new DisabledIntrinsicFunctionExecutor();

    var exception =
        assertThrows(
            UnsupportedOperationException.class,
            () ->
                executor.execute("createLink", new IntrinsicFunctionParams.Positional(List.of())));

    assertThat(exception).hasMessageContaining("createLink");
  }

  @Test
  void refusesDispatchThroughAnObjectTypedProperty() {
    // This is the mapper shape connector properties typically bind through (e.g. an ioMapping
    // expression's result bound into a Map<String, Object>-like field) — the outbound property
    // mapper route both confirmed exploit routes went through. ObjectMapper#convertValue wraps
    // whatever the deserializer throws as an IllegalArgumentException; the message it carries
    // over is what proves dispatch — not evaluation — is what was refused.
    var payload = Map.of("value", exploitNode());

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> mapper.convertValue(payload, ObjectTypedModel.class));

    assertThat(exception).hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void refusesDispatchThroughAStringTypedProperty() {
    // The issue is explicit that a fix guarding only String is bypassed via an Object-typed
    // field; this proves the converse also holds — a String-typed property doesn't dispatch either.
    var payload = Map.of("value", exploitNode());

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> mapper.convertValue(payload, StringTypedModel.class));

    assertThat(exception).hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void ordinaryDocumentReferenceStillMaterializesWithDispatchDisabled() {
    // Disabling function dispatch must not disable plain document-reference resolution: the two
    // are handled by separate deserializer branches (isDocumentReference vs. isIntrinsicFunction).
    Document document =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8)).build());
    JsonNode reference = mapper.valueToTree(document);

    var payload = Map.of("value", reference);
    var result = mapper.convertValue(payload, ObjectTypedModel.class);

    assertThat(result.value()).isInstanceOf(Document.class);
    assertThat(((Document) result.value()).asByteArray())
        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void plainDataStillBindsWithDispatchDisabled() {
    var payload = Map.of("value", "just a string");

    var result = mapper.convertValue(payload, StringTypedModel.class);

    assertThat(result.value()).isEqualTo("just a string");
  }
}
