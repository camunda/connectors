/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;

@DocumentReturnFormat(
    group = "operation",
    tooltip =
        "How the rendered CSV should be returned. Document reference uploads it to the document"
            + " store; as text returns the CSV inline as a String.",
    supportedFormats = {DocumentReturnChoice.DOCUMENT, DocumentReturnChoice.TEXT},
    defaultFormat = DocumentReturnChoice.DOCUMENT)
public record WriteCsvRequest(
    List<?> data,
    // Legacy input from element-template versions <= 2, replaced by the @DocumentReturnFormat
    // response dropdown. Kept (but hidden) so old templates still deserialize and pick the legacy
    // output path when documentReturnFormat is absent.
    @TemplateProperty(ignore = true) boolean createDocument,
    CsvFormat format) {}
