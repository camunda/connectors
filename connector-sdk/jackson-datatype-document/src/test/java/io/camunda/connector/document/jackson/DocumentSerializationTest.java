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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.operation.DocumentOperationExecutor;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.annotation.jackson.JacksonModuleDocument;
import java.util.Map;
import java.util.Optional;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.skyscreamer.jsonassert.JSONAssert;

public class DocumentSerializationTest {

  @Mock private DocumentFactory factory;
  @Mock private DocumentOperationExecutor operationExecutor;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleDocument(factory, operationExecutor))
          .registerModule(new Jdk8Module());

  record SourceTypeDocument(Document document) {}

  @Test
  void sourceTypeDocument() throws JsonProcessingException, JSONException {
    var ref = new CamundaDocumentReferenceModel("test", "test", Map.of(), Optional.empty());
    var document = mock(Document.class);
    when(document.reference()).thenReturn(ref);
    var source = new SourceTypeDocument(document);
    var result = objectMapper.writeValueAsString(source);
    var expectedResult =
        """
        {
          "document": {
            "$documentType": "camunda",
            "storeId": "test",
            "documentId": "test",
            "metadata": {}
          }
        }
        """;
    JSONAssert.assertEquals(expectedResult, result, true);
  }
}
