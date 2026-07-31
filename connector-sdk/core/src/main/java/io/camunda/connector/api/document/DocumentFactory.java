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

public interface DocumentFactory {

  /**
   * Jackson {@code ObjectReader}/{@code DeserializationContext} attribute key used to carry the
   * physical tenant (Zeebe cluster/"engine") a {@code Document}-typed field should be resolved
   * against. Set via {@code objectMapper.reader().withAttribute(PHYSICAL_TENANT_ID_ATTRIBUTE, ...)}
   * before deserializing, read back via {@code context.getAttribute(PHYSICAL_TENANT_ID_ATTRIBUTE)}
   * inside the deserializer — necessary because the {@code ObjectMapper}/module holding the
   * document deserializer is a long-lived singleton, not rebuilt per physical tenant.
   */
  String PHYSICAL_TENANT_ID_ATTRIBUTE = "physicalTenantId";

  /** Given a document reference, create the Document object */
  Document resolve(DocumentReference reference);

  /**
   * Upload a document to the underlying document store and parse the document reference into a
   * Document object
   */
  Document create(DocumentCreationRequest request);
}
