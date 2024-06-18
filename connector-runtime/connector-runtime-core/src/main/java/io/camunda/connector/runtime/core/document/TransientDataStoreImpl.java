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
package io.camunda.connector.runtime.core.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransientDataStoreImpl implements TransientDataStore {

  private final Map<String, byte[]> dataStore = new HashMap<>();

  @Override
  public byte[] get(String dataId) {
    return dataStore.get(dataId);
  }

  @Override
  public TransientDataTransaction createTransaction() {
    return new TransientDataTransactionImpl();
  }

  private class TransientDataTransactionImpl implements TransientDataTransaction {

    private final List<String> issuedDataIds = new ArrayList<>();
    private boolean isClosed = false;

    @Override
    public String put(byte[] data) {
      if (isClosed) {
        throw new IllegalStateException("Transaction is already closed");
      }
      String dataId = UUID.randomUUID().toString();
      issuedDataIds.add(dataId);
      dataStore.put(dataId, data);
      return dataId;
    }

    @Override
    public void close() {
      if (isClosed) {
        return;
      }
      isClosed = true;
      issuedDataIds.forEach(dataStore::remove);
    }
  }
}
