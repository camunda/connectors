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
package io.camunda.connector.runtime.core.inbound;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentSource;
import io.camunda.connector.api.inbound.DocumentTransaction;
import io.camunda.connector.runtime.core.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.TransientDataStore;
import io.camunda.connector.runtime.core.document.TransientDataTransaction;

public class DocumentTransactionImpl implements DocumentTransaction {

  private final DocumentFactory documentFactory;
  private final TransientDataTransaction dataTransaction;

  public DocumentTransactionImpl(TransientDataStore transientDataStore) {
    this.dataTransaction = transientDataStore.createTransaction();
    this.documentFactory = new DocumentFactory(dataTransaction);
  }

  @Override
  public Document createDocument(DocumentSource source) {
    return documentFactory.createDocument(source);
  }

  @Override
  public void close() throws Exception {
    dataTransaction.close();
  }
}
