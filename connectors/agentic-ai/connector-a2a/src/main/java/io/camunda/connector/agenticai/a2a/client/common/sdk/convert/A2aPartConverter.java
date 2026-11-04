/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.convert;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aPart;
import java.util.List;
import org.a2aproject.sdk.spec.Part;
import org.apache.commons.collections4.CollectionUtils;

public interface A2aPartConverter {
  A2aPart convert(Part<?> part);

  default List<A2aPart> convert(List<Part<?>> parts) {
    if (CollectionUtils.isEmpty(parts)) {
      return List.of();
    }
    return parts.stream().map(this::convert).toList();
  }
}
