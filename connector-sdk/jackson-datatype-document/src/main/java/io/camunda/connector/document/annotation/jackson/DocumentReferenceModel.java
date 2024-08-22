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
package io.camunda.connector.document.annotation.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.document.DocumentMetadata;
import io.camunda.document.operation.DocumentOperation;
import io.camunda.document.reference.DocumentReference;
import java.util.Map;
import java.util.Optional;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = DocumentReferenceModel.DISCRIMINATOR_KEY,
    visible = true,
    include = As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CamundaDocumentReferenceModel.class, name = "camunda"),
  @JsonSubTypes.Type(value = ExternalDocumentReferenceModel.class, name = "external")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface DocumentReferenceModel extends DocumentReference {

  String DISCRIMINATOR_KEY = "documentType";

  /**
   * Document references may have operations associated with them. Operation indicates that the
   * document should not be used as is, but should be transformed or processed in some way. This
   * processing must take place in the context of the connector.
   */
  @JsonInclude(Include.NON_EMPTY)
  Optional<DocumentOperation> operation();

  record CamundaDocumentReferenceModel(
      String storeId,
      String documentId,
      @JsonProperty("metadata") Map<String, Object> rawMetadata,
      Optional<DocumentOperation> operation)
      implements DocumentReferenceModel, CamundaDocumentReference {

    @JsonProperty(DISCRIMINATOR_KEY)
    private String documentType() {
      return "camunda";
    }

    @Override
    @JsonIgnore
    public DocumentMetadata metadata() {
      return new DocumentMetadata(rawMetadata);
    }
  }

  record ExternalDocumentReferenceModel(String url, Optional<DocumentOperation> operation)
      implements DocumentReferenceModel, ExternalDocumentReference {

    @JsonProperty(DISCRIMINATOR_KEY)
    private String documentType() {
      return "external";
    }
  }
}
