/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.observationmasking;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Deterministic case facts retained when old tool observations are masked. */
public record CaseLedger(
    List<String> matchedTransactionIds,
    @Nullable String overchargeAmount,
    @Nullable String fraudVerdict,
    @Nullable String deadline,
    Map<String, String> reconciliationFacts) {

  private static final Pattern OVERCHARGE_TEXT =
      Pattern.compile("(?i)\"?overchargeAmount\"?\\s*[:=]\\s*\"?([0-9]+(?:\\.[0-9]+)?)");
  private static final Pattern DEADLINE_TEXT =
      Pattern.compile("(?i)\"?(?:filingDeadline|deadline)\"?\\s*[:=]\\s*\"?(\\d{4}-\\d{2}-\\d{2})");
  private static final Pattern FRAUD_TEXT =
      Pattern.compile(
          "(?i)\"?(?:fraud(?:\\s+risk)?(?:\\s+(?:verdict|opinion))?|risk|verdict)\"?\\s*[:=]\\s*\"?([A-Z_-]+)");

  public CaseLedger {
    matchedTransactionIds = List.copyOf(matchedTransactionIds);
    reconciliationFacts = reconciliationFacts == null ? Map.of() : Map.copyOf(reconciliationFacts);
  }

  public CaseLedger(
      List<String> matchedTransactionIds,
      @Nullable String overchargeAmount,
      @Nullable String fraudVerdict,
      @Nullable String deadline) {
    this(matchedTransactionIds, overchargeAmount, fraudVerdict, deadline, Map.of());
  }

  public static CaseLedger empty() {
    return new CaseLedger(List.of(), null, null, null);
  }

  public boolean isEmpty() {
    return matchedTransactionIds.isEmpty()
        && overchargeAmount == null
        && fraudVerdict == null
        && deadline == null
        && reconciliationFacts.isEmpty();
  }

  public CaseLedger merge(CaseLedger newer) {
    final var transactionIds = new LinkedHashSet<>(matchedTransactionIds);
    transactionIds.addAll(newer.matchedTransactionIds);
    final var facts = new LinkedHashMap<>(reconciliationFacts);
    facts.putAll(newer.reconciliationFacts);
    return new CaseLedger(
        List.copyOf(transactionIds),
        newer.overchargeAmount != null ? newer.overchargeAmount : overchargeAmount,
        newer.fraudVerdict != null ? newer.fraudVerdict : fraudVerdict,
        newer.deadline != null ? newer.deadline : deadline,
        facts);
  }

  public static CaseLedger fromMessages(List<Message> messages) {
    final var extractor = new Extractor();
    for (final var message : messages) {
      if (message instanceof ToolCallResultMessage results) {
        for (final var result : results.results()) {
          for (final var content : result.content()) {
            if (content instanceof ObjectContent objectContent) {
              extractor.visit(objectContent.content(), result.name());
            } else if (content instanceof TextContent textContent) {
              extractor.visitText(textContent.text(), result.name());
            }
          }
        }
      }
    }
    return extractor.toLedger();
  }

  private static final class Extractor {
    private final LinkedHashSet<String> transactionIds = new LinkedHashSet<>();
    private @Nullable String overchargeAmount;
    private @Nullable String fraudVerdict;
    private @Nullable String deadline;
    private final Map<String, String> reconciliationFacts = new LinkedHashMap<>();

    private void visit(@Nullable Object value, @Nullable String toolName) {
      if (value instanceof Map<?, ?> map) {
        for (final var entry : map.entrySet()) {
          if (entry.getKey() instanceof String key) {
            record(key, entry.getValue(), toolName);
            visit(entry.getValue(), toolName);
          }
        }
      } else if (value instanceof Iterable<?> items) {
        items.forEach(item -> visit(item, toolName));
      } else if (value instanceof String text) {
        visitText(text, toolName);
      }
    }

    private void record(String key, @Nullable Object value, @Nullable String toolName) {
      final String normalized = key.replaceAll("[^A-Za-z]", "").toLowerCase();
      if (value == null) {
        return;
      }
      if (toolName != null && toolName.toLowerCase().contains("reconcil")) {
        final var factName =
            switch (normalized) {
              case "refundtotal" -> "refundTotal";
              case "matchcount" -> "matchCount";
              case "daysbetweencharges" -> "daysBetweenCharges";
              default -> null;
            };
        if (factName != null && (value instanceof Number || value instanceof String)) {
          reconciliationFacts.put(factName, String.valueOf(value));
        }
        if (normalized.equals("refundsposted") && value instanceof Iterable<?> refunds) {
          final var ids = new ArrayList<String>();
          for (final var refund : refunds) {
            if (refund instanceof Map<?, ?> map && map.get("id") instanceof String id) {
              ids.add(id);
            }
          }
          reconciliationFacts.put("postedRefundTransactionIds", String.join(", ", ids));
        }
      }
      if (normalized.equals("matchedtransactionids")
          || normalized.equals("matchingtransactionids")
          || (normalized.equals("matches")
              && toolName != null
              && toolName.toLowerCase().contains("reconcil"))
          || (normalized.equals("transactionids")
              && toolName != null
              && toolName.toLowerCase().contains("reconcil"))) {
        addTransactionIds(value);
      } else if (normalized.equals("overchargeamount")) {
        overchargeAmount = valueText(value);
      } else if (normalized.equals("filingdeadline") || normalized.equals("deadline")) {
        deadline = valueText(value);
      } else if (normalized.equals("fraudverdict")
          || normalized.equals("fraudopinion")
          || normalized.equals("fraudrisk")
          || (toolName != null
              && toolName.toLowerCase().contains("fraud")
              && List.of("verdict", "opinion", "risk").contains(normalized))) {
        fraudVerdict = valueText(value);
      }
    }

    private void visitText(String text, @Nullable String toolName) {
      final var extractedOvercharge = firstGroup(OVERCHARGE_TEXT, text);
      final var extractedDeadline = firstGroup(DEADLINE_TEXT, text);
      if (extractedOvercharge != null) {
        overchargeAmount = extractedOvercharge;
      }
      if (extractedDeadline != null) {
        deadline = extractedDeadline;
      }
      if (toolName != null && toolName.toLowerCase().contains("fraud")) {
        final var extractedVerdict = firstGroup(FRAUD_TEXT, text);
        if (extractedVerdict != null) {
          fraudVerdict = extractedVerdict;
        }
      }
    }

    private static @Nullable String firstGroup(Pattern pattern, String text) {
      final Matcher matcher = pattern.matcher(text);
      return matcher.find() ? matcher.group(1) : null;
    }

    private void addTransactionIds(Object value) {
      if (value instanceof Iterable<?> values) {
        for (final var item : values) {
          if (item instanceof Map<?, ?> map) {
            final var id = map.get("id");
            if (id != null) {
              transactionIds.add(String.valueOf(id));
            }
          } else if (item != null) {
            transactionIds.add(String.valueOf(item));
          }
        }
      } else if (value instanceof String text) {
        transactionIds.add(text);
      }
    }

    private CaseLedger toLedger() {
      return new CaseLedger(
          new ArrayList<>(transactionIds),
          overchargeAmount,
          fraudVerdict,
          deadline,
          reconciliationFacts);
    }

    private static String valueText(Object value) {
      return value instanceof Number || value instanceof Boolean
          ? String.valueOf(value)
          : value instanceof String text ? text : "";
    }
  }
}
