# Connectors Experience — Easy Wins Scoping (2026-07-27)

Filter used: `is:issue state:open label:connectors-experience no:assignee` on `camunda/connectors` (project board API not available in this session — see caveats in the routine). 9 issues matched. None of them looked like mid-flight work slipping through the unassigned filter on manual review.

---
**Issue:** #7874 feat(salesforce): support Salesforce Composite API — https://github.com/camunda/connectors/issues/7874
**Classification:** 🟢 GREEN
**Summary:** Add a new "Composite Request" operation to the Salesforce connector so multiple Salesforce subrequests (e.g. create Account → Contact → Opportunity) can run atomically in one HTTP round trip via Salesforce's Composite API.
**Connector source:** https://github.com/camunda/connectors/tree/main/connectors/salesforce/element-templates (specifically `salesforce-connector.json`; versioned templates live under `connectors/salesforce/element-templates/versioned`). The Salesforce connector has no dedicated Java module — it's a hand-authored element template over the generic REST connector (`io.camunda:http-json:1`).
**Docs coverage:** https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/ and https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/#sobject-records
**Reason for classification:** Clear, self-contained acceptance criteria (three named fields, one new operation), no Java code involved, and it follows the existing SOQL Query / sObject operation pattern already in the same template file.

**💬 Suggested starting prompt:**
> You are working on issue #7874 from camunda/connectors: https://github.com/camunda/connectors/issues/7874
> **Issue:** feat(salesforce): support Salesforce Composite API
> **What's needed:** Add a new "Composite Request" operation to the Salesforce element template for Salesforce's Composite API (`POST /services/data/vXX.0/composite`), with `allOrNone` (boolean), optional `collateSubrequests` (boolean), and `compositeRequest` (list of subrequests with `method`, `url`, `referenceId`, optional `body`). Surface the full `compositeResponse` array in the output so FEEL expressions can reference subrequest results by `referenceId`.
> **Connector source:** https://github.com/camunda/connectors/blob/main/connectors/salesforce/element-templates/salesforce-connector.json
> **Relevant docs:** https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/ and Salesforce's own Composite API reference (https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite.htm, linked from the issue).
> **Acceptance criteria:** New operation appears alongside SOQL Query / sObject operations in the modeler; fields match the table in the issue; response mapping exposes `compositeResponse`.
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
---

---
**Issue:** #5851 Generate Camunda Connector from OpenAPI spec — https://github.com/camunda/connectors/issues/5851
**Classification:** 🟢 GREEN
**Summary:** Automatically generate a Camunda outbound connector/element template from the Orchestration Cluster REST API's own OpenAPI spec, as the recommended alternative to hand-written connectors (relevant given the Operate connector deprecation).
**Connector source:** https://github.com/camunda/connectors/tree/main/element-template-generator/congen-cli (CLI entry point) and https://github.com/camunda/connectors/tree/main/element-template-generator/openapi-parser (the generator implementation).
**Docs coverage:** https://docs.camunda.io/docs/components/modeler/web-modeler/element-templates/element-template-generator/ and https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/#getting-started
**Reason for classification:** The direction is already agreed and a maintainer confirmed `congen-cli`'s `openapi-outbound` generator is the intended tool (see issue comment history). The task is scoped to running it against the Orchestration Cluster's `rest-api.yaml` and identifying/fixing concrete gaps (e.g. relative `$ref` handling) rather than an open-ended design decision.

**💬 Suggested starting prompt:**
> You are working on issue #5851 from camunda/connectors: https://github.com/camunda/connectors/issues/5851
> **Issue:** Generate Camunda Connector from OpenAPI spec
> **What's needed:** Run `congen-cli`'s `openapi-outbound` generator against the Orchestration Cluster REST API's own OpenAPI spec (`rest-api.yaml`) and check whether it already produces a usable element template. Document and, where feasible, fix concrete gaps — e.g. relative `$ref` handling (Web Modeler's own importer reportedly fails on these), spec coverage, or manual refinement steps still needed.
> **Connector source:** https://github.com/camunda/connectors/tree/main/element-template-generator/congen-cli and https://github.com/camunda/connectors/tree/main/element-template-generator/openapi-parser
> **Relevant docs:** https://docs.camunda.io/docs/components/modeler/web-modeler/element-templates/element-template-generator/, https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/#getting-started
> **Acceptance criteria:** A working element template generated from the OC spec via `congen-cli`, plus a short write-up of any gaps found and whether they were fixed or are out of scope.
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
---

---
**Issue:** #7083 Migrate remaining AWS connectors to use `AwsClientSupport` from `aws-base` — https://github.com/camunda/connectors/issues/7083
**Classification:** 🟡 AMBER
**Summary:** Migrate six AWS connectors (`aws-lambda`, `aws-sqs`, `aws-sns`, `aws-eventbridge`, `aws-bedrock`, `aws-s3`) that still build AWS SDK v2 clients manually onto the shared `AwsClientSupport.createClient()`/`configureClient()` helper, matching the pattern already used in `aws-bedrock-knowledgebase`.
**Connector source:** Helper: https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java · Reference migration: https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock-knowledgebase/src/main/java/io/camunda/connector/aws/bedrock/knowledgebase/BedrockKnowledgeBaseConnectorFunction.java · Targets: https://github.com/camunda/connectors/tree/main/connectors/aws/aws-lambda, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-sqs, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-sns, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-eventbridge, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-bedrock, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-s3
**Docs coverage:** No docs found for this area (internal refactor, not user-facing). For context on the connectors themselves: https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-lambda/, https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-s3/, https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/
**Reason for classification:** Mechanical and well-precedented (one reference migration already exists), but a later maintainer comment folded this into the wider AWS SDK v2 migration epic (camunda/product-hub#3581) with explicit sequencing/testing constraints — ideally after #7968 lands, and `aws-s3`/`aws-bedrock` currently have no e2e coverage while the migration also adds endpoint-override behaviour they don't have today.
**Blocked by / missing info:** Confirm #7968 (golden result-shape fixtures) has landed before starting, and get sign-off that adding endpoint-override support to `aws-s3`/`aws-bedrock` as a side effect is acceptable given the missing e2e coverage there. Worth a quick check with whoever owns the AWS SDK v2 migration epic first.

**💬 Suggested starting prompt:**
> You are working on issue #7083 from camunda/connectors: https://github.com/camunda/connectors/issues/7083
> **Issue:** Migrate remaining AWS connectors to use `AwsClientSupport` from `aws-base`
> **What's needed:** Migrate `aws-lambda` (`AwsLambdaSupplier`), `aws-sqs` (`DefaultAmazonSQSClientSupplier`), `aws-sns` (`SnsClientSupplier`), `aws-eventbridge` (`AwsEventBridgeClientSupplier`), `aws-bedrock` (`BedrockExecutor`), and `aws-s3` (`S3Executor`) onto `AwsClientSupport.createClient()`/`configureClient()`, following the pattern in `BedrockKnowledgeBaseConnectorFunction`. Keep each connector's `*ClientSupplier` interface intact (it's the unit-test mocking seam) — consolidate internals only. Leave `aws-dynamodb` out of scope (still SDK v1).
> **Connector source:** https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java (helper), https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock-knowledgebase/src/main/java/io/camunda/connector/aws/bedrock/knowledgebase/BedrockKnowledgeBaseConnectorFunction.java (reference)
> **Relevant docs:** N/A — internal refactor.
> **Acceptance criteria:** All six connectors build AWS SDK v2 clients via `AwsClientSupport`; existing unit tests still pass via the preserved `*ClientSupplier` seam; endpoint-override behaviour change on `aws-s3`/`aws-bedrock` is called out explicitly.
> **Constraints:** Follow existing code style. Check whether #7968 has landed first. Flag anything uncertain rather than guessing. Do not commit or push.
---

---
**Issue:** #6739 Add custom HTTP headers support to the SAP OData connector — https://github.com/camunda/connectors/issues/6739
**Classification:** 🟡 AMBER
**Summary:** Add a FEEL Map `headers` field to the SAP OData connector, for both standard and `$batch`/`changeset` requests, mirroring the REST connector's existing headers support.
**Connector source:** Not in this repository — the SAP OData connector lives in the separate `camunda/sap-connectors` repo (package `io.camunda.connector.sap.odata`, classes such as `ODataRequestExecutor`/`ODataBatchRequestExecutor`/`CommonExecutor`), which is outside this session's repo access. For the pattern to mirror, see this repo's REST connector: https://github.com/camunda/connectors/blob/main/connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonRequest.java
**Docs coverage:** https://docs.camunda.io/docs/components/camunda-integrations/sap/odata-connector/ (see especially the `$batch` request structure section) and https://docs.camunda.io/docs/components/connectors/protocol/rest/#request for the REST connector's headers behaviour to mirror.
**Reason for classification:** The ask itself is well defined and has a direct precedent to copy (REST connector's `headers` FEEL Map). But the actual code lives in a different repository this session can't reach, and the issue's own thread notes an unresolved version/backport decision (8.8 vs 8.9 vs 8.10) pending an account-team planning session.
**Blocked by / missing info:** Confirm access to `camunda/sap-connectors` before any implementation work starts, and get the backport/target-version decision from the account team (Zespri is currently on 8.6 pre-GA alpha template) — don't guess which version(s) to target.

**💬 Suggested starting prompt:**
> You are working on issue #6739 from camunda/connectors: https://github.com/camunda/connectors/issues/6739
> **Issue:** Add custom HTTP headers support to the SAP OData connector
> **What's needed:** Add a `headers` field (FEEL Map) to the SAP OData connector's element template and request model, applied to both standard OData requests and `$batch`/`changeset` requests, mirroring the REST connector's `headers` behaviour.
> **Connector source:** This work happens in `camunda/sap-connectors` (not `camunda/connectors`) — confirm you have access to that repo first. Reference pattern in this repo: https://github.com/camunda/connectors/blob/main/connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonRequest.java
> **Relevant docs:** https://docs.camunda.io/docs/components/camunda-integrations/sap/odata-connector/, https://docs.camunda.io/docs/components/connectors/protocol/rest/#request
> **Acceptance criteria:** Arbitrary key-value headers can be set on both single OData calls and batch/changeset requests, e.g. for `If-Match` concurrency control.
> **Constraints:** Follow existing code style. Confirm the target Camunda version(s) for this change with the account team before implementing — do not guess. Flag anything uncertain rather than guessing. Do not commit or push.
---

---
**Issue:** #7137 Evaluate creating OOTB connector for AWS Transcribe — https://github.com/camunda/connectors/issues/7137
**Classification:** 🟡 AMBER
**Summary:** Decide whether Camunda should build an out-of-the-box AWS Transcribe connector (audio/video-to-text), alongside the existing AWS ML/AI connectors.
**Connector source:** Closest structural references: https://github.com/camunda/connectors/tree/main/connectors/aws/aws-textract, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-comprehend (both async-job-pattern ML connectors), https://github.com/camunda/connectors/tree/main/connectors/aws/aws-s3 (likely audio/video source), https://github.com/camunda/connectors/tree/main/connectors/aws/aws-base (shared AWS auth/client support).
**Docs coverage:** https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-comprehend/ and https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/
**Reason for classification:** This is explicitly a product-fit decision, not an implementation task — the issue itself says it needs discussion with a named stakeholder to determine whether this fits the OOTB Connectors strategy. Claude can usefully research the async-job pattern and draft a recommendation, but can't make the product call.
**Blocked by / missing info:** A product-strategy decision from the connectors product owner (the issue names a specific Camunda stakeholder to check with) on whether AWS Transcribe fits OOTB strategy — this needs a conversation, not code.

**💬 Suggested starting prompt:**
> You are working on issue #7137 from camunda/connectors: https://github.com/camunda/connectors/issues/7137
> **Issue:** Evaluate creating OOTB connector for AWS Transcribe
> **What's needed:** Research how AWS Transcribe's API works (async job: start job → poll/callback → retrieve result) relative to Camunda's existing AWS ML/AI connectors (Comprehend, Textract, Bedrock, SageMaker), and draft a short recommendation on effort/shape if built, to support a product-fit decision.
> **Connector source:** https://github.com/camunda/connectors/tree/main/connectors/aws/aws-textract, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-comprehend, https://github.com/camunda/connectors/tree/main/connectors/aws/aws-s3
> **Relevant docs:** https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-comprehend/, https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/
> **Acceptance criteria:** A written recommendation (not code) covering: expected connector shape (sync vs async/job-polling), effort estimate, and how it'd fit alongside existing AWS ML connectors — for a human product decision.
> **Constraints:** This is a research/recommendation task, not implementation. Flag anything uncertain rather than guessing. Do not commit or push.
---

---
**Issue:** #154 Help tooltip for the response/output mapping in connectors — https://github.com/camunda/connectors/issues/154
**Classification:** 🟡 AMBER
**Summary:** Add an in-app tooltip/hint in the properties panel explaining the `status`/`headers`/`body` response-mapping variables and FEEL destructuring syntax for connector output mapping, starting with REST and Webhook connectors.
**Connector source:** https://github.com/camunda/connectors/blob/main/element-template-generator/annotations/src/main/java/io/camunda/connector/generator/java/annotation/DataExample.java, https://github.com/camunda/connectors/blob/main/element-template-generator/core/src/main/java/io/camunda/connector/generator/java/util/DataExampleModel.java, https://github.com/camunda/connectors/blob/main/element-template-generator/core/src/main/java/io/camunda/connector/generator/java/ClassBasedDocsGenerator.java, https://github.com/camunda/connectors/blob/main/connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonResult.java
**Docs coverage:** https://docs.camunda.io/docs/components/connectors/use-connectors/#variable-and-response-mapping, https://docs.camunda.io/docs/components/connectors/protocol/rest/#response
**Reason for classification:** The team already agreed a direction years ago (extend `@DataExample` with an optional `property` field, render the first output example in a tooltip) and the connectors-side pieces above are in this repo and clear to implement. But: (a) the blocking upstream dependency, bpmn-io/properties-panel#202 ("support tooltips for entries"), is closed/resolved on a quick check, so the UI capability this depends on may now exist — worth confirming its actual current state before assuming it's still blocked; and (b) a more recent architecture discussion is reportedly considering moving output mapping to the engine by default, which could change or obsolete this whole approach.
**Blocked by / missing info:** Confirm current status of bpmn-io/properties-panel tooltip support (and whether bpmn-js-element-templates#157 "dynamic output mappings" is relevant) — both outside this session's repo access. More importantly, check with the team whether the newer engine-level output-mapping direction supersedes this ticket before investing effort.

**💬 Suggested starting prompt:**
> You are working on issue #154 from camunda/connectors: https://github.com/camunda/connectors/issues/154
> **Issue:** Help tooltip for the response/output mapping in connectors
> **What's needed:** Add an optional `property` field to the `@DataExample` annotation, and wire it through so the first output example from a connector's output dataclass can be rendered as a tooltip hint in the properties panel — starting with the REST and Webhook connectors.
> **Connector source:** https://github.com/camunda/connectors/blob/main/element-template-generator/annotations/src/main/java/io/camunda/connector/generator/java/annotation/DataExample.java, https://github.com/camunda/connectors/blob/main/element-template-generator/core/src/main/java/io/camunda/connector/generator/java/util/DataExampleModel.java, https://github.com/camunda/connectors/blob/main/element-template-generator/core/src/main/java/io/camunda/connector/generator/java/ClassBasedDocsGenerator.java
> **Relevant docs:** https://docs.camunda.io/docs/components/connectors/use-connectors/#variable-and-response-mapping, https://docs.camunda.io/docs/components/connectors/protocol/rest/#response
> **Acceptance criteria:** `@DataExample` supports a `property` field; REST and Webhook output dataclasses use it; generated element templates surface an example in a way the properties panel can render as a tooltip.
> **Constraints:** Before implementing, check with the team whether the properties-panel tooltip capability (bpmn-io/properties-panel#202) has actually landed, and whether a newer engine-level output-mapping direction changes the scope of this work. Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
---

**Not a fit right now**
#7144 Adjust element templates and validator to changes in the json schema — new validator rules would need to land as ERROR across the shared element-template-generator/validator used by every connector, with 807+52 existing violations to clean up across ~101 modules first; core shared infra with wide blast radius.
#6713 Allow slack intermediate catch event to correlate across process definitions — touches core webhook correlation/deduplication logic shared by all inbound connectors; may already be partly achievable via existing message-correlation semantics (see the validated workaround in the related #6843), which needs a specialist to confirm before any code is written.
#6155 Upgrade Box SDK from 4.x to 10.x — a prior attempt (PR #6122) was abandoned because the v10 SDK is missing v4 functionality, with an open upstream gap (box/box-java-sdk#1877); needs specialist judgement on how to handle the gaps, not a straightforward version bump.
