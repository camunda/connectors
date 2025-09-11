# Agentic AI Examples

This directory contains example projects showcasing the Agentic AI capabilities. It is split up into 3 categories:

- [ai-agent/ad-hoc-sub-process](ai-agent/ad-hoc-sub-process): example projects directly implementing AI Agents on top of
  an ad-hoc sub-process. This
  is the recommended approach for most use cases.
- [ai-agent/service-task](ai-agent/service-task): example projects implementing AI Agents using service tasks. This
  approach is more
  complex and requires more manual modeling work to implement the tool calling feedback loop, but can be useful in
  certain scenarios.
- [ad-hoc-tools-schema](ad-hoc-tools-schema): example project directly using
  the [Ad-Hoc Tools Schema Resolver](https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-ad-hoc-tools-schema-resolver/)
  to derive tool descriptions from elements within the ad-hoc sub-process. This can be used in combination with custom
  LLM connectors.
