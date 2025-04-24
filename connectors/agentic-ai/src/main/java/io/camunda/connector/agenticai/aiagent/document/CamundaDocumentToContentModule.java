/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.document.Document;

public class CamundaDocumentToContentModule extends SimpleModule {

  private final CamundaDocumentToContentConverter documentConverter;

  public CamundaDocumentToContentModule(CamundaDocumentToContentConverter documentConverter) {
    super();
    this.documentConverter = documentConverter;
  }

  @Override
  public void setupModule(SetupContext context) {
    addSerializer(Document.class, new CamundaDocumentSerializer(documentConverter));
    super.setupModule(context);
  }
}
