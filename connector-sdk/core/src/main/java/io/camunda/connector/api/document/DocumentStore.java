/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.api.document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public interface DocumentStore {

  byte[] load(String documentReference);

  String store(byte[] content);

  class InMemoryDocumentStore implements DocumentStore {
    private final Map<String, byte[]> store;

    public InMemoryDocumentStore() {
      this.store = new HashMap<>();
      store.put("example", "example".getBytes());
    }

    @Override
    public byte[] load(String documentReference) {
      return store.get(documentReference);
    }

    @Override
    public String store(byte[] content) {
      var id = UUID.randomUUID().toString();
      store.put(id, content);
      return id;
    }
  }
}
