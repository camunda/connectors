/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import io.a2a.spec.Part;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aContent;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public interface A2aPartToContentConverter {
  A2aContent convert(Part<?> part);

  default List<A2aContent> convert(List<Part<?>> parts) {
    if (CollectionUtils.isEmpty(parts)) {
      return List.of();
    }
    return parts.stream().map(this::convert).toList();
  }
}
