# Connectors Experience: Easy Wins Scoping Pass

**Date:** 2026-08-03
**Board:** https://github.com/orgs/camunda/projects/23/views/17
**Filter used:** `is:issue state:open label:connectors-experience no:assignee repo:camunda/connectors`
**Issues matched:** 8 (all processed, no cap applied)

Purpose: identify issues the Pod Lead could pick up and drive to completion working directly with Claude, without needing a specialist engineer. This is a scoping pass only. Nothing was posted, changed or assigned on GitHub.

## Verdict at a glance

| Issue | Title | Class |
|---|---|---|
| #7083 | Migrate remaining AWS connectors to use `AwsClientSupport` | 🟢 GREEN |
| #7874 | feat(salesforce): support Salesforce Composite API | 🟢 GREEN |
| #7144 | Adjust element templates and validator to changes in the json schema | 🟡 AMBER |
| #7137 | Evaluate creating OOTB connector for AWS Transcribe | 🟡 AMBER |
| #6739 | Add custom HTTP headers support to the SAP OData connector | 🟡 AMBER |
| #6713 | Allow slack intermediate catch event to correlate across process definitions | 🔴 RED |
| #6155 | Upgrade Box SDK from 4.x to 10.x | 🔴 RED |
| #5851 | Generate Camunda Connector from OpenAPI spec | 🔴 RED |

Recommended order to start: **#7083 first** (mechanical, reference implementation already in the tree), then **#7874**, then the rules-only slice of **#7144**.

---

**Issue:** #7083 Migrate remaining AWS connectors to use `AwsClientSupport` from `aws-base` — https://github.com/camunda/connectors/issues/7083
**Classification:** 🟢 GREEN
**Summary:** Replace hand-rolled AWS SDK v2 client construction in six AWS connectors with the shared `AwsClientSupport` helper already extracted into `aws-base`.
**Connector source:** https://github.com/camunda/connectors/tree/main/connectors/aws

The helper to adopt:
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java

Reference implementation, already migrated:
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock-knowledgebase/src/main/java/io/camunda/connector/aws/bedrock/knowledgebase/BedrockKnowledgeBaseConnectorFunction.java

The six files still building clients manually:
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-lambda/src/main/java/io/camunda/connector/awslambda/AwsLambdaSupplier.java
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-sqs/src/main/java/io/camunda/connector/common/suppliers/DefaultAmazonSQSClientSupplier.java
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-sns/src/main/java/io/camunda/connector/sns/suppliers/SnsClientSupplier.java
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-eventbridge/src/main/java/io/camunda/connector/aws/eventbridge/AwsEventBridgeClientSupplier.java
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock/src/main/java/io/camunda/connector/aws/bedrock/core/BedrockExecutor.java
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-s3/src/main/java/io/camunda/connector/aws/s3/core/S3Executor.java

**Docs coverage:** Internal refactor with no user-facing docs surface. Nearest context pages for the affected connectors: https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-s3/ and https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/

**Reason for classification:** Verified against the current tree: `AwsClientSupport` exists and exposes both `createClient()` and `configureClient()`, one connector is already migrated as a copyable reference, and all six listed files still contain zero references to the helper. The epic owner has written the constraints out explicitly in the issue comments, so there is no judgement call left to make.

**💬 Suggested starting prompt:**
> You are working on issue #7083 from camunda/connectors: https://github.com/camunda/connectors/issues/7083
> **Issue:** Migrate remaining AWS connectors to use `AwsClientSupport` from `aws-base`
> **What's needed:** Migrate the six connectors that still construct AWS SDK v2 clients by hand (`aws-lambda`, `aws-sqs`, `aws-sns`, `aws-eventbridge`, `aws-bedrock`, `aws-s3`) onto `AwsClientSupport.createClient()` / `configureClient()`. `aws-bedrock-knowledgebase` is already done and is your reference. Leave `aws-dynamodb` alone, it is still on AWS SDK v1 and is covered separately by issue #7973.
> **Connector source:**
> - Helper to adopt: https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java
> - Reference implementation: https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock-knowledgebase/src/main/java/io/camunda/connector/aws/bedrock/knowledgebase/BedrockKnowledgeBaseConnectorFunction.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-lambda/src/main/java/io/camunda/connector/awslambda/AwsLambdaSupplier.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-sqs/src/main/java/io/camunda/connector/common/suppliers/DefaultAmazonSQSClientSupplier.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-sns/src/main/java/io/camunda/connector/sns/suppliers/SnsClientSupplier.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-eventbridge/src/main/java/io/camunda/connector/aws/eventbridge/AwsEventBridgeClientSupplier.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-bedrock/src/main/java/io/camunda/connector/aws/bedrock/core/BedrockExecutor.java
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-s3/src/main/java/io/camunda/connector/aws/s3/core/S3Executor.java
> **Relevant docs:** Internal refactor, no docs change needed. Context: https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-s3/
> **Acceptance criteria:**
> 1. All six connectors resolve credentials, region and optional endpoint override through `AwsClientSupport` rather than inline builder code.
> 2. The per-connector `*ClientSupplier` interfaces stay in place. They are the unit-test mocking seam the AWS SDK v2 epic's test plan depends on. Consolidate their internals only, do not delete the seam.
> 3. `aws-dynamodb` is untouched.
> 4. Existing unit and integration tests still pass.
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
> Two things to raise rather than silently absorb: `aws-s3` and `aws-bedrock` currently have no endpoint override support at all, so routing them through `AwsClientSupport` adds it as a behaviour change, and neither has e2e coverage (LocalStack e2e covers lambda/sqs/sns/eventbridge only). Call both out explicitly in your summary.

---

**Issue:** #7874 feat(salesforce): support Salesforce Composite API — https://github.com/camunda/connectors/issues/7874
**Classification:** 🟢 GREEN
**Summary:** Add a Composite Request operation to the Salesforce connector so several subrequests run atomically in one HTTP round trip, with later subrequests able to reference earlier results.
**Connector source:** https://github.com/camunda/connectors/tree/main/connectors/salesforce

- Template to edit: https://github.com/camunda/connectors/blob/main/connectors/salesforce/element-templates/salesforce-connector.json
- Frozen version snapshots: https://github.com/camunda/connectors/tree/main/connectors/salesforce/element-templates/versioned

**Docs coverage:** https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/ (documents only SOQL Query and sObject Create/Get/Update/Delete today, which matches the tree, so the gap the issue describes is real and current)

**Reason for classification:** Confirmed the Salesforce connector has no Java module at all. It is a hand-authored element template layered on the generic REST connector (`io.camunda:http-json:1`), so this is JSON template work following four existing operation patterns in the same file, with no runtime code, no auth changes and no shared infrastructure in scope. The issue also spells the required fields out in a table.

**💬 Suggested starting prompt:**
> You are working on issue #7874 from camunda/connectors: https://github.com/camunda/connectors/issues/7874
> **Issue:** feat(salesforce): support Salesforce Composite API
> **What's needed:** Add a new "Composite Request" operation to the Salesforce connector element template, wrapping `POST /services/data/vXX.0/composite`. Fields: `allOrNone` (boolean, roll back all subrequests if any fails), `collateSubrequests` (boolean, optional), and `compositeRequest` (the list of subrequests, each with `method`, `url`, `referenceId` and optional `body`). The connector output must surface the full `compositeResponse` array so FEEL expressions can pick out individual subrequest results by `referenceId`.
> **Connector source:**
> - https://github.com/camunda/connectors/blob/main/connectors/salesforce/element-templates/salesforce-connector.json
> - https://github.com/camunda/connectors/tree/main/connectors/salesforce/element-templates/versioned
> **Relevant docs:**
> - https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/
> - https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/defining-templates/
> - Salesforce Composite API reference is linked from the issue body.
> **Important context, verified in the tree:** There is no Salesforce Java module. `connectors/salesforce` is template-only and is not a Maven module. The template's `zeebe:taskDefinition` type is the generic REST connector `io.camunda:http-json:1`, so the new operation is modelled purely as template properties, conditions and bindings, exactly like the existing SOQL Query and sObject operations in the same file. Read those four first and copy their shape.
> **Acceptance criteria:**
> 1. New operation selectable from the existing `salesforceInteractionType` dropdown, alongside "SOQL Query" and "sObject records".
> 2. `allOrNone`, optional `collateSubrequests` and `compositeRequest` fields present, shown only when the composite operation is selected, and bound so they produce the correct request body and target URL.
> 3. Response surfaces the whole `compositeResponse` array.
> 4. Template `version` is bumped (currently 5) and the previous version is snapshotted into `element-templates/versioned/` following the existing `salesforce-connector-1..4.json` convention.
> 5. The element template validator passes. Note the repo has rules that police exactly this: `CurrentVersionBumpRule`, `VersionedTemplateConsistencyRule` and `ElementTemplateVersionConsistencyRule` in https://github.com/camunda/connectors/tree/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
> One modelling decision to flag rather than assume: element templates have no repeating-group primitive, so `compositeRequest` most likely becomes a single FEEL-expression field taking a list of context values. Say so explicitly and show the FEEL a modeller would type.

---

**Issue:** #7144 Adjust element templates and validator to changes in the json schema — https://github.com/camunda/connectors/issues/7144
**Classification:** 🟡 AMBER
**Summary:** Add three new element-template validator rules for patterns the JSON schema has deprecated, then clean up every existing violation so the rules can land as ERROR without breaking CI.
**Connector source:** https://github.com/camunda/connectors/tree/main/element-template-generator/validator

- Rules package to extend: https://github.com/camunda/connectors/tree/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule
- Interface to implement: https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/core/Rule.java
- Where rules are registered: https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/command/ValidatorCommand.java
- Closest existing patterns to copy: https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule/EmptyGroupRule.java and https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule/DefaultValueInChoicesRule.java

**Docs coverage:**
- https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/defining-templates/ (covers `value`, `generatedValue`, the `Hidden` type and optional bindings)
- https://docs.camunda.io/docs/components/modeler/element-templates/template-example/

Docs confirm the `Hidden` plus `generatedValue` pattern and that optional bindings exist to avoid persisting empty values, but no docs page states the `zeebe:input` plus `optional: true` combination is deprecated. For that rule the schema and validator are the only source of truth.

**Reason for classification:** The rule-writing half is genuinely easy: a two-method interface, 25 existing rules to copy from, and `runSingleFileRules` already skips frozen `versioned/` templates so new rules will not fire on historical snapshots. The cleanup half is what pulls it to amber, both because it is a semantics-affecting codemod across roughly 100 connector modules and because the counts in the issue are now well out of date.

**Blocked by / missing info:**
1. **The issue's violation counts are stale.** Measured against the current tree today: rule 1 (`Hidden` with neither `value` nor `generatedValue`) has **167** occurrences across 159 files, not 52. Rule 2 (`optional: true` on a `zeebe:input` binding) has **4277** occurrences across 423 files, not 807. Rule 3 (`feel: "optional"` with `editable: false`) is still at **0**, so that one is free.
2. **Most of that is frozen history, and the split needs confirming before anyone starts.** Of the 4277 rule-2 hits, 3025 are inside `versioned/` directories, leaving 739 in current templates and 513 in hybrid templates. Rule 1 splits the same way: 108 versioned, 34 current, 25 hybrid. Since `runSingleFileRules` skips versioned files, the real cleanup surface is current plus hybrid, so about **1252** for rule 2 and **59** for rule 1. Worth confirming that reading of intent before touching anything, because it is the difference between a day and a fortnight.
3. **The generated-versus-hand-written split makes it smaller than it looks.** Current and hybrid templates are generated from `@TemplateProperty` annotations, and there are only **253** `optional = true` annotations across 101 Java files. Dropping those regenerates the bulk of the 1252. Only genuinely hand-written templates need direct JSON edits.
4. **One case needs an engineering decision, not a codemod.** The agentic-ai "agent" orphan plumbing traces to PR #6824, where an empty hidden `zeebe:input` binding is used deliberately to scope a variable as local so it does not propagate to outer scopes. That is load-bearing behaviour, so "clean it up" needs an owner's call. Check with the author of that PR or the agentic-ai owner.
5. **Confirm the fix value with the modeler team.** The linked #ask-modeler thread (https://camunda.slack.com/archives/C0693F1NFK5/p1779176016687579) records that the correct fix for hidden inputs is `"value": ""` and specifically not `"value": null`, since null reproduces the same missing-source edge case the rule exists to catch. Upstream tracking: https://github.com/camunda/element-templates-json-schema/issues/181

**Suggested split:** the three rules plus rule 3 needing no cleanup is a clean, self-contained first PR, landed as WARN. Do that with Claude, then treat each cleanup wave as its own PR and flip to ERROR last. Only the cleanup waves need the decisions above.

**💬 Suggested starting prompt:**
> You are working on issue #7144 from camunda/connectors: https://github.com/camunda/connectors/issues/7144
> **Issue:** Adjust element templates and validator to changes in the json schema
> **What's needed:** Scope this run to the validator rules only, not the cleanup. Add three new single-file rules to the element template validator: `hidden-requires-value` (a `Hidden` property must specify `value` or `generatedValue`), `optional-not-on-zeebe-input` (`optional: true` is invalid on a `zeebe:input` binding), and `feel-optional-not-with-editable-false` (`feel: "optional"` cannot be combined with `editable: false`). Register them at WARN severity for now, not ERROR, because the existing violations have not been cleaned up yet.
> **Connector source:**
> - https://github.com/camunda/connectors/tree/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule
> - https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/core/Rule.java
> - https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/command/ValidatorCommand.java
> - Patterns to copy: https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule/EmptyGroupRule.java and https://github.com/camunda/connectors/blob/main/element-template-generator/validator/src/main/java/io/camunda/connector/validator/rule/DefaultValueInChoicesRule.java
> **Relevant docs:** https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/defining-templates/ and https://docs.camunda.io/docs/components/modeler/element-templates/template-example/. Note no docs page documents the `zeebe:input` plus `optional: true` deprecation, so treat the JSON schema and validator as the source of truth there.
> **Acceptance criteria:**
> 1. Three new rules in the `rule/` package implementing `Rule`, each with a `*RuleTest.java` alongside the existing rule tests.
> 2. Registered in the `runSingleFileRules` list in `ValidatorCommand.java`, at WARN.
> 3. Rule IDs resolve via the default `id()` derivation to exactly `hidden-requires-value`, `optional-not-on-zeebe-input` and `feel-optional-not-with-editable-false`. Check the kebab-case derivation in `Rule.java` and name the classes so it lands correctly.
> 4. Findings point at the offending property with a useful JSON pointer, consistent with existing rules. See `JsonPointers.java` in the `core/` package.
> 5. Run the validator over the repo and report the violation counts each new rule produces, split by current, hybrid and `versioned/` templates.
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push. Do not attempt the cleanup of existing violations in this run, and do not modify any connector element templates or `@TemplateProperty` annotations.
> **Context worth knowing:** `runSingleFileRules` already skips files inside `versioned/` directories, so your rules will not fire on frozen historical snapshots. Expect roughly 59 hits for `hidden-requires-value` and roughly 1252 for `optional-not-on-zeebe-input` across current plus hybrid templates, and 0 for the third rule. If your numbers come out very different from that, stop and say so rather than adjusting the rules to match.

---

**Issue:** #7137 Evaluate creating OOTB connector for AWS Transcribe — https://github.com/camunda/connectors/issues/7137
**Classification:** 🟡 AMBER
**Summary:** Decide whether an out-of-the-box AWS Transcribe connector belongs in the product, so processes can turn audio and video into text transcripts without custom code.
**Connector source:** No AWS Transcribe module exists. Closest structural references, both AWS ML services producing structured output from documents:
- https://github.com/camunda/connectors/tree/main/connectors/aws/aws-comprehend
- https://github.com/camunda/connectors/tree/main/connectors/aws/aws-textract
- https://github.com/camunda/connectors/tree/main/connectors/aws/aws-s3 (audio and video input would most likely arrive from S3)
- https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java (shared client and auth support)

**Docs coverage:** No docs exist for AWS Transcribe, as there is no connector. Conventions for the sibling AWS ML connectors, including the sync versus async execution split that Transcribe would also need: https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-comprehend/ and https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-sagemaker/

**Reason for classification:** The deliverable here is a product decision rather than code, and the decision-maker named in the issue is the Pod Lead, so it is actionable without a specialist. It stays amber because the body contradicts itself and there is nothing to implement until the decision lands.

**Blocked by / missing info:** The issue body says "the specific integration/service for the connector was not mentioned in the thread" while the title names AWS Transcribe, so the original ask is not fully pinned down. Read the originating thread (https://camunda.slack.com/archives/C02JLRNQQ05/p1777981960864569?thread_ts=1777981960.864569&cid=C02JLRNQQ05) to confirm Transcribe is really what was requested and to recover the underlying use case, which the ticket never records. Also worth noting this is a decision Calvin owes himself, per the issue's own action item, so it needs no external unblocking beyond that. If the answer is yes, the async job pattern (start job, poll or callback, retrieve result) is the main design question, and `aws-comprehend` already solves it.

**💬 Suggested starting prompt:**
> You are working on issue #7137 from camunda/connectors: https://github.com/camunda/connectors/issues/7137
> **Issue:** Evaluate creating OOTB connector for AWS Transcribe
> **What's needed:** Produce a written evaluation, not an implementation, of whether Camunda should ship an out-of-the-box AWS Transcribe connector for converting audio and video (typically from S3) into text transcripts. Cover: what the connector would need to expose, how it would fit the existing AWS connector catalogue, the async job pattern it would require, rough implementation size, and a recommendation.
> **Connector source:** No Transcribe module exists yet. Study these as structural references:
> - https://github.com/camunda/connectors/tree/main/connectors/aws/aws-comprehend
> - https://github.com/camunda/connectors/tree/main/connectors/aws/aws-textract
> - https://github.com/camunda/connectors/tree/main/connectors/aws/aws-s3
> - https://github.com/camunda/connectors/blob/main/connectors/aws/aws-base/src/main/java/io/camunda/connector/aws/AwsClientSupport.java
> **Relevant docs:** https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-comprehend/ and https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-sagemaker/
> **Acceptance criteria:** A recommendation document covering proposed operations and fields, the sync versus async execution split and how `aws-comprehend` already handles it, how transcript output would flow through the Camunda document store, effort estimate, and a clear build or do-not-build recommendation with reasoning.
> **Constraints:** Follow existing code style if you write any illustrative code. Flag anything uncertain rather than guessing. Do not commit or push. Do not build the connector in this run.
> **Note:** The issue body contradicts its own title, saying the specific service was never named in the originating thread. Flag that the underlying use case is unrecorded and that this should be confirmed before any build decision is treated as final.

---

**Issue:** #6739 Add custom HTTP headers support to the SAP OData connector — https://github.com/camunda/connectors/issues/6739
**Classification:** 🟡 AMBER
**Summary:** Add a FEEL Map `headers` field to the SAP OData connector, for both standard and `$batch` requests, bringing it in line with the REST connector.
**Connector source:** **Not in this repository.** I searched the whole `camunda/connectors` tree and there is no SAP module of any kind. The source lives in the separate `camunda/sap-connectors` repo, which this session has no access to, so **I cannot give you a verified direct link to the module or its files.** Treat the class names circulating on the issue (`ODataRequestExecutor`, `ODataBatchRequestExecutor`, `CommonExecutor`, `sap-odata-connector.json`, package `io.camunda.connector.sap.odata`) as unverified until someone opens that repo and checks.

The pattern to mirror does live here, and this link is verified:
- https://github.com/camunda/connectors/blob/main/connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonRequest.java

**Docs coverage:** https://docs.camunda.io/docs/components/camunda-integrations/sap/odata-connector/ (the current SAP OData page, which documents no header support, consistent with the gap described) and https://docs.camunda.io/docs/components/camunda-integrations/sap/sap-integration/

**Reason for classification:** The feature itself is small and well understood, and there is a clean in-repo pattern to copy. It is amber purely on logistics: the code is in a repo I could not open to verify anything, and a version decision is still open with an external dependency.

**Blocked by / missing info:**
1. **Repo access.** Confirm you can push to `camunda/sap-connectors` and re-verify every file path before starting. Nothing about the SAP source in this report is verified.
2. **Version and backport decision is still unresolved after four months.** The customer (Zespri International) is on 8.6, which used the pre-GA alpha template. The thread (https://camunda.slack.com/archives/C02AWA0RF8A/p1774533707808649?thread_ts=1773884633.039939&cid=C02AWA0RF8A) discusses whether this needs backporting to 8.8 or 8.9 or can simply target 8.10, and records no decision, pending an account-team upgrade planning session. The original question from sbuettner on the issue was never answered. Get that answer first, since it changes the shape of the work. Claudia S. (CSM) was named as the person in active talks with the account.
3. **Scope question worth settling up front:** whether headers apply per-`$batch`-request, per-changeset, or both, since OData batching nests requests and the issue asks for both standard and batch without saying which level.

**💬 Suggested starting prompt:**
> You are working on issue #6739 from camunda/connectors: https://github.com/camunda/connectors/issues/6739
> **Issue:** Add custom HTTP headers support to the SAP OData connector
> **What's needed:** Add a `headers` field using the FEEL Map data type to the SAP OData connector's element template and request model, applied to both standard OData requests and `$batch` or changeset requests, mirroring how the REST connector merges arbitrary key-value pairs into the outgoing request. Target cases are `If-Match` for optimistic concurrency on PATCH/PUT/DELETE and custom SAP-specific headers.
> **Connector source:** **The SAP OData connector is NOT in camunda/connectors.** It lives in the separate `camunda/sap-connectors` repository. Your first step is to open that repo and locate the OData connector's request model, executors and element template yourself. Do not trust any path or class name given to you second-hand, including `ODataRequestExecutor`, `ODataBatchRequestExecutor`, `CommonExecutor` or `sap-odata-connector.json`, all of which are unverified. Report the real paths you find before you change anything.
> The pattern to mirror is verified and does live in camunda/connectors: https://github.com/camunda/connectors/blob/main/connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonRequest.java
> **Relevant docs:** https://docs.camunda.io/docs/components/camunda-integrations/sap/odata-connector/ and https://docs.camunda.io/docs/components/camunda-integrations/sap/sap-integration/
> **Acceptance criteria:**
> 1. A `headers` field accepting a FEEL Map on the element template, optional, consistent in labelling and grouping with the REST connector's equivalent.
> 2. Headers merged into outgoing standard OData requests.
> 3. Headers merged into `$batch` requests, with the nesting level stated explicitly (outer batch request, individual changeset request, or both).
> 4. Tests covering both paths, including an `If-Match` case.
> **Constraints:** Follow existing code style. Flag anything uncertain rather than guessing. Do not commit or push.
> **Two things to stop and ask about rather than decide yourself:** the target version, since the requesting customer is on 8.6 and no decision was ever recorded on whether this backports to 8.8 or 8.9 or simply targets 8.10; and the batch nesting level in criterion 3 if the existing code does not make the answer obvious.

---

## Not a fit right now

- **#6713 Allow slack intermediate catch event to correlate across process definitions** — needs deep inbound-connector runtime knowledge, since the real constraint is the shared webhook activation, correlation and deduplication model rather than anything Slack-specific, and it changes behaviour for every inbound connector.
- **#6155 Upgrade Box SDK from 4.x to 10.x** — the premise is stale (the repo is on 5.14.1, not 4.x, with a deliberate Renovate pin to the v5 line) and v10 is a full generated-API rewrite with confirmed feature gaps and an unresolved upstream issue, box-java-sdk#1877, that already killed one attempt.
- **#5851 Generate Camunda Connector from OpenAPI spec** — acceptance criteria are ambiguous across three different readings (use the existing `congen-cli` tool, close gaps in `openapi-parser`, or fix Web Modeler's importer, the last of which is in another codebase), with no definition of done.

---

## Notes on this run

**Nothing mid-flight appears to have slipped through the filter.** The filter falls back to open plus unassigned plus labelled, because this session has repo-level access only and cannot read the project board's Status field. I checked each of the 8 for signs of active work and found none carrying a PR or an in-progress owner. Two are worth a second glance before you pick anything up:

- **#5851** may have a community contributor mid-flight. vringar asked azan-baloch to try the `congen-cli` route back in February and there has been no reply since. Check before anyone else starts on it.
- **#7083** has an explicit sequencing preference from the epic owner: ideally land after #7968 (golden result-shape fixtures) so a client-wiring regression has a safety net. Not a hard blocker, but confirm where #7968 stands first.

**Two tool limitations affected this run, both worth fixing before the next one.**

1. **The Camunda Docs MCP server is not authorised**, so I could not use it. It needs authorising from claude.ai connector settings, and this session is non-interactive so it cannot be done from here.
2. **`docs.camunda.io` is blocked by this session's egress policy** (the proxy returns 403 on CONNECT), so I could not fetch any docs page directly either.

Every docs URL above was therefore confirmed through web search result listings, which return real titles and URLs plus page summaries, rather than by loading the pages. They are real URLs, not guesses, but I could not verify anchor-level fragments, so I have linked pages rather than anchors throughout. One specific consequence: the `template-properties` page URL that appears in earlier enrichment comments on #7144 could not be confirmed, so I have used the confirmed `defining-templates` page instead, which covers the same material.

Everything about the `camunda/connectors` tree itself, including all file paths, the six unmigrated AWS files, the Salesforce template's structure and version, the validator's rule registration and versioned-skip behaviour, and all violation counts, was verified directly against the working copy at commit `17bc53f`.
