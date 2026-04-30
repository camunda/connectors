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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Verifies the JSON → factory → {@code Document.reference()} → JSON path for inline documents,
 * including the intentional asymmetry where a missing {@code name} on input becomes a generated
 * UUID on output (because the factory mints a UUID at construction time).
 */
class InlineDocumentRoundtripTest {

  private final ObjectMapper modelMapper = new ObjectMapper();
  private final ObjectMapper serializingMapper =
      new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());
  private final DocumentFactoryImpl factory =
      new DocumentFactoryImpl(mock(CamundaDocumentStore.class));

  @Test
  void roundtrip_withAllFields_isJsonEqual() throws JsonProcessingException, JSONException {
    String input =
        """
        {
          "camunda.document.type": "inline",
          "content": "hello",
          "name": "greet.txt",
          "contentType": "text/plain"
        }
        """;

    String output = roundtrip(input);

    JSONAssert.assertEquals(input, output, true);
  }

  @Test
  void roundtrip_jsonObjectContent_isPreservedAsRawJsonString()
      throws JsonProcessingException, JSONException {
    // Input has `content` as a JSON object; the deserializer captures it as raw JSON text.
    // On output, that captured string is emitted as a JSON string literal — byte-equivalent
    // round-trip but not shape-preserving.
    String input =
        """
        {
          "camunda.document.type": "inline",
          "content": {"name":"Jane","age":28},
          "name": "me.json",
          "contentType": "application/json"
        }
        """;
    String expectedOutput =
        """
        {
          "camunda.document.type": "inline",
          "content": "{\\"name\\":\\"Jane\\",\\"age\\":28}",
          "name": "me.json",
          "contentType": "application/json"
        }
        """;

    String output = roundtrip(input);

    JSONAssert.assertEquals(expectedOutput, output, true);
  }

  @Test
  void roundtrip_withoutName_emitsGeneratedUuidName()
      throws JsonProcessingException, JSONException {
    // Documents the intentional asymmetry: when no name is provided in the input JSON, the
    // factory generates a UUID at construction. That UUID surfaces through reference().name()
    // and is therefore present in the serialized output.
    String input =
        """
        {
          "camunda.document.type": "inline",
          "content": "hello"
        }
        """;

    String output = roundtrip(input);

    DocumentReferenceModel parsedOutput =
        modelMapper.readValue(output, DocumentReferenceModel.class);
    var inline = (InlineDocumentReferenceModel) parsedOutput;
    assertThat(inline.content()).isEqualTo("hello");
    assertThat(inline.contentType()).isNull();
    assertThat(inline.name()).isNotBlank();
    // Generated UUIDs are 36 chars (8-4-4-4-12 hex with dashes).
    assertThat(inline.name()).hasSize(36).contains("-");
  }

  private String roundtrip(String input) throws JsonProcessingException {
    DocumentReferenceModel model = modelMapper.readValue(input, DocumentReferenceModel.class);
    Document doc = factory.resolve(model);
    return serializingMapper.writeValueAsString(doc);
  }
}
