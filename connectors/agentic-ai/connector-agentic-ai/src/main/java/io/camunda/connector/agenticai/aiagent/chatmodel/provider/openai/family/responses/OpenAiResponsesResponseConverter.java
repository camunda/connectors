/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException.PartialResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiToolCallArguments;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Maps an accumulated OpenAI Responses API SDK {@link Response} to the domain {@link
 * AssistantMessage}, its {@link AgentMetrics}, and a {@link ChatResult}.
 *
 * <p>An output {@code message} item's {@code output_text} parts become {@link TextContent}, {@code
 * function_call} items become {@link ToolCall}s, and {@code reasoning} items become {@link
 * ReasoningContent} whose {@code payload} carries the full raw item so it round-trips losslessly
 * back onto the request; see {@link #toReasoningContent}. Any provider-specific or unrecognized
 * output item is captured losslessly as {@link ProviderContent} (kept inline in original order) and
 * never added to {@code toolCalls}, since the caller is never expected to act on it.
 *
 * <p>The domain {@link StopReason} is derived from the response shape: an {@code
 * incomplete_details.reason} of {@code max_output_tokens} maps to {@link StopReason#LENGTH} as a
 * normal completion; {@code content_filter} instead throws {@link ContentFilteredException},
 * carrying the assistant message and metrics already built for the turn as its {@link
 * PartialResult}. Otherwise, one or more {@code function_call} items map to {@link
 * StopReason#TOOL_USE}, and a normal completion maps to {@link StopReason#STOP}. A top-level {@code
 * status} of {@code failed} is handled separately, before any of the above -- see {@link
 * #checkForFailure}.
 *
 * <p>The raw vendor stop-reason string is always preserved under the {@code openai} provider-id key
 * in {@link AssistantMessage#metadata()}, independent of how it normalizes to the domain {@link
 * StopReason}.
 */
public class OpenAiResponsesResponseConverter {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiResponsesResponseConverter.class);

  private static final String OPENAI_PROVIDER = "openai";

  private final ObjectMapper objectMapper;

  public OpenAiResponsesResponseConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChatResult toResult(Response response, Duration executionTime) {
    checkForFailure(response);
    final AssistantMessage assistantMessage = toAssistantMessage(response);
    final AgentMetrics metrics =
        toMetrics(response, assistantMessage.toolCalls().size(), executionTime);

    if (assistantMessage.stopReason() instanceof UnknownStopReason unknown
        && Response.IncompleteDetails.Reason.CONTENT_FILTER.asString().equals(unknown.value())) {
      throw new ContentFilteredException(
          "Model response was blocked by provider content filtering.",
          new PartialResult(assistantMessage, metrics));
    }

    return new ChatResult.Completed(assistantMessage, metrics);
  }

  /**
   * A {@code response.failed} terminal event carries this same {@link Response} shape, with {@code
   * status: failed} and {@code error} populated instead of a normal completion -- it has neither
   * {@code incomplete_details} nor (usually) tool calls, so {@link #mapStopReason} would otherwise
   * fall through to a plain {@link StopReason#STOP}/{@link StopReason#TOOL_USE} and this converter
   * would report a successful {@link ChatResult.Completed}. Surface it as a failed model call
   * instead, mirroring how {@link
   * io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModel} already
   * reports transport/SDK-level failures under the same error code.
   */
  private void checkForFailure(Response response) {
    if (response.status().filter(ResponseStatus.FAILED::equals).isEmpty()) {
      return;
    }
    final String detail =
        response
            .error()
            .map(error -> "%s: %s".formatted(error.code().asString(), error.message()))
            .orElse("no error detail provided by OpenAI");
    throw new ConnectorException(
        ERROR_CODE_FAILED_MODEL_CALL, "OpenAI response failed: %s".formatted(detail));
  }

  AssistantMessage toAssistantMessage(Response response) {
    final List<Content> content = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();
    @Nullable String assistantMessageId = null;

    for (final ResponseOutputItem item : response.output()) {
      if (item.message().isPresent()) {
        final ResponseOutputMessage message = item.message().get();
        // The first message item's own id (msg_*), not response.id() (resp_*, the envelope this
        // turn came from). If a response ever produced more than one message item, only the
        // first's id survives; the rest are lost the same way their items' content is already
        // flattened into one list.
        if (assistantMessageId == null) {
          assistantMessageId = message.id();
        }
        for (final ResponseOutputMessage.Content messageContent : message.content()) {
          messageContent
              .outputText()
              .ifPresent(text -> content.add(TextContent.textContent(text.text())));
          // A refusal has no dedicated domain content type; surface its text as visible
          // assistant text rather than silently dropping it.
          messageContent
              .refusal()
              .ifPresent(refusal -> content.add(TextContent.textContent(refusal.refusal())));
        }
      } else if (item.functionCall().isPresent()) {
        final ResponseFunctionToolCall functionCall = item.functionCall().get();
        toolCalls.add(
            ToolCall.builder()
                .id(functionCall.callId())
                .name(functionCall.name())
                .arguments(OpenAiToolCallArguments.parse(objectMapper, functionCall.arguments()))
                .build());
      } else if (item.reasoning().isPresent()) {
        content.add(toReasoningContent(item));
      } else if (item.webSearchCall().isPresent() || item.codeInterpreterCall().isPresent()) {
        content.add(ProviderContent.providerContent(OPENAI_PROVIDER, toRawMap(item)));
      } else {
        // Server-tool / provider-specific items not recognized by this SDK version have no
        // provider-neutral representation. Preserve them losslessly and in original order as
        // ProviderContent rather than silently dropping them; they are never client tool calls
        // (the caller is never expected to act on them), so they are kept out of toolCalls.
        final Map<String, Object> raw = toRawMap(item);
        if (LOG.isTraceEnabled()) {
          LOG.trace(
              "OpenAI server-side output item preserved as ProviderContent: type={}, payload={}",
              raw.get("type"),
              raw);
        }
        content.add(ProviderContent.providerContent(OPENAI_PROVIDER, raw));
      }
    }

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .messageId(assistantMessageId)
        .modelId(modelId(response.model()))
        .stopReason(mapStopReason(response, !toolCalls.isEmpty()))
        .metadata(AssistantMessageMetadata.withDefaults(openAiMetadata(response)))
        .build();
  }

  /**
   * The raw vendor stop-reason string preserved under the {@code openai} provider-id key in {@link
   * AssistantMessage#metadata()}, independent of how it normalizes to the domain {@link
   * StopReason}. The Responses API has no single stop-reason enum: an {@code
   * incomplete_details.reason} is the most specific raw signal when present (truncation / content
   * filtering); otherwise the response's top-level {@code status} (e.g. {@code completed}) is used
   * as a fallback raw signal.
   */
  private Map<String, Object> openAiMetadata(Response response) {
    return response
        .incompleteDetails()
        .flatMap(Response.IncompleteDetails::reason)
        .map(Response.IncompleteDetails.Reason::asString)
        .or(() -> response.status().map(ResponseStatus::asString))
        .<Map<String, Object>>map(sr -> Map.of(OPENAI_PROVIDER, Map.of("stopReason", sr)))
        .orElse(Map.of());
  }

  /**
   * Maps the Responses API's response shape to the domain {@link StopReason}: an {@code
   * incomplete_details.reason} of {@code max_output_tokens} maps to {@link StopReason#LENGTH}
   * (returned as a normal completion rather than raised as an error -- the caller decides
   * whether/how to react to a truncated turn). Otherwise, a response containing one or more {@code
   * function_call} items maps to {@link StopReason#TOOL_USE}, and a normal completion maps to
   * {@link StopReason#STOP}. {@code content_filter} never reaches this mapping -- {@link #toResult}
   * throws before calling it.
   */
  private StopReason mapStopReason(Response response, boolean hasToolCalls) {
    final var incompleteReason =
        response.incompleteDetails().flatMap(Response.IncompleteDetails::reason);
    if (incompleteReason.isPresent()) {
      final var reason = incompleteReason.get();
      return switch (reason.value()) {
        case MAX_OUTPUT_TOKENS -> StopReason.LENGTH;
        default -> new StopReason.UnknownStopReason(reason.asString());
      };
    }

    return hasToolCalls ? StopReason.TOOL_USE : StopReason.STOP;
  }

  /**
   * {@link ResponsesModel} is a three-way union (a bare string, a {@code ChatModel} enum member, or
   * a Responses-only enum member): the wire value {@code "gpt-5"} deserializes into the {@code
   * ChatModel} variant (it matches a known enum member) rather than the bare-string variant, so
   * {@code asString()} cannot be called unconditionally -- the matching variant must be resolved
   * first.
   */
  private String modelId(ResponsesModel model) {
    if (model.isString()) {
      return model.asString();
    } else if (model.isChat()) {
      return model.asChat().asString();
    } else if (model.isOnly()) {
      return model.asOnly().asString();
    }
    return model.toString();
  }

  /**
   * Builds the {@link ReasoningContent} for a reasoning output item. The human-readable summary
   * text is always lifted out of {@code summary} into {@link ReasoningContent#text()}. Whether
   * {@code summary} is also stripped from {@code payload} depends on {@link
   * #canReconstructSummaryFromText}: when it holds, {@code text()} is the sole copy and {@link
   * OpenAiResponsesRequestConverter#mergeReasoningText} rebuilds {@code summary} from it before
   * replay; otherwise {@code summary} is left untouched in {@code payload} -- deliberately
   * duplicated with {@code text()} -- since reconstructing it from a single joined string would be
   * lossy (multiple entries, or an entry carrying fields this domain model doesn't model).
   */
  private ReasoningContent toReasoningContent(ResponseOutputItem item) {
    final Map<String, Object> raw = new LinkedHashMap<>(toRawMap(item));
    final Optional<ResponseReasoningItem> reasoning = item.reasoning();
    final String text = reasoning.map(this::summaryText).orElse(null);
    if (text == null) {
      return ReasoningContent.reasoningContent(OPENAI_PROVIDER, raw);
    }
    if (reasoning.filter(this::canReconstructSummaryFromText).isPresent()) {
      raw.remove("summary");
    }
    return new ReasoningContent(OPENAI_PROVIDER, raw, text, Map.of());
  }

  private @Nullable String summaryText(ResponseReasoningItem reasoning) {
    final String joined =
        reasoning.summary().stream()
            .map(ResponseReasoningItem.Summary::text)
            .collect(Collectors.joining("\n"));
    return StringUtils.hasText(joined) ? joined : null;
  }

  /**
   * Holds only when {@code summary} can be reconstructed byte-identical from {@link #summaryText}'s
   * joined string alone: exactly one entry, with no additional/unknown fields on it. This is safe
   * to check conservatively: {@code summary} plays no role in verifying reasoning continuity (only
   * {@code encrypted_content}/{@code id} do), so leaving it un-stripped costs nothing but the
   * deduplication.
   */
  private boolean canReconstructSummaryFromText(ResponseReasoningItem reasoning) {
    final List<ResponseReasoningItem.Summary> summary = reasoning.summary();
    return summary.size() == 1 && summary.get(0)._additionalProperties().isEmpty();
  }

  /**
   * Uses the SDK's own {@link ObjectMappers#jsonMapper()} rather than the injected app {@link
   * ObjectMapper}: only it knows how to serialize the raw item's {@code JsonValue}/{@code
   * JsonField} internals faithfully (e.g. omitting genuinely-absent optional fields instead of
   * materializing them as explicit {@code null}, and not leaking the Kotlin-generated {@code
   * isValid()} property as a spurious {@code valid} key).
   */
  private Map<String, Object> toRawMap(ResponseOutputItem item) {
    return ObjectMappers.jsonMapper()
        .convertValue(item, new TypeReference<Map<String, Object>>() {});
  }

  private AgentMetrics toMetrics(Response response, int toolCalls, Duration executionTime) {
    final AgentMetrics.TokenUsage tokenUsage =
        response.usage().map(this::toTokenUsage).orElseGet(AgentMetrics.TokenUsage::empty);

    return AgentMetrics.builder()
        .modelCalls(1)
        .toolCalls(toolCalls)
        .tokenUsage(tokenUsage)
        .executionTime(executionTime)
        .build();
  }

  private AgentMetrics.TokenUsage toTokenUsage(ResponseUsage usage) {
    final long cachedTokens = usage.inputTokensDetails().cachedTokens();

    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount((int) (usage.inputTokens() - cachedTokens))
        .outputTokenCount((int) usage.outputTokens())
        .cacheReadTokenCount((int) cachedTokens)
        .reasoningTokenCount((int) usage.outputTokensDetails().reasoningTokens())
        .build();
  }
}
