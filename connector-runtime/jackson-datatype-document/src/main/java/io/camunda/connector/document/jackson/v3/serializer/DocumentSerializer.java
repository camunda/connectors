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
package io.camunda.connector.document.jackson.v3.serializer;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 counterpart of {@link
 * io.camunda.connector.document.jackson.serializer.DocumentSerializer}. That legacy variant is kept
 * on Jackson 2 because it is registered onto connector-feel's LocalFeelExpressionEvaluator
 * ObjectMapper, which cannot move to Jackson 3 until jackson-module-scala ships a Jackson 3
 * release. This class is used by every other, Jackson 3-based ObjectMapper.
 */
public class DocumentSerializer extends ValueSerializer<Document> {

  public DocumentSerializer() {}

  @Override
  public void serialize(Document document, JsonGenerator jsonGenerator, SerializationContext ctxt) {
    var reference = document.reference();
    if (reference
        instanceof DocumentReference.ExternalDocumentReference externalDocumentReference) {
      final var model =
          new DocumentReferenceModel.ExternalDocumentReferenceModel(
              externalDocumentReference.url(), externalDocumentReference.name());
      jsonGenerator.writePOJO(model);
    } else if (reference instanceof CamundaDocumentReference camundaReference) {
      final CamundaDocumentReferenceModel model;
      if (camundaReference instanceof CamundaDocumentReferenceModel camundaModel) {
        model = camundaModel;
      } else {
        model =
            new CamundaDocumentReferenceModel(
                camundaReference.getStoreId(),
                camundaReference.getDocumentId(),
                camundaReference.getContentHash(),
                new CamundaDocumentMetadataModel(camundaReference.getMetadata()));
      }
      jsonGenerator.writePOJO(model);
    } else if (reference instanceof InlineDocumentReference inlineReference) {
      jsonGenerator.writePOJO(
          new InlineDocumentReferenceModel(
              inlineReference.content(), inlineReference.name(), inlineReference.contentType()));
    } else {
      throw new IllegalArgumentException("Unsupported document reference type: " + reference);
    }
  }
}
