/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore.BedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListEventsIterable;

/**
 * In-memory implementation of {@link BedrockAgentCoreClientFactory} for e2e tests. Stores events in
 * a shared {@link ConcurrentHashMap} so that events written in one agent turn are visible in
 * subsequent turns. Supports branch chain traversal via {@code includeParentBranches}.
 */
public class InMemoryBedrockAgentCoreClientFactory implements BedrockAgentCoreClientFactory {

  public static final InMemoryBedrockAgentCoreClientFactory INSTANCE =
      new InMemoryBedrockAgentCoreClientFactory();

  private final Map<String, StoredEvent> events = new ConcurrentHashMap<>();
  private final Map<String, BranchInfo> branches = new ConcurrentHashMap<>();
  private final AtomicInteger eventCounter = new AtomicInteger(0);

  @Override
  public BedrockAgentCoreClient createClient(AwsAgentCoreMemoryStorageConfiguration config) {
    var client = mock(BedrockAgentCoreClient.class);

    doAnswer(invocation -> handleCreateEvent(invocation.getArgument(0)))
        .when(client)
        .createEvent(any(CreateEventRequest.class));

    doAnswer(invocation -> handleListEvents(invocation.getArgument(0)))
        .when(client)
        .listEventsPaginator(any(ListEventsRequest.class));

    doNothing().when(client).close();

    return client;
  }

  public void clear() {
    events.clear();
    branches.clear();
    eventCounter.set(0);
  }

  private CreateEventResponse handleCreateEvent(CreateEventRequest request) {
    var eventId = "evt-" + eventCounter.incrementAndGet();

    String branchName = null;
    if (request.branch() != null) {
      branchName = request.branch().name();
      branches.putIfAbsent(branchName, new BranchInfo(branchName, request.branch().rootEventId()));
    }

    var storedEvent =
        new StoredEvent(
            eventId,
            request.memoryId(),
            request.actorId(),
            request.sessionId(),
            branchName,
            Instant.now(),
            request.payload(),
            request.metadata());

    events.put(eventId, storedEvent);

    return CreateEventResponse.builder().event(Event.builder().eventId(eventId).build()).build();
  }

  private ListEventsIterable handleListEvents(ListEventsRequest request) {
    String targetBranch = null;
    boolean includeParents = false;

    if (request.filter() != null && request.filter().branch() != null) {
      targetBranch = request.filter().branch().name();
      includeParents = request.filter().branch().includeParentBranches();
    }

    // Collect branch names to include
    var branchesToInclude = new ArrayList<String>();
    if (targetBranch != null) {
      branchesToInclude.add(targetBranch);

      if (includeParents) {
        // Walk up the branch chain to find parent branches
        var currentBranch = targetBranch;
        while (currentBranch != null) {
          var branchInfo = branches.get(currentBranch);
          if (branchInfo == null) {
            break;
          }

          // Find which branch the rootEventId belongs to
          var rootEvent = events.get(branchInfo.rootEventId());
          if (rootEvent == null) {
            break;
          }

          var parentBranch = rootEvent.branchName();
          if (parentBranch != null) {
            branchesToInclude.add(parentBranch);
          }
          currentBranch = parentBranch;
        }
      }
    }

    final var requestedBranch = targetBranch;
    final var branchSet = branchesToInclude;

    List<Event> matchingEvents =
        events.values().stream()
            .filter(e -> e.memoryId().equals(request.memoryId()))
            .filter(e -> e.actorId().equals(request.actorId()))
            .filter(e -> e.sessionId().equals(request.sessionId()))
            .filter(
                e -> {
                  if (requestedBranch == null) {
                    // No branch filter: return events on the main timeline (no branch)
                    return e.branchName() == null;
                  }
                  // Include events on any branch in the chain, plus main timeline events
                  return branchSet.contains(e.branchName()) || e.branchName() == null;
                })
            .map(
                e ->
                    Event.builder()
                        .eventId(e.eventId())
                        .eventTimestamp(e.timestamp())
                        .payload(e.payloads())
                        .metadata(e.metadata())
                        .build())
            .toList();

    var iterable = mock(ListEventsIterable.class);
    doAnswer(
            inv ->
                (software.amazon.awssdk.core.pagination.sync.SdkIterable<Event>)
                    () -> matchingEvents.iterator())
        .when(iterable)
        .events();

    return iterable;
  }

  private record StoredEvent(
      String eventId,
      String memoryId,
      String actorId,
      String sessionId,
      String branchName,
      Instant timestamp,
      List<software.amazon.awssdk.services.bedrockagentcore.model.PayloadType> payloads,
      Map<String, software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue> metadata) {}

  private record BranchInfo(String name, String rootEventId) {}
}
