/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.aws.agentcore.memory.model.response.MemoryRetrievalResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClientBuilder;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

@ExtendWith(MockitoExtension.class)
class AwsAgentCoreMemoryConnectorTest extends BaseTest {

  @Test
  void shouldBindVariablesAndExecuteRetrieve() throws Exception {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-test-123",
          "maxResults": 5,
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/strategies/prefs/actors/user-1",
            "searchQuery": "user preferences",
            "memoryStrategyId": "preferences",
            "topK": 3
          }
        }
        """;

    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .content(MemoryContent.builder().text("User prefers dark mode").build())
                    .memoryStrategyId("preferences")
                    .namespaces("/strategies/prefs/actors/user-1")
                    .createdAt(Instant.parse("2025-11-01T10:00:00Z"))
                    .score(0.95)
                    .metadata(Map.of("category", MetadataValue.fromStringValue("ui-preferences")))
                    .build())
            .nextToken("token-page-2")
            .build();

    var mockClient = mock(BedrockAgentCoreClient.class);
    when(mockClient.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);
    doNothing().when(mockClient).close();

    var mockBuilder = mock(BedrockAgentCoreClientBuilder.class);
    when(mockBuilder.credentialsProvider(any())).thenReturn(mockBuilder);
    when(mockBuilder.region(any())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenReturn(mockClient);

    try (MockedStatic<BedrockAgentCoreClient> staticMock =
        mockStatic(BedrockAgentCoreClient.class)) {
      staticMock.when(BedrockAgentCoreClient::builder).thenReturn(mockBuilder);

      var context = getContextBuilderWithSecrets().variables(input).build();
      var connector = new AwsAgentCoreMemoryConnectorFunction();

      // when
      Object result = connector.execute(context);

      // then
      assertThat(result).isInstanceOf(MemoryRetrievalResult.class);
      var memResult = (MemoryRetrievalResult) result;
      assertThat(memResult.recordCount()).isEqualTo(1);
      assertThat(memResult.nextToken()).isEqualTo("token-page-2");
      assertThat(memResult.memoryDocument()).isNotNull();

      // Verify SDK request
      var captor = org.mockito.ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
      verify(mockClient).retrieveMemoryRecords(captor.capture());
      var sdkReq = captor.getValue();
      assertThat(sdkReq.memoryId()).isEqualTo("mem-test-123");
      assertThat(sdkReq.namespace()).isEqualTo("/strategies/prefs/actors/user-1");
      assertThat(sdkReq.searchCriteria().searchQuery()).isEqualTo("user preferences");
      assertThat(sdkReq.searchCriteria().memoryStrategyId()).isEqualTo("preferences");
      assertThat(sdkReq.searchCriteria().topK()).isEqualTo(3);
      assertThat(sdkReq.maxResults()).isEqualTo(5);
    }
  }

  @Test
  void shouldHandleEmptyResults() throws Exception {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-empty",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/ns/test",
            "searchQuery": "find nothing"
          }
        }
        """;

    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(List.of()).build();

    var mockClient = mock(BedrockAgentCoreClient.class);
    when(mockClient.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);
    doNothing().when(mockClient).close();

    var mockBuilder = mock(BedrockAgentCoreClientBuilder.class);
    when(mockBuilder.credentialsProvider(any())).thenReturn(mockBuilder);
    when(mockBuilder.region(any())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenReturn(mockClient);

    try (MockedStatic<BedrockAgentCoreClient> staticMock =
        mockStatic(BedrockAgentCoreClient.class)) {
      staticMock.when(BedrockAgentCoreClient::builder).thenReturn(mockBuilder);

      var context = getContextBuilderWithSecrets().variables(input).build();
      var connector = new AwsAgentCoreMemoryConnectorFunction();

      // when
      Object result = connector.execute(context);

      // then
      assertThat(result).isInstanceOf(MemoryRetrievalResult.class);
      var memResult = (MemoryRetrievalResult) result;
      assertThat(memResult.recordCount()).isZero();
      assertThat(memResult.nextToken()).isNull();
    }
  }

  @Test
  void shouldUseDefaultCredentialsChain() throws Exception {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "defaultCredentialsChain"
          },
          "configuration": {
            "region": "eu-west-1"
          },
          "memoryId": "mem-default",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/ns/test",
            "searchQuery": "test"
          }
        }
        """;

    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(List.of()).build();

    var mockClient = mock(BedrockAgentCoreClient.class);
    when(mockClient.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);
    doNothing().when(mockClient).close();

    var mockBuilder = mock(BedrockAgentCoreClientBuilder.class);
    when(mockBuilder.credentialsProvider(any())).thenReturn(mockBuilder);
    when(mockBuilder.region(any())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenReturn(mockClient);

    try (MockedStatic<BedrockAgentCoreClient> staticMock =
        mockStatic(BedrockAgentCoreClient.class)) {
      staticMock.when(BedrockAgentCoreClient::builder).thenReturn(mockBuilder);

      var context = getContextBuilderWithSecrets().variables(input).build();
      var connector = new AwsAgentCoreMemoryConnectorFunction();

      // when
      Object result = connector.execute(context);

      // then
      assertThat(result).isInstanceOf(MemoryRetrievalResult.class);
    }
  }
}
