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
package io.camunda.connector.document.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import org.junit.jupiter.api.Test;

class InlineDocumentReferenceModelTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializeJsonObjectContent_storesAsRawJsonText() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": {"name": "Jane", "age": 28},
          "name": "me.json",
          "contentType": "application/json"
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    // The deserializer captured the JSON object as raw JSON text (no Map intermediate).
    assertThat(model.content()).isEqualTo("{\"name\":\"Jane\",\"age\":28}");
    assertThat(model.name()).isEqualTo("me.json");
    assertThat(model.contentType()).isEqualTo("application/json");
  }

  @Test
  void deserializeJsonStringContent_storesAsUnquotedText() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": "col1,col2\\nval1,val2",
          "name": "data.csv",
          "contentType": "text/csv"
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    // JSON string input -> stored as raw text (no surrounding quotes).
    assertThat(model.content()).isEqualTo("col1,col2\nval1,val2");
  }

  @Test
  void deserializeJsonArrayContent_storesAsRawJsonText() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": [1, 2, 3]
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    assertThat(model.content()).isEqualTo("[1,2,3]");
  }

  @Test
  void deserializeJsonNumberContent_storesAsRawText() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": 42
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    assertThat(model.content()).isEqualTo("42");
  }

  @Test
  void deserializeJsonBooleanContent_storesAsRawText() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": true
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    assertThat(model.content()).isEqualTo("true");
  }

  @Test
  void deserializeNullContent_storesNull() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": null
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    assertThat(model.content()).isNull();
  }

  @Test
  void deserializeMinimalInlineDocument_onlyContent() throws Exception {
    String json =
        """
        {
          "camunda.document.type": "inline",
          "content": "hello"
        }
        """;

    InlineDocumentReferenceModel model =
        (InlineDocumentReferenceModel) objectMapper.readValue(json, DocumentReferenceModel.class);

    assertThat(model.content()).isEqualTo("hello");
    assertThat(model.name()).isNull();
    assertThat(model.contentType()).isNull();
  }

  @Test
  void serializeInlineModel_emitsContentAsJsonString() throws Exception {
    // Even when the original input was a JSON object, serialize emits the captured
    // string content as a JSON string literal — sufficient for byte-equivalent round-trip.
    InlineDocumentReferenceModel model =
        new InlineDocumentReferenceModel("{\"x\":1}", "x.json", "application/json");

    String json = objectMapper.writeValueAsString(model);

    assertThat(json)
        .contains("\"camunda.document.type\":\"inline\"")
        .contains("\"content\":\"{\\\"x\\\":1}\"")
        .contains("\"name\":\"x.json\"")
        .contains("\"contentType\":\"application/json\"");
  }

  @Test
  void serializeInlineModel_withNullOptionalFields_omitsThem() throws Exception {
    InlineDocumentReferenceModel model = new InlineDocumentReferenceModel("hello", null, null);

    String json = objectMapper.writeValueAsString(model);

    assertThat(json).doesNotContain("\"name\"").doesNotContain("\"contentType\"");
    assertThat(json).contains("\"content\":\"hello\"");
  }
}
