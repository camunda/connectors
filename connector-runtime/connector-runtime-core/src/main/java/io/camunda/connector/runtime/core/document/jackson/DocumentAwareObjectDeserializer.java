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
package io.camunda.connector.runtime.core.document.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentSource.ReferenceDocumentSource;
import io.camunda.connector.runtime.core.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentOperationExecutor;
import java.io.IOException;
import java.io.InputStream;

public class DocumentAwareObjectDeserializer extends StdDeserializer<Object>
    implements ContextualDeserializer {

  public DocumentAwareObjectDeserializer(
      boolean isLazy, DocumentFactory factory, DocumentOperationExecutor operationExecutor) {
    this(isLazy, factory, operationExecutor, TypeFactory.unknownType());
  }

  protected DocumentAwareObjectDeserializer(
      boolean isLazy,
      DocumentFactory factory,
      DocumentOperationExecutor executor,
      JavaType valueType) {
    super(valueType);
    this.isLazy = isLazy;
    this.documentFactory = factory;
    this.operationExecutor = executor;
    this.documentDeserializer = new SimpleDocumentDeserializer(factory);
  }

  private final boolean isLazy;
  private final DocumentFactory documentFactory;
  private final DocumentOperationExecutor operationExecutor;

  private final SimpleDocumentDeserializer documentDeserializer;

  @Override
  public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {

    if (_valueType.isTypeOrSubTypeOf(Document.class)) {
      return documentDeserializer.deserialize(jsonParser, deserializationContext);
    }
    var reference =
        deserializationContext.readTreeAsValue(
            jsonParser.readValueAsTree(), DocumentReference.class);

    if (reference.operation().isPresent()) {
      var operation = reference.operation().get();
      if (isLazy && _valueType.isTypeOrSuperTypeOf(Object.class)) {
        // type does not matter, operation will be evaluated later during serialization
        return reference;
      }
      // evaluate operation eagerly
      var document = documentFactory.from(new ReferenceDocumentSource(reference)).build();
      return operationExecutor.execute(operation, document);
    }
    // no operation, deserialize as document
    return deserializeDocumentCompatibleType(reference);
  }

  private Object deserializeDocumentCompatibleType(DocumentReference reference) {

    var documentSource = new ReferenceDocumentSource(reference);
    var document = documentFactory.from(documentSource).build();

    if (_valueType.isArrayType() && _valueType.getContentType().isTypeOrSubTypeOf(byte.class)) {
      return document.asByteArray();
    }

    if (_valueType.isTypeOrSubTypeOf(String.class)) {
      return document.asBase64();
    }

    if (_valueType.isTypeOrSubTypeOf(InputStream.class)) {
      return document.asInputStream();
    }

    throw new IllegalArgumentException("Type " + _valueType + " is not compatible with Document");
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new DocumentAwareObjectDeserializer(
        this.isLazy, this.documentFactory, this.operationExecutor, ctxt.getContextualType());
  }
}
