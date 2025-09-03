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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.io.IOException;

public class DocumentSerializer extends JsonSerializer<Document> {

  public DocumentSerializer() {}

  @Override
  public void serialize(
      Document document, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    var reference = document.reference();
    if (reference instanceof CamundaDocumentReference camundaReference) {
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
      jsonGenerator.writeObject(model);
    } else if (reference instanceof ExternalDocumentReference externalReference) {
      final ExternalDocumentReferenceModel model;
      if (externalReference instanceof ExternalDocumentReferenceModel externalModel) {
        model = externalModel;
      } else {
        model =
            new ExternalDocumentReferenceModel(externalReference.url(), externalReference.name());
      }
      jsonGenerator.writeObject(model);
    } else {
      throw new IllegalArgumentException("Unsupported document reference type: " + reference);
    }
  }
}
