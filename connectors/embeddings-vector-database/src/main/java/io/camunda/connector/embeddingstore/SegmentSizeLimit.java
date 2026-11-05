/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.data.segment.TextSegment;
import java.util.List;

/** A store that can only hold segments up to a certain size. */
public interface SegmentSizeLimit {

  /**
   * Fails if any segment is too large for the store. Called before anything is embedded or written,
   * so a document is either stored completely or not at all.
   */
  void checkSegments(List<TextSegment> segments);
}
