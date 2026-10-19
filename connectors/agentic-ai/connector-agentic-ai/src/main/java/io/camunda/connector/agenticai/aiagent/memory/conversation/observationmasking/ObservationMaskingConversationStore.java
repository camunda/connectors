/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.observationmasking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationLoadResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * Prototype conversation store that masks older tool observations and pins deterministic case
 * facts.
 *
 * <p>The process variable holds the masked transcript and its immutable snapshot pointer. Full
 * observations remain in this runtime's memory and can be read through the local evidence endpoint.
 */
public class ObservationMaskingConversationStore implements ConversationStore, AutoCloseable {

  public static final String TYPE = "observation-masking";
  public static final String MASKED_RESULT_PROPERTY = "observationMasked";
  public static final String RESULT_POINTER_PROPERTY = "observationResultPointer";
  private static final int FULL_RESULTS_TO_KEEP = 2;
  private static final String EVIDENCE_PATH = "/agentic-ai/memory/";
  private static final Pattern SENSITIVE_TEXT =
      Pattern.compile(
          "(?i)(api[_-]?key|access[_-]?token|secret|password|authorization)\\s*[:=]\\s*([^\\s,;]+)");

  private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ConcurrentMap<String, ToolCallResultContent>>
      archivedResults = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final int port;
  private final String bindAddress;
  private final String externalHost;
  private @Nullable HttpServer server;

  public ObservationMaskingConversationStore(
      ObjectMapper objectMapper, int port, String externalHost) {
    this(objectMapper, port, "127.0.0.1", externalHost);
  }

  public ObservationMaskingConversationStore(
      ObjectMapper objectMapper, int port, String bindAddress, String externalHost) {
    this.objectMapper = objectMapper;
    this.port = port;
    this.bindAddress = bindAddress;
    this.externalHost = externalHost.replaceAll("/+$", "");
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public ConversationSession createSession(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var previousContext =
        ConversationUtil.loadConversationContext(
            agentContext, ObservationMaskingConversationContext.class);
    return new Session(previousContext);
  }

  @Override
  public synchronized void close() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  public static int configuredPort() {
    final String configured = System.getenv("AGENTIC_AI_MEMORY_EVIDENCE_PORT");
    return configured == null || configured.isBlank() ? 18087 : Integer.parseInt(configured);
  }

  public static String configuredHost() {
    return System.getenv().getOrDefault("AGENTIC_AI_MEMORY_EVIDENCE_HOST", "http://localhost");
  }

  public static String configuredBindAddress() {
    return System.getenv().getOrDefault("AGENTIC_AI_MEMORY_EVIDENCE_BIND_ADDRESS", "127.0.0.1");
  }

  private synchronized int startEvidenceServer() {
    if (server != null) {
      return server.getAddress().getPort();
    }
    try {
      final var address = new InetSocketAddress(InetAddress.getByName(bindAddress), port);
      final var httpServer = HttpServer.create(address, 0);
      httpServer.createContext(EVIDENCE_PATH, this::handleEvidenceRequest);
      httpServer.start();
      server = httpServer;
      return httpServer.getAddress().getPort();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not start the observation memory evidence endpoint on port " + port, e);
    }
  }

  private void handleEvidenceRequest(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!"GET".equals(exchange.getRequestMethod())) {
        send(exchange, 405, Map.of("error", "Only GET is supported."));
        return;
      }
      final var path = exchange.getRequestURI().getPath().substring(EVIDENCE_PATH.length());
      final var segments = path.split("/");
      if (segments.length < 2 || segments.length > 3) {
        send(exchange, 404, Map.of("error", "Memory snapshot not found."));
        return;
      }
      final var snapshot = snapshots.get(snapshotKey(segments[0], segments[1]));
      if (snapshot == null) {
        send(exchange, 404, Map.of("error", "Memory snapshot not found."));
        return;
      }
      if (segments.length == 3 && "results".equals(segments[2])) {
        final var callId = queryParameter(exchange, "callId");
        final var result =
            callId == null
                ? null
                : archivedResults
                    .getOrDefault(snapshot.conversationId(), new ConcurrentHashMap<>())
                    .get(callId);
        if (result == null) {
          send(exchange, 404, Map.of("error", "Archived tool result not found."));
          return;
        }
        send(exchange, 200, safeResult(result));
        return;
      }
      if (segments.length == 3) {
        send(exchange, 404, Map.of("error", "Memory snapshot not found."));
        return;
      }
      send(exchange, 200, snapshotView(snapshot));
    }
  }

  private void send(HttpExchange exchange, int status, Object body) throws IOException {
    final byte[] json = objectMapper.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, json.length);
    exchange.getResponseBody().write(json);
  }

  private Map<String, Object> snapshotView(Snapshot snapshot) {
    final var view = new LinkedHashMap<String, Object>();
    view.put("conversationId", snapshot.conversationId());
    view.put("snapshotId", snapshot.snapshotId());
    view.put("storeUrl", snapshot.storeUrl());
    view.put("strategy", TYPE);
    view.put("maskedContext", snapshot.messages().stream().map(this::safeMessage).toList());
    view.put("ledger", ledgerView(snapshot.ledger()));
    return view;
  }

  private Map<String, Object> safeMessage(Message message) {
    final var view = new LinkedHashMap<String, Object>();
    view.put("role", messageRole(message));
    if (message instanceof UserMessage userMessage) {
      view.put("content", userMessage.content().stream().map(this::safeContent).toList());
    } else if (message instanceof AssistantMessage assistantMessage) {
      view.put("content", assistantMessage.content().stream().map(this::safeContent).toList());
      if (assistantMessage.toolCalls() != null) {
        view.put(
            "toolCalls",
            assistantMessage.toolCalls().stream()
                .map(
                    call -> {
                      final var toolCall = new LinkedHashMap<String, Object>();
                      if (call.id() != null) {
                        toolCall.put("id", call.id());
                      }
                      if (call.name() != null) {
                        toolCall.put("name", call.name());
                      }
                      return toolCall;
                    })
                .toList());
      }
    } else if (message instanceof ToolCallResultMessage resultMessage) {
      view.put("results", resultMessage.results().stream().map(this::safeResult).toList());
    }
    return view;
  }

  private String messageRole(Message message) {
    return switch (message) {
      case UserMessage ignored -> "user";
      case AssistantMessage ignored -> "assistant";
      case ToolCallResultMessage ignored -> "tool";
      default -> "message";
    };
  }

  private Object safeContent(Content content) {
    return switch (content) {
      case TextContent text -> Map.of("text", redactText(text.text()));
      case ObjectContent object -> safeValue(object.content());
      case DocumentReferenceContent reference ->
          Map.of(
              "documentId",
              reference.id(),
              "fileName",
              reference.fileName() == null ? "" : reference.fileName(),
              "contentType",
              reference.contentType() == null ? "" : reference.contentType());
      case DocumentContent document ->
          Map.of(
              "document",
              document.document().metadata() == null
                  ? Map.of()
                  : Map.of(
                      "fileName",
                      String.valueOf(document.document().metadata().getFileName()),
                      "contentType",
                      String.valueOf(document.document().metadata().getContentType())));
      default -> Map.of("type", content.getClass().getSimpleName());
    };
  }

  private Map<String, Object> safeResult(ToolCallResultContent result) {
    final var view = new LinkedHashMap<String, Object>();
    if (result.id() != null) {
      view.put("callId", result.id());
    }
    if (result.name() != null) {
      view.put("toolName", result.name());
    }
    view.put("content", result.content().stream().map(this::safeContent).toList());
    return view;
  }

  private Object safeValue(@Nullable Object value) {
    if (value instanceof Map<?, ?> map) {
      final var safe = new LinkedHashMap<String, Object>();
      map.forEach(
          (key, item) -> {
            final String name = String.valueOf(key);
            safe.put(name, isSensitiveKey(name) ? "[REDACTED]" : safeValue(item));
          });
      return safe;
    }
    if (value instanceof Iterable<?> values) {
      return StreamSupport.stream(values.spliterator(), false).map(this::safeValue).toList();
    }
    if (value instanceof String text) {
      return redactText(text);
    }
    if (value instanceof Number || value instanceof Boolean) {
      return value;
    }
    return value == null ? null : Map.of("type", value.getClass().getSimpleName());
  }

  private static boolean isSensitiveKey(String key) {
    final String normalized = key.toLowerCase().replaceAll("[^a-z]", "");
    return normalized.contains("secret")
        || normalized.contains("password")
        || normalized.contains("apikey")
        || normalized.contains("token")
        || normalized.equals("authorization")
        || normalized.equals("credential");
  }

  private static String redactText(String value) {
    return SENSITIVE_TEXT.matcher(value).replaceAll("$1=[REDACTED]");
  }

  private static Map<String, Object> ledgerView(CaseLedger ledger) {
    final var view = new LinkedHashMap<String, Object>();
    view.put("matchedTransactionIds", ledger.matchedTransactionIds());
    if (ledger.overchargeAmount() != null) {
      view.put("overchargeAmount", ledger.overchargeAmount());
    }
    if (ledger.fraudVerdict() != null) {
      view.put("fraudVerdict", ledger.fraudVerdict());
    }
    if (ledger.deadline() != null) {
      view.put("deadline", ledger.deadline());
    }
    return view;
  }

  private static @Nullable String queryParameter(HttpExchange exchange, String name) {
    final var query = exchange.getRequestURI().getRawQuery();
    if (query == null) {
      return null;
    }
    for (final var part : query.split("&")) {
      final var pair = part.split("=", 2);
      if (pair.length == 2 && name.equals(pair[0])) {
        return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static String snapshotKey(String conversationId, String snapshotId) {
    return conversationId + "/" + snapshotId;
  }

  private final class Session implements ConversationSession {

    private final @Nullable ObservationMaskingConversationContext previousContext;

    private Session(@Nullable ObservationMaskingConversationContext previousContext) {
      this.previousContext = previousContext;
    }

    @Override
    public ConversationLoadResult loadMessages(AgentContext agentContext) {
      if (previousContext == null) {
        return ConversationLoadResult.empty();
      }
      return ConversationLoadResult.of(previousContext.messages());
    }

    @Override
    public ConversationContext storeMessages(
        AgentContext agentContext, ConversationStoreRequest request) {
      final String conversationId =
          previousContext != null ? previousContext.conversationId() : UUID.randomUUID().toString();
      final String snapshotId = UUID.randomUUID().toString();
      final int actualPort = startEvidenceServer();
      final String storeUrl =
          externalHost + ":" + actualPort + EVIDENCE_PATH + conversationId + "/" + snapshotId;
      final var ledger =
          (previousContext != null ? previousContext.ledger() : CaseLedger.empty())
              .merge(CaseLedger.fromMessages(request.messages()));
      final var maskedMessages =
          maskOlderResults(conversationId, snapshotId, storeUrl, request.messages());
      final var snapshot =
          new Snapshot(conversationId, snapshotId, storeUrl, maskedMessages, ledger);
      snapshots.put(snapshotKey(conversationId, snapshotId), snapshot);
      return ObservationMaskingConversationContext.builder()
          .conversationId(conversationId)
          .snapshotId(snapshotId)
          .storeUrl(storeUrl)
          .messages(maskedMessages)
          .ledger(ledger)
          .build();
    }

    private List<Message> maskOlderResults(
        String conversationId, String snapshotId, String storeUrl, List<Message> messages) {
      int fullResultCount = 0;
      for (final var message : messages) {
        if (message instanceof ToolCallResultMessage results) {
          fullResultCount +=
              (int) results.results().stream().filter(result -> !isMasked(result)).count();
        }
      }
      int resultIndex = 0;
      final List<Message> masked = new ArrayList<>(messages.size());
      for (final var message : messages) {
        if (!(message instanceof ToolCallResultMessage results)) {
          masked.add(message);
          continue;
        }
        final List<ToolCallResultContent> updatedResults = new ArrayList<>();
        for (final var result : results.results()) {
          if (isMasked(result)) {
            updatedResults.add(result);
            continue;
          }
          if (resultIndex++ >= fullResultCount - FULL_RESULTS_TO_KEEP) {
            updatedResults.add(result);
            continue;
          }
          final String callId =
              result.id() == null || result.id().isBlank()
                  ? message.id().toString() + "-" + resultIndex
                  : result.id();
          archivedResults
              .computeIfAbsent(conversationId, ignored -> new ConcurrentHashMap<>())
              .putIfAbsent(callId, result);
          final String pointer =
              storeUrl + "/results?callId=" + URLEncoder.encode(callId, StandardCharsets.UTF_8);
          final String maskedText =
              "Older tool result masked. Tool: "
                  + result.name()
                  + ". Call ID: "
                  + callId
                  + ". Full result: "
                  + pointer;
          final var properties = new LinkedHashMap<>(result.properties());
          properties.put(MASKED_RESULT_PROPERTY, true);
          properties.put(RESULT_POINTER_PROPERTY, pointer);
          updatedResults.add(
              new ToolCallResultContent(
                  result.id(),
                  result.name(),
                  List.of(TextContent.textContent(maskedText)),
                  result.elementId(),
                  result.completedAt(),
                  properties));
        }
        masked.add(results.withResults(updatedResults));
      }
      return List.copyOf(masked);
    }
  }

  private static boolean isMasked(ToolCallResultContent result) {
    return Boolean.TRUE.equals(result.properties().get(MASKED_RESULT_PROPERTY));
  }

  private record Snapshot(
      String conversationId,
      String snapshotId,
      String storeUrl,
      List<Message> messages,
      CaseLedger ledger) {}
}
