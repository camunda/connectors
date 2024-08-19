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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.operation.DocumentOperationExecutor;
import io.camunda.connector.document.annotation.jackson.deserializer.ByteArrayDocumentDeserializer;
import io.camunda.connector.document.annotation.jackson.deserializer.DocumentDeserializer;
import io.camunda.connector.document.annotation.jackson.deserializer.DocumentOperationResultDeserializer;
import io.camunda.connector.document.annotation.jackson.deserializer.InputStreamDocumentDeserializer;
import io.camunda.connector.document.annotation.jackson.deserializer.ObjectDocumentDeserializer;
import io.camunda.connector.document.annotation.jackson.deserializer.StringDocumentDeserializer;
import io.camunda.connector.document.annotation.jackson.serializer.DocumentSerializer;
import java.io.InputStream;

public class JacksonModuleDocument extends SimpleModule {

  public static class DocumentModuleSettings {

    private boolean lazy = true;
    private boolean enableObject = true;
    private boolean enableString = true;

    private DocumentModuleSettings() {}

    /**
     * Enable lazy operations for document deserialization.
     *
     * <p>When enabled, given that the connector consumes a document as a generic {@link Object}
     * type, and an operation is present in the document reference, the operation is not executed in
     * the deserialization phase. Instead, the operation is executed during serialization using the
     * {@link DocumentSerializer}.
     *
     * <p>Disable lazy operations if your connector doesn't use the document module for
     * serialization (or doesn't use Jackson at all).
     *
     * <p>This takes no effect if {@link #enableObject(boolean)} is disabled.
     */
    public void lazyOperations(boolean lazy) {
      this.lazy = lazy;
    }

    /** Enable deserialization of document references into objects. */
    public void enableObject(boolean enable) {
      this.enableObject = enable;
    }

    /** Enable deserialization of document references into strings. */
    public void enableString(boolean enable) {
      this.enableString = enable;
    }

    public static DocumentModuleSettings create() {
      return new DocumentModuleSettings();
    }
  }

  private final DocumentFactory documentFactory;
  private final DocumentOperationExecutor operationExecutor;
  private final DocumentModuleSettings settings;

  public JacksonModuleDocument(
      DocumentFactory documentFactory,
      DocumentOperationExecutor operationExecutor,
      DocumentModuleSettings settings) {
    this.documentFactory = documentFactory;
    this.operationExecutor = operationExecutor;
    this.settings = settings;
  }

  public JacksonModuleDocument(
      DocumentFactory documentFactory, DocumentOperationExecutor operationExecutor) {
    this(documentFactory, operationExecutor, DocumentModuleSettings.create());
  }

  @Override
  public String getModuleName() {
    return "JacksonModuleDocument";
  }

  @Override
  public Version version() {
    // TODO: get version from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-document");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(Document.class, new DocumentDeserializer(operationExecutor, documentFactory));
    addDeserializer(
        DocumentOperationResult.class,
        new DocumentOperationResultDeserializer(operationExecutor, documentFactory));
    addDeserializer(
        byte[].class, new ByteArrayDocumentDeserializer(operationExecutor, documentFactory));
    addDeserializer(
        InputStream.class, new InputStreamDocumentDeserializer(operationExecutor, documentFactory));
    if (settings.enableObject) {
      addDeserializer(
          Object.class,
          new ObjectDocumentDeserializer(operationExecutor, documentFactory, settings.lazy));
    }
    if (settings.enableString) {
      addDeserializer(
          String.class, new StringDocumentDeserializer(operationExecutor, documentFactory));
    }
    addSerializer(Document.class, new DocumentSerializer(operationExecutor));
    super.setupModule(context);
  }
}
