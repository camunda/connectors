/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.mapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.api.services.drive.model.File;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Test;

class DocumentMapperTest {

  @Test
  void mapToDocument() {
    OutboundConnectorContext context = OutboundConnectorContextBuilder.create().build();
    DocumentMapper mapper = new DocumentMapper(context);

    File file = new File();
    file.setMimeType("text/plain");
    file.setName("book.txt");
    var document = mapper.mapToDocument(new byte[1], file);

    assertThat(document.metadata().getFileName()).isEqualTo("book.txt");
    assertThat(document.metadata().getContentType()).isEqualTo("text/plain");
  }
}
