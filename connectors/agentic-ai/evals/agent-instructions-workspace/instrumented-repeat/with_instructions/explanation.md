# Fix and affected paths

The synthetic baseline converts reasoning and provider-native content without checking its provider tag. After switching from OpenAI, that can replay foreign blocks or fail Anthropic-specific payload validation.

The fix checks the tag before conversion and drops only foreign reasoning/native blocks. Ordinary text, documents, objects, and valid Anthropic blocks remain in order. Tool-result JSON fallback and persisted conversation formats are unchanged.

This affects the native Anthropic Messages path shared by AI Agent Task and AI Agent Sub-process: v2 configuration and the default v1-to-native rewrite, across Anthropic API, Bedrock Mantle, Foundry, and custom backends. Conversion is independent of the in-process, Camunda document, or AWS AgentCore conversation store. The opt-out LangChain4j path, separate Bedrock Converse provider, and other providers are unchanged. Existing saved conversations need no schema migration.

# Why these tests are sufficient

Two focused regressions check:

- The complete ordered output of mixed saved content, preserving useful content and Anthropic thinking/signatures/native blocks while rejecting foreign blocks—even an Anthropic-shaped foreign payload.
- Foreign opaque payloads are discarded before Anthropic-specific validation.

Both new tests failed before the fix. Existing tests also cover legacy and redacted thinking, document conversion, and tool-result fallback. This is a pure content transformation; correctness does not depend on engine/BPMN wiring, so no new E2E test or capability-matrix entry is needed.

# Actually completed

- Pre-fix converter run: 21 tests, 3 failures and 1 error.
- Final focused converter run with Maven reactor dependencies (`-am`): **21 passed**, none skipped.
- Combined content/request/response converter run with `-am`: **74 passed**, none skipped.
- Spotless apply/check, license format/check, `git diff --check HEAD`, and saved-patch reverse-application check: **passed**.

Maven ran offline with build caching disabled. Selecting its simple local-repository manager resolved cached-artifact origin mismatches. Every shell-command run and exit status, including failed attempts, is recorded in `commands.txt`; Maven logs are alongside it. `change.patch` contains only the production converter and its test changes against synthetic baseline HEAD.

# Not verified

No full-suite/install, CPT/BPMN, live conversation-store, migration, or real-provider integration tests were run. Product-path scope above is source-traced, not integration-tested. No paid provider APIs were called; no commit or PR was created.
