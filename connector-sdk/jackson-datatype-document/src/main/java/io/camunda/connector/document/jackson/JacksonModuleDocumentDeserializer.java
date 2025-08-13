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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.deserializer.ByteArrayDeserializer;
import io.camunda.connector.document.jackson.deserializer.DocumentDeserializer;
import io.camunda.connector.document.jackson.deserializer.InputStreamDeserializer;
import io.camunda.connector.document.jackson.deserializer.ObjectDeserializer;
import io.camunda.connector.document.jackson.deserializer.StringDeserializer;
import io.camunda.intrinsic.IntrinsicFunctionExecutor;
import java.io.InputStream;

public class JacksonModuleDocumentDeserializer extends SimpleModule {

  private final DocumentFactory documentFactory;
  private final IntrinsicFunctionExecutor intrinsicFunctionExecutor;
  private final DocumentModuleSettings settings;

  public JacksonModuleDocumentDeserializer(
      DocumentFactory documentFactory,
      IntrinsicFunctionExecutor intrinsicFunctionExecutor,
      DocumentModuleSettings settings) {
    this.documentFactory = documentFactory;
    this.intrinsicFunctionExecutor = intrinsicFunctionExecutor;
    this.settings = settings;
  }

  public JacksonModuleDocumentDeserializer(
      DocumentFactory documentFactory, IntrinsicFunctionExecutor intrinsicFunctionExecutor) {
    this(documentFactory, intrinsicFunctionExecutor, DocumentModuleSettings.create());
  }

  @Override
  public String getModuleName() {
    return "JacksonModuleDocumentDeserializer";
  }

  @Override
  public Version version() {
    // TODO: get version from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-document");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(
        Document.class,
        new DocumentDeserializer(documentFactory, intrinsicFunctionExecutor, settings));
    addDeserializer(
        byte[].class,
        new ByteArrayDeserializer(documentFactory, intrinsicFunctionExecutor, settings));
    addDeserializer(
        InputStream.class,
        new InputStreamDeserializer(documentFactory, intrinsicFunctionExecutor, settings));
    if (settings.isObjectEnabled()) {
      addDeserializer(
          Object.class,
          new ObjectDeserializer(documentFactory, intrinsicFunctionExecutor, settings));
    }
    if (settings.isStringEnabled()) {
      addDeserializer(
          String.class,
          new StringDeserializer(documentFactory, intrinsicFunctionExecutor, settings));
    }
    super.setupModule(context);
  }

  public static class DocumentModuleSettings {

    private boolean enableObject = true;
    private boolean enableString = true;
    private int maxIntrinsicFunctions = 10; // per deserialization run, including nested

    private DocumentModuleSettings() {}

    public static DocumentModuleSettings create() {
      return new DocumentModuleSettings();
    }

    /** Enable deserialization of document references into objects. */
    public void enableObject(boolean enable) {
      this.enableObject = enable;
    }

    /** Enable deserialization of document references into strings. */
    public void enableString(boolean enable) {
      this.enableString = enable;
    }

    /** Set the maximum number of intrinsic functions per object. */
    public void setMaxIntrinsicFunctions(int maxIntrinsicFunctions) {
      this.maxIntrinsicFunctions = maxIntrinsicFunctions;
    }

    public boolean isObjectEnabled() {
      return enableObject;
    }

    public boolean isStringEnabled() {
      return enableString;
    }

    public int getMaxIntrinsicFunctions() {
      return maxIntrinsicFunctions;
    }
  }
}
