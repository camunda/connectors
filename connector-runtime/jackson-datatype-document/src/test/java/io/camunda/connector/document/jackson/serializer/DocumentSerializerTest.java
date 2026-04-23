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
package io.camunda.connector.document.jackson.serializer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class DocumentSerializerTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());

  @Test
  void shouldReturnJsonForExternalDocument() throws JsonProcessingException, JSONException {
    var ref = new ExternalDocumentReferenceModel("https://example.com/file.pdf", "file.pdf");
    var document = mock(Document.class);
    when(document.reference()).thenReturn(ref);

    var result = objectMapper.writeValueAsString(document);

    JSONAssert.assertEquals(
        """
        {
          "camunda.document.type": "external",
          "url": "https://example.com/file.pdf",
          "name": "file.pdf"
        }
        """,
        result,
        true);
  }

  @Test
  void shouldReturnJsonForCamundaDocument() throws JsonProcessingException, JSONException {
    var metadata = new CamundaDocumentMetadataModel(null, null, null, null, null, null, null);
    var ref = new CamundaDocumentReferenceModel("store-1", "doc-42", "abc123", metadata);
    var document = mock(Document.class);
    when(document.reference()).thenReturn(ref);

    var result = objectMapper.writeValueAsString(document);

    JSONAssert.assertEquals(
        """
        {
          "camunda.document.type": "camunda",
          "storeId": "store-1",
          "documentId": "doc-42",
          "contentHash": "abc123",
          "metadata": {}
        }
        """,
        result,
        true);
  }

  @Test
  void shouldThrowExceptionForUnsupportedDocument() {
    var unsupportedRef = mock(DocumentReference.class);
    var document = mock(Document.class);
    when(document.reference()).thenReturn(unsupportedRef);

    assertThatThrownBy(() -> objectMapper.writeValueAsString(document))
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .cause()
        .hasMessageContaining("Unsupported document reference type");
  }
}
