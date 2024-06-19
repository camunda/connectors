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
package io.camunda.connector.api.document;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface DocumentContent {

  /**
   * Transient document content is only available during the execution of the connector and is not
   * persisted. Before returning the result of the connector execution, the transient document must
   * be converted to a static document.
   *
   * <p>Transient documents are useful for passing data between connectors in a workflow, or for
   * using the data in the FEEL engine. Such operations involve type conversions and data
   * transformations that would cause a performance and memory overhead if the data were passed in a
   * static form.
   */
  @JsonTypeName("TRANSIENT")
  record TransientDocumentContent(String transientDataId) implements DocumentContent {}

  /**
   * Static document content is passed "as is". If this content is returned to the process engine,
   * it will be written into the process variable as a base64-encoded string. In this case, the
   * content size must not exceed the maximum allowed size for a process variable.
   */
  @JsonTypeName("STATIC")
  record StaticDocumentContent(byte[] data) implements DocumentContent {}

  /**
   * Reference document content was stored externally using a connector. The reference can be
   * resolved into static content by invoking that connector.
   */
  @JsonTypeName("REFERENCE")
  record ReferenceDocumentContent(DocumentReference reference) implements DocumentContent {}
}
