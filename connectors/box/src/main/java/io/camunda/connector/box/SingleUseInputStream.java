/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SingleUseInputStream extends FilterInputStream {

  private boolean consumed;

  public SingleUseInputStream(InputStream in) {
    super(in);
  }

  @Override
  public int read() throws IOException {
    ensureNotConsumed();
    int b = super.read();
    if (b == -1) {
      consumed = true;
    }
    return b;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    ensureNotConsumed();
    int n = super.read(b, off, len);
    if (n == -1) {
      consumed = true;
    }
    return n;
  }

  @Override
  public void close() throws IOException {
    consumed = true;
    super.close();
  }

  private void ensureNotConsumed() throws IOException {
    if (consumed) {
      throw new IOException(
          "The document stream was already consumed. The upload request was resent by the HTTP"
              + " client, which cannot be replayed; the job retry will upload the document again.");
    }
  }
}
