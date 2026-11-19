# Connector board: missing team label classification (2026-11-19)

Query: `project:camunda/23 is:issue state:open -label:connectors-core -label:connectors-experience -label:agentic-ai` returned 70 issues.

## Applied directly (camunda/connectors)

`connectors-core` was added to these 8 issues. No other labels were changed.

| Issue | Label | Why |
|---|---|---|
| #10594 ci: ASSIGN_URGENCY fails (Vault 403), ADD_TO_PROJECT fails to remove issues — https://github.com/camunda/connectors/issues/10594 | connectors-core | CI and issue automation are KTLO, which sits with Core by default. |
| #10589 [8.8] runtime image fails to start: No enum constant JsonMapper.JACKSON2 — https://github.com/camunda/connectors/issues/10589 | connectors-core | Connector runtime environment start-up bug. |
| #10578 Runtime answers HTTP 500 for client errors — https://github.com/camunda/connectors/issues/10578 | connectors-core | Webhook and inbound request handling in the runtime. |
| #10577 Webhook HTTP 500 for form-urlencoded with charset — https://github.com/camunda/connectors/issues/10577 | connectors-core | Webhook protocol connector execution. |
| #10576 Webhook JWT auth rejects valid token with '-' or '_' — https://github.com/camunda/connectors/issues/10576 | connectors-core | Webhook protocol connector execution. |
| #10572 Slack: documents on "Post message" never appear — https://github.com/camunda/connectors/issues/10572 | connectors-core | Backend bug with no UI or template part, so it is KTLO and goes to Core by default. |
| #10565 Remove Java4Shell Base64 intrinsics mitigation — https://github.com/camunda/connectors/issues/10565 | connectors-core | CVE follow-up on runtime launchers is KTLO. |
| #10560 Docker: move 8.10 images from JDK 26 to JDK 25 LTS — https://github.com/camunda/connectors/issues/10560 | connectors-core | Runtime image maintenance is KTLO. |

## Recommended (no write access to these repos)

This session can only write to camunda/connectors and camunda/team-connectors. These recommendations are based on each issue's title and body from search. Comments in these repos could not be read.

| Issue | Recommended label | Why |
|---|---|---|
| camunda-hub#31490 Show a credential as not deployed in the Hub UI — https://github.com/camunda/camunda-hub/issues/31490 | connectors-experience | Credentials manager frontend (UI only). |
| camunda#68443 Read id and version tag from .py/.js headers (managed scripts) — https://github.com/camunda/camunda/issues/68443 | connectors-core | Engine-level resource model for managed scripts that the Connectors runtime executes (camunda/connectors#9902). |
| camunda-docs#7416 Improve custom connector docs — https://github.com/camunda/camunda-docs/issues/7416 | connectors-core | Docs for building connectors (build-connector, SDK). |
| sap-connectors#23 Adjust SAP connectors to new best practices — https://github.com/camunda/sap-connectors/issues/23 | connectors-experience | About the Element Template generator DSL (TemplateDiscriminatorProperty, TemplateSubType). |
| product-hub#3731 Metadata Lookup — https://github.com/camunda/product-hub/issues/3731 | connectors-experience | Pickers and auto-completion while configuring connectors in the modeller, which is connector-configuration UX. |
| product-hub#3051 Auto-Scaling Connector Runtime Based on Load — https://github.com/camunda/product-hub/issues/3051 | connectors-core | Connector runtime environment. |
| camunda#34321 Update Connectors 8.7/8.6 to use backported process definitions API — https://github.com/camunda/camunda/issues/34321 | connectors-core | Runtime's inbound process definition search. |
| documentation-team#481 Improve Self-Managed Setup Documentation — https://github.com/camunda/documentation-team/issues/481 | connectors-core | Docs for setting up the Connectors runtime. |
| connector-sdk#484 Simplify object mapping, validation and secret handling — https://github.com/camunda/connector-sdk/issues/484 | connectors-core | SDK. |
| connector-sdk#481 Experiment: Connector SDK Core refactoring — https://github.com/camunda/connector-sdk/issues/481 | connectors-core | SDK. |
| connector-sdk#294 C# implementation of the Connector SDK — https://github.com/camunda/connector-sdk/issues/294 | connectors-core | SDK. |
| connector-sdk#180 Support job failure with backoff — https://github.com/camunda/connector-sdk/issues/180 | connectors-core | SDK and runtime job handling. |
| product-hub#1262 [Internal] M2M Token Expenses — https://github.com/camunda/product-hub/issues/1262 | connectors-core | Runtime authentication against the cluster. |
| product-hub#1253 Inbound Webhook: more auth and HTTP methods — https://github.com/camunda/product-hub/issues/1253 | connectors-core | Webhook protocol connector. |
| product-hub#1031 Inbound Connectors support for helm charts — https://github.com/camunda/product-hub/issues/1031 | connectors-core | Runtime environment deployment. |
| product-hub#846 Helm charts for Outbound OOTB Connectors — https://github.com/camunda/product-hub/issues/846 | connectors-core | Runtime environment deployment. |
| product-hub#784 GraphQL Connector — https://github.com/camunda/product-hub/issues/784 | connectors-core | GraphQL protocol connector. |
| connector-runtime-docker#32 Conditionally push latest docker tag — https://github.com/camunda/connector-runtime-docker/issues/32 | connectors-core | Runtime image release is KTLO. |
| product-hub#436 Update REST + SendGrid Connector to use dynamic templates — https://github.com/camunda/product-hub/issues/436 | connectors-experience | Element templates and connector-configuration UX. |

## Left alone (no clear single fit)

- connectors#10557 Orchestration Cluster connector: add BPMN XML operation. It needs both an element template change and a runtime change. The AI-agent motivation is only an example.
- team-connectors#1332, #1282: weekly meeting issues, not product work.
- camunda#63491, c4-connectors#72: Connector Manage & Run. The handbook says to treat this as ambiguous until the handover is done.
- product-hub#3506 (OAuth flow), #3433 (Connection Validation), c8ctl#720 (credential CLI): each covers both the Connection primitive (Core) and the credentials UI (Experience).
- camunda-docs#6800: the hybrid-mode guide is about runtime settings but sits under use-connectors docs, which Experience owns.
- product-hub#3606, camunda#40365, camunda#43375: FEEL evaluation in the engine, not connector scope.
- camunda-hub#17267, #9255: duplicate webhook ID behaviour across the Web Modeler and the runtime.
- product-hub#3016, #3015, #2717, #2884, #1515, #817: these span the modeller, the engine and the runtime.
- product-hub#2886, #2543, #2224, #2120: AI-related connectors, but none is about agent decision-making, so not clearly agentic-ai.
- New OOTB connector epics that the handbook does not assign: product-hub#3223, #1874, #1393, #1374, #1157, #1017, #843, #765, #467, #465, #462, #403, #321, #239, #237, #190, #189, #43.

Note: many of the product-hub epics from 2022 and 2023 look shipped (for example Kafka, SQS, Lambda, UiPath). Consider archiving them from the board instead of labelling them.
