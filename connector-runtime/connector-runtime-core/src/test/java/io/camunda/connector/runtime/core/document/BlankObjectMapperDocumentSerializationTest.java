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
package io.camunda.connector.runtime.core.document;

import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Regression test for GitHub issue #6946.
 *
 * <p>When a FEEL expression evaluates against a context that contains a {@link CamundaDocument},
 * the FEEL deserializer ends up calling {@code AbstractFeelDeserializer.BLANK_OBJECT_MAPPER
 * .valueToTree(contextMap)}. The bug is that on 8.6/8.7/8.8 {@code BLANK_OBJECT_MAPPER} is a vanilla
 * {@code new ObjectMapper()} — {@code FAIL_ON_EMPTY_BEANS=true} and no document serializer
 * registered — so {@code valueToTree} throws {@link
 * com.fasterxml.jackson.databind.exc.InvalidDefinitionException} for any {@link CamundaDocument}.
 *
 * <p>This test reproduces the user-facing failure path through the public API
 * ({@link FeelContextAwareObjectReader}) and asserts that no exception is thrown.
 *
 * <p><b>Expected behaviour:</b>
 *
 * <ul>
 *   <li>On stable/8.8 (and 8.6, 8.7) the test <b>fails</b> — {@code BLANK_OBJECT_MAPPER = new
 *       ObjectMapper()} surfaces the bug as an exception.
 *   <li>On main the test <b>passes</b> — {@code BLANK_OBJECT_MAPPER =
 *       ConnectorsObjectMapperSupplier.getCopy()} has {@code FAIL_ON_EMPTY_BEANS=false}, so the
 *       call no longer throws (though the document is silently emptied — see #6946 follow-up).
 * </ul>
 *
 * <p>A complete fix would also register {@code JacksonModuleDocumentSerializer} on the blank mapper
 * so the document reference is preserved rather than serialized as {@code {}}.
 */
class BlankObjectMapperDocumentSerializationTest {

  private record TargetType(Supplier<String> value) {}

  @Test
  void feelEvaluationWithDocumentInContextDoesNotThrow() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonModuleFeelFunction());

    var metadata = new CamundaDocumentMetadataModel(null, null, null, null, null, null, null);
    var reference = new CamundaDocumentReferenceModel("store-1", "doc-1", "hash-1", metadata);
    // Real CamundaDocument, not a Mockito mock — Mockito's proxy exposes internal getters that
    // make the bean non-empty and accidentally bypass the FAIL_ON_EMPTY_BEANS path we want to
    // exercise. The store is unused here because no content is read.
    Map<String, Object> feelContext =
        Map.of("doc", new CamundaDocument(metadata, reference, null));

    // The FEEL expression itself is trivial — the failure is triggered by serializing the
    // context map (which contains a CamundaDocument) into a JsonNode for the FEEL engine,
    // not by evaluating the expression.
    String json = "{ \"value\": \"= \\\"hello\\\"\" }";

    assertThatNoException()
        .as(
            "Issue #6946: FEEL deserialization must not fail when the context contains a "
                + "CamundaDocument. This test fails on stable/8.8 and below, and passes on main.")
        .isThrownBy(
            () ->
                FeelContextAwareObjectReader.of(mapper)
                    .withStaticContext(feelContext)
                    .readValue(json, TargetType.class));
  }
}
