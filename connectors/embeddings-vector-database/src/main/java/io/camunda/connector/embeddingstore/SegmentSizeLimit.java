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
   * so a size failure never leaves part of a request stored. Other failures, such as an embedding
   * model error in a later batch, can still leave the earlier batches stored.
   */
  void checkSegments(List<TextSegment> segments);
}
