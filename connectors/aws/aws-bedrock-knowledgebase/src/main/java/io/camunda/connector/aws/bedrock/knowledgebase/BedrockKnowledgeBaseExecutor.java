/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.RetrieveOperation;
import io.camunda.connector.aws.bedrock.knowledgebase.model.response.KnowledgeBaseRetrievalResult;
import io.camunda.connector.aws.bedrock.knowledgebase.model.response.RetrievalResultEntry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockAgentRuntimeException;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;

public class BedrockKnowledgeBaseExecutor {

  private static final String ERROR_KB_RETRIEVAL_FAILED = "KB_RETRIEVAL_FAILED";

  private final BedrockAgentRuntimeClient client;

  public BedrockKnowledgeBaseExecutor(BedrockAgentRuntimeClient client) {
    this.client = client;
  }

  public KnowledgeBaseRetrievalResult execute(
      BedrockKnowledgeBaseRequest request, DocumentFactory documentFactory) {
    return switch (request.getOperation()) {
      case RetrieveOperation op -> retrieve(op, request, documentFactory);
    };
  }

  private KnowledgeBaseRetrievalResult retrieve(
      RetrieveOperation op, BedrockKnowledgeBaseRequest request, DocumentFactory documentFactory) {
    try {
      var sdkRequestBuilder =
          RetrieveRequest.builder()
              .knowledgeBaseId(request.getKnowledgeBaseId())
              .retrievalQuery(q -> q.text(op.query()));

      if (op.numberOfResults() != null) {
        sdkRequestBuilder.retrievalConfiguration(
            KnowledgeBaseRetrievalConfiguration.builder()
                .vectorSearchConfiguration(
                    KnowledgeBaseVectorSearchConfiguration.builder()
                        .numberOfResults(op.numberOfResults())
                        .build())
                .build());
      }

      var sdkResponse = client.retrieve(sdkRequestBuilder.build());

      var results =
          sdkResponse.retrievalResults().stream()
              .filter(r -> r.content() != null && r.content().text() != null)
              .map(
                  r -> {
                    var content = r.content().text();
                    var doc =
                        documentFactory.create(
                            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                                .contentType("text/plain")
                                .build());
                    return new RetrievalResultEntry(
                        doc.reference(),
                        content,
                        r.score(),
                        r.location() != null && r.location().s3Location() != null
                            ? r.location().s3Location().uri()
                            : null,
                        r.hasMetadata()
                            ? r.metadata().entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue() != null ? e.getValue().unwrap() : ""))
                            : Map.of());
                  })
              .toList();

      return new KnowledgeBaseRetrievalResult(results, results.size(), sdkResponse.nextToken());

    } catch (BedrockAgentRuntimeException e) {
      var errorMsg =
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(
          ERROR_KB_RETRIEVAL_FAILED, "Bedrock Knowledge Base error: " + errorMsg, e);
    }
  }
}
