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
package io.camunda.connector.document.annotation.jackson.deserializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.operation.DocumentOperationExecutor;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import java.io.InputStream;

public class InputStreamDocumentDeserializer extends DocumentDeserializerBase<InputStream> {

  public InputStreamDocumentDeserializer(
      DocumentOperationExecutor operationExecutor, DocumentFactory documentFactory) {
    super(operationExecutor, documentFactory);
  }

  @Override
  public InputStream deserializeDocumentReference(
      DocumentReferenceModel reference, DeserializationContext ctx) {
    ensureNoOperation(reference);
    var document = createDocument(reference);
    return document.asInputStream();
  }

  @Override
  public InputStream fallback(JsonNode node, DeserializationContext ctx) {
    throw new IllegalArgumentException(
        "unsupported document format. Expected a document reference, got: " + node);
  }
}
