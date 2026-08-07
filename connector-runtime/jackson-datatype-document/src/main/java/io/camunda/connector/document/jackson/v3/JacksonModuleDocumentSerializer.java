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
package io.camunda.connector.document.jackson.v3;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.document.jackson.v3.serializer.DocumentSerializer;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 counterpart of {@link
 * io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer}. See that class' javadoc
 * for why a separate Jackson 2 variant still exists.
 */
public class JacksonModuleDocumentSerializer extends SimpleModule {

  public JacksonModuleDocumentSerializer() {}

  @Override
  public String getModuleName() {
    return "JacksonModuleDocumentSerializer";
  }

  @Override
  public Version version() {
    // TODO: get version from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-document");
  }

  @Override
  public void setupModule(SetupContext context) {
    addSerializer(Document.class, new DocumentSerializer());
    super.setupModule(context);
  }
}
