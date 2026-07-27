/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/** Connector-owned mirror of the AWS SDK v2 {@code Warning} shape. */
@JsonPropertyOrder({"errorCode", "pages"})
public record Warning(String errorCode, List<Integer> pages) {

  public static Warning from(final software.amazon.awssdk.services.textract.model.Warning warning) {
    if (warning == null) {
      return null;
    }
    return new Warning(warning.errorCode(), warning.hasPages() ? warning.pages() : null);
  }
}
