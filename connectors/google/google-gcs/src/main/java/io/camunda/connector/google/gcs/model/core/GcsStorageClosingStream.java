/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.core;

import com.google.cloud.storage.Storage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming wrapper that closes the underlying GCS {@link Storage} client after the consumer
 * finishes reading. Lets the runtime stream the blob directly into the document store without
 * buffering, while ensuring the storage client is released even on the streaming code path.
 */
final class GcsStorageClosingStream extends FilterInputStream {

  private final Storage storage;

  GcsStorageClosingStream(InputStream in, Storage storage) {
    super(in);
    this.storage = storage;
  }

  @Override
  public void close() throws IOException {
    try {
      super.close();
    } finally {
      try {
        storage.close();
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException("Failed to close GCS storage client", e);
      }
    }
  }
}
