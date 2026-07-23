# `MessageAccumulator` throws "Missing input JSON" for `server_tool_use` blocks whose input is not streamed as `input_json_delta`

## Summary

When streaming a Messages response that uses a **server-side tool** (e.g. `web_search`), the
API can deliver the tool's `input` inline in the `content_block_start` event with **no**
subsequent `input_json_delta` events. `MessageAccumulator` (and `BetaMessageAccumulator`)
unconditionally require at least one `input_json_delta` for any block where
`tracksToolInput()` is true — which includes `server_tool_use` — and therefore throw:

```
com.anthropic.errors.AnthropicInvalidDataException: Missing input JSON for index N.
```

The accumulated message is lost even though the `content_block_start` event already carried the
complete, valid tool input.

- SDK: `com.anthropic:anthropic-java` 2.48.0 (also present on `main` / 2.49.0)
- Affects both `MessageAccumulator` (stable) and `BetaMessageAccumulator` (beta) — identical logic.

## Root cause

In `MessageAccumulator.kt` (line numbers from 2.48.0):

1. `tracksToolInput()` returns `true` for server tool blocks:
   ```kotlin
   internal fun ContentBlock.tracksToolInput(): Boolean =
       isToolUse() || isServerToolUse()          // line 46
   ```

2. `visitContentBlockStart` stores the `server_tool_use` block but **never seeds**
   `messageContentInputJson[index]` from the block's own inline `input` field:
   ```kotlin
   override fun visitServerToolUse(serverToolUse: ServerToolUseBlock): ContentBlock =
       ContentBlock.ofServerToolUse(serverToolUse)   // line ~345 — input ignored
   ```
   `messageContentInputJson[index]` is populated **only** by `visitContentBlockDelta` →
   `visitInputJson` (line ~421).

3. `visitContentBlockStop` then throws when no delta ever arrived:
   ```kotlin
   if (oldContentBlock.tracksToolInput()) {
       inputJson
           ?: throw AnthropicInvalidDataException("Missing input JSON for index $index.")  // line ~468
   }
   ```

For a regular client `tool_use` block, `content_block_start` carries an empty input (`{}`) and the
input is streamed via `input_json_delta` deltas, so the map gets populated and the guard passes.
For `web_search` server tool calls, the API delivers the full input inline at `content_block_start`
and sends no deltas, so the map entry is never created and the guard fails. (The exact
inline-vs-delta behavior is per server tool — `code_execution`, for instance, does stream deltas and
is unaffected.)

## Minimal reproducer (stable client, real API)

```java
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.WebSearchTool20250305;

public class AccumulatorServerToolRepro {
  public static void main(String[] args) {
    // Reads ANTHROPIC_API_KEY from the environment.
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(Model.CLAUDE_SONNET_4_5)
            .maxTokens(1024)
            .addUserMessage("Use web search to find today's top technology news headline.")
            .addTool(WebSearchTool20250305.builder().build())
            .build();

    MessageAccumulator accumulator = MessageAccumulator.create();
    try (StreamResponse<RawMessageStreamEvent> stream =
        client.messages().createStreaming(params)) {
      stream.stream().forEach(accumulator::accumulate);
    }

    // Throws: AnthropicInvalidDataException: Missing input JSON for index N
    Message message = accumulator.message();
    System.out.println(message);
  }
}
```

Running this prints a stack trace terminating in:

```
Caused by: com.anthropic.errors.AnthropicInvalidDataException: Missing input JSON for index N.
	at com.anthropic.helpers.MessageAccumulator$accumulate$1.visitContentBlockStop(MessageAccumulator.kt:468)
```

(The same reproducer against the beta client — `client.beta().messages().createStreaming(...)`
with `BetaMessageAccumulator` and the `beta.messages` types — fails identically at
`BetaMessageAccumulator.kt:531`.)

### No-network variant

The bug is purely in event accumulation and can be reproduced without the API by feeding a
synthetic event sequence to `MessageAccumulator.accumulate(...)`:

1. `message_start`
2. `content_block_start` at index 0 with a `server_tool_use` block whose `input` is a fully
   populated object, e.g. `{"query": "top technology news"}`
3. `content_block_stop` at index 0 **(no `input_json_delta` in between)**
4. `message_delta` + `message_stop`

`message()` then throws `Missing input JSON for index 0`.

## Expected behavior

When a tool-input-tracking block's input is delivered inline in `content_block_start` and no
`input_json_delta` follows, the accumulator should use the inline input rather than throwing. The
accumulated `Message` should contain the `server_tool_use` block with its complete `input`.

## Suggested fix

In `visitContentBlockStart`, when the started block is tool-input-tracking and carries a non-empty
inline `input`, seed `messageContentInputJson[index]` with the serialized inline JSON. Deltas (if
any subsequently arrive) then append as today; the `content_block_stop` guard passes either way.
Equivalently, `visitContentBlockStop` could fall back to the block's own inline `input` when no
delta-accumulated JSON is present, before throwing.
