/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

/**
 * The attribute vocabulary of the AI Agent spans. The {@code gen_ai.*}, {@code session.id}, and
 * {@code error.type} names are pinned to the OpenTelemetry GenAI semantic conventions
 * (semantic-conventions-genai, status Development, as of 2026-10-02); the {@code camunda.*} names
 * are Camunda's own. An upstream rename is a change in this class.
 */
public final class GenAiAttributes {

  private GenAiAttributes() {}

  // --- Semantic conventions: operation, provider, model ---

  public static final AttributeKey<String> OPERATION_NAME =
      AttributeKey.stringKey("gen_ai.operation.name");
  public static final AttributeKey<String> PROVIDER_NAME =
      AttributeKey.stringKey("gen_ai.provider.name");
  public static final AttributeKey<String> REQUEST_MODEL =
      AttributeKey.stringKey("gen_ai.request.model");
  public static final AttributeKey<String> RESPONSE_MODEL =
      AttributeKey.stringKey("gen_ai.response.model");
  public static final AttributeKey<String> RESPONSE_ID =
      AttributeKey.stringKey("gen_ai.response.id");
  public static final AttributeKey<List<String>> RESPONSE_FINISH_REASONS =
      AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

  // --- Semantic conventions: token usage ---

  public static final AttributeKey<Long> USAGE_INPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.input_tokens");
  public static final AttributeKey<Long> USAGE_OUTPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.output_tokens");
  public static final AttributeKey<Long> USAGE_CACHE_READ_INPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.cache_read.input_tokens");
  public static final AttributeKey<Long> USAGE_CACHE_CREATION_INPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.cache_creation.input_tokens");
  public static final AttributeKey<Long> USAGE_REASONING_OUTPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.reasoning.output_tokens");

  // --- Semantic conventions: conversation and agent ---

  public static final AttributeKey<String> CONVERSATION_ID =
      AttributeKey.stringKey("gen_ai.conversation.id");
  public static final AttributeKey<String> AGENT_ID = AttributeKey.stringKey("gen_ai.agent.id");
  public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
  public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("session.id");

  // --- Semantic conventions: tools ---

  public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
  public static final AttributeKey<String> TOOL_CALL_ID =
      AttributeKey.stringKey("gen_ai.tool.call.id");
  public static final AttributeKey<String> TOOL_TYPE = AttributeKey.stringKey("gen_ai.tool.type");
  public static final AttributeKey<String> TOOL_CALL_ARGUMENTS =
      AttributeKey.stringKey("gen_ai.tool.call.arguments");
  public static final AttributeKey<String> TOOL_CALL_RESULT =
      AttributeKey.stringKey("gen_ai.tool.call.result");

  // --- Semantic conventions: content (opt-in) ---

  public static final AttributeKey<String> INPUT_MESSAGES =
      AttributeKey.stringKey("gen_ai.input.messages");
  public static final AttributeKey<String> OUTPUT_MESSAGES =
      AttributeKey.stringKey("gen_ai.output.messages");
  public static final AttributeKey<String> SYSTEM_INSTRUCTIONS =
      AttributeKey.stringKey("gen_ai.system_instructions");
  public static final AttributeKey<String> TOOL_DEFINITIONS =
      AttributeKey.stringKey("gen_ai.tool.definitions");

  // --- Semantic conventions: errors ---

  public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

  // --- Camunda attributes ---

  public static final AttributeKey<String> PROCESS_INSTANCE_KEY =
      AttributeKey.stringKey("camunda.process_instance.key");
  public static final AttributeKey<String> ELEMENT_INSTANCE_KEY =
      AttributeKey.stringKey("camunda.element_instance.key");
  public static final AttributeKey<String> ELEMENT_ID =
      AttributeKey.stringKey("camunda.element.id");
  public static final AttributeKey<String> BPMN_PROCESS_ID =
      AttributeKey.stringKey("camunda.bpmn_process_id");
  public static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("camunda.tenant_id");
  public static final AttributeKey<String> AGENT_INSTANCE_KEY =
      AttributeKey.stringKey("camunda.agent_instance.key");
  public static final AttributeKey<String> JOB_KEY = AttributeKey.stringKey("camunda.job.key");
  public static final AttributeKey<String> JOB_KIND =
      AttributeKey.stringKey("camunda.agent.job.kind");
  public static final AttributeKey<String> JOB_OUTCOME =
      AttributeKey.stringKey("camunda.agent.job.outcome");
  public static final AttributeKey<String> JOB_ATTEMPT =
      AttributeKey.stringKey("camunda.agent.job.attempt");
  public static final AttributeKey<String> RUN_TRIGGER =
      AttributeKey.stringKey("camunda.agent.run.trigger");
  public static final AttributeKey<String> RUN_INPUT =
      AttributeKey.stringKey("camunda.agent.run.input");
  public static final AttributeKey<String> TOOL_OUTCOME =
      AttributeKey.stringKey("camunda.agent.tool.outcome");
  public static final AttributeKey<String> TOOL_START_TIME_SOURCE =
      AttributeKey.stringKey("camunda.agent.tool.start_time_source");

  // --- Values ---

  public static final String OPERATION_CHAT = "chat";
  public static final String OPERATION_INVOKE_AGENT = "invoke_agent";
  public static final String OPERATION_EXECUTE_TOOL = "execute_tool";

  public static final String TOOL_TYPE_FUNCTION = "function";

  public static final String JOB_OUTCOME_COMPLETED = "completed";
  public static final String JOB_OUTCOME_FAILED = "failed";
  public static final String JOB_OUTCOME_SUPERSEDED = "superseded";
  public static final String JOB_OUTCOME_BPMN_ERROR = "bpmn_error";

  public static final String TOOL_OUTCOME_COMPLETED = "completed";
  public static final String TOOL_OUTCOME_CANCELLED = "cancelled";

  /** The tool call start was derived from the time-ordered id of the message that issued it. */
  public static final String START_TIME_SOURCE_MESSAGE_ID = "message_id";

  /** The tool call start is unknown; the span has no duration. */
  public static final String START_TIME_SOURCE_UNKNOWN = "unknown";

  public static final String RUN_TRIGGER_INITIAL = "initial";
  public static final String RUN_TRIGGER_FOLLOW_UP = "follow_up";

  public static final String ERROR_TYPE_JOB_SUPERSEDED = "job_superseded";
}
