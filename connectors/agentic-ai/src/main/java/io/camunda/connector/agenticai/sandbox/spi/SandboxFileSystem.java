/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

import java.util.List;

public interface SandboxFileSystem {

  byte[] read(String path);

  FileInfo stat(String path);

  void write(String path, byte[] content);

  void writeBatch(List<FileEntry> entries);

  List<FileInfo> list(String path);

  void delete(String path, boolean recursive);

  /**
   * Search for lines matching {@code pattern} under {@code dir}. Optional — not all providers
   * support it.
   */
  default List<Match> search(String dir, String pattern) {
    throw new UnsupportedOperationException("search not supported by this provider");
  }
}
