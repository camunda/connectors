/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase.model.response;

import io.camunda.connector.api.document.DocumentReference;
import java.util.Map;

public record RetrievalResultEntry(
    DocumentReference documentReference,
    String content,
    Double score,
    String sourceUri,
    Map<String, Object> metadata) {}
