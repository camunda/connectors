## Problem and fix

The synthetic baseline converts reasoning and opaque blocks from any provider into Anthropic blocks. Foreign reasoning can fail Anthropic payload validation; other foreign blocks can be replayed. Conversion now checks provider ownership first. Shared text, documents and objects retain their order, and Anthropic thinking/signatures and opaque blocks still round-trip. Stored history and tool-result handling are unchanged.

## Affected product paths

This converter handles user/assistant history for native Anthropic requests: AI Agent Task and AI Agent Sub-process v2, plus v1 configurations rewritten to native providers. All native Anthropic backends share it. The separate LangChain4j and Bedrock Converse adapters are unchanged.

## Why these tests

The mixed-content regression combines OpenAI reasoning, an opaque non-map reasoning payload, and foreign content shaped like a valid Anthropic text block. It verifies filtering before conversion, no foreign replay, and preservation of surrounding text, image bytes, objects, signed Anthropic thinking and native opaque content. Existing tests cover legacy/redacted thinking and tool results; request/response converter tests cover adjacent mappings.

## Completed checks

- Before the fix: 20 converter tests ran; two existing foreign-content tests failed and the new regression threw.
- After the fix: all 20 converter tests passed with Maven reactor dependencies (`-am`).
- Related request/response converters: all 53 tests passed with `-am`.
- Targeted Spotless check and `git diff --check` passed.

Successful Maven runs were offline, using a tracking-filename override for cached dependency provenance. Initial failed/interrupted attempts, exact commands and exit statuses are recorded in `commands.txt`; build logs are alongside it. `change.patch` contains only the converter and regression test relative to baseline `6b3aede`.

## Not verified

No full-suite, deployed saved-conversation, or live-provider testing was performed. No paid provider APIs were called.
