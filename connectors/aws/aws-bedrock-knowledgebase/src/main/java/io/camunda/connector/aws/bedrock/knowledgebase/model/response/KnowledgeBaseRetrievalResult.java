/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase.model.response;

import java.util.List;

/**
 * @param paginationToken Token for retrieving additional results in subsequent requests.
 */
public record KnowledgeBaseRetrievalResult(
    List<RetrievalResultEntry> results, int resultCount, String paginationToken) {}
