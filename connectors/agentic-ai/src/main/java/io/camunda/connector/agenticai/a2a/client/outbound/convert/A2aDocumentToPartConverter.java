/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.convert;

import io.a2a.spec.Part;
import io.camunda.connector.api.document.Document;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public interface A2aDocumentToPartConverter {
  Part<?> convert(Document camundaDocument);

  default List<? extends Part<?>> convert(List<Document> camundaDocuments) {
    if (CollectionUtils.isEmpty(camundaDocuments)) {
      return List.of();
    }
    return camundaDocuments.stream().map(this::convert).toList();
  }
}
