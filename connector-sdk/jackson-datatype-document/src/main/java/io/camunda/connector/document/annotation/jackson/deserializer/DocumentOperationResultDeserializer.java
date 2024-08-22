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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.camunda.connector.document.annotation.jackson.DocumentOperationResult;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import io.camunda.document.DocumentFactory;
import io.camunda.document.operation.DocumentOperationExecutor;

public class DocumentOperationResultDeserializer
    extends DocumentDeserializerBase<DocumentOperationResult<?>> implements ContextualDeserializer {

  private final JavaType valueType;

  public DocumentOperationResultDeserializer(
      DocumentOperationExecutor operationExecutor, DocumentFactory documentFactory) {
    super(operationExecutor, documentFactory);
    this.valueType = null;
  }

  public DocumentOperationResultDeserializer(
      DocumentOperationExecutor operationExecutor,
      DocumentFactory documentFactory,
      JavaType valueType) {
    super(operationExecutor, documentFactory);
    this.valueType = valueType;
  }

  @Override
  public DocumentOperationResult<?> deserializeDocumentReference(
      DocumentReferenceModel reference, DeserializationContext ctx) {
    var operation =
        reference
            .operation()
            .orElseThrow(
                () -> new IllegalArgumentException("Document reference must contain an operation"));
    var resultSupplier = deserializeOperation(reference, operation);

    return () -> {
      var result = resultSupplier.get();
      if (valueType == null) {
        return result;
      }
      if (valueType.getContentType().isTypeOrSubTypeOf(result.getClass())) {
        return result;
      } else {
        throw new IllegalArgumentException(
            "Unexpected operation result type: "
                + result.getClass()
                + " while executing operation "
                + operation.name()
                + ". Expected "
                + valueType.getContentType()
                + ", but got "
                + result.getClass());
      }
    };
  }

  @Override
  public DocumentOperationResult<?> fallback(JsonNode node, DeserializationContext ctx) {
    throw new IllegalArgumentException(
        "Unsupported document format. Expected a document reference, got: " + node);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    var valueType = property.getType();
    return new DocumentOperationResultDeserializer(operationExecutor, documentFactory, valueType);
  }
}
