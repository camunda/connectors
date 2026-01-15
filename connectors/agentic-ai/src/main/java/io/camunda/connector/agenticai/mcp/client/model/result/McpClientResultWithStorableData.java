/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.api.document.DocumentFactory;

public interface McpClientResultWithStorableData {

  /**
   * Stores data that is marked as storable, e.g. binary data, as Camunda documents using the
   * provided <tt>documentFactory</tt> and returns a new instance of the result holding references
   * to the created documents.
   *
   * @param documentFactory responsible for creating the actual documents in Camunda.
   * @return a new instance of the result holding references to the created documents, if eligible
   *     data is present, otherwise itself.
   */
  McpClientResult convertStorableMcpResultData(DocumentFactory documentFactory);

  /**
   * Interface for messages that hold binary content that can be replaced with a Camunda document
   * reference.
   */
  interface StorableMcpDataContainer<T> {

    /**
     * If conditions are met, creates a Camunda document using the provided <tt>documentFactory</tt>
     * and returns a new value that holds a reference to the created document.
     *
     * @param documentFactory the document factory that is responsible to create the document in
     *     Camunda.
     * @return a new value holding a reference to the created document.
     */
    T replaceWithDocumentReference(DocumentFactory documentFactory);
  }
}
