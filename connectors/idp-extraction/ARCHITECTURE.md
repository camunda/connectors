---
architecture_md: 1
component: camunda/connectors/connectors/idp-extraction
system: { id: connectors, map: "camunda/connectors/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: Intelligent document processing (IDP) outbound connectors that classify documents and extract text, structured and unstructured data with OCR and LLM providers.
team: { name: connectors-idp, contact: unknown }
intake: { how: issue, template: FEATURE_REQUEST.md }
owns:
  - IDP extraction, classification, structured extraction and unstructured extraction connectors and their element templates
  - Provider clients for document text extraction (AWS Textract, Azure Document Intelligence, GCP Document AI, ABBYY Vantage, local PDFBox)
  - Provider clients for LLM-based extraction and classification (AWS Bedrock, Azure OpenAI, Azure AI Foundry, OpenAI, Vertex AI)
does_not_own:
  - { concept: "AI Agent, MCP and A2A connectors", owner: camunda/connectors/connectors/agentic-ai }
  - { concept: "Generic AWS authentication and client setup (aws-base)", owner: camunda/connectors/connectors }
  - { concept: "Document handling in the SDK and runtime (Document, DocumentCreationRequest)", owner: camunda/connectors }
depends_on:
  - id: sdk
    component: camunda/connectors
    kind: library
    contract: "connector-sdk/core, element-template-generator annotations"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: aws-base
    component: camunda/connectors/connectors
    kind: library
    contract: "connector-aws-base (AWS authentication and region model)"
    architecture: connectors/ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: langchain4j
    component: LangChain4j
    kind: external
    contract: "langchain4j-core, -bedrock, -azure-open-ai, -open-ai, -vertex-ai-gemini"
    workaround_policy: never
  - id: ocr-providers
    component: "AWS Textract, Azure Document Intelligence, GCP Document AI, ABBYY Vantage"
    kind: external
    contract: "Provider SDKs and HTTP APIs"
    workaround_policy: never
consumers:
  - { who: "Process modelers", via: "element-templates/ (classification, extraction, structured, unstructured)", promise: "Templates versioned; released versions under versioned/" }
  - { who: "Default runtime bundle", via: "apps/bundle/default-bundle dependency and ServiceLoader registration", promise: "Same repo and release" }
  - { who: "Camunda IDP application in Web Modeler", via: "IDP element templates", promise: "TODO(confirm) whether IDP templates are generated or applied by Web Modeler and what compatibility that requires" }
exposes:
  - { contract: "IDP connector types and element templates", spec: element-templates/, policy: "../../docs/connector-template-versioning.md" }
constraints:
  - { id: C1, name: "Generated templates and template versioning", hard: true, ref: ../../docs/connector-template-versioning.md }
  - { id: C2, name: "Provider parity across OCR and LLM providers", hard: false, ref: src/main/java/io/camunda/connector/idp/extraction/client }
  - { id: C3, name: "Documents passed as Document references, never raw bytes", hard: true, ref: ../../AGENTS.md }
decisions: ../../docs/adr/README.md
---

# Architecture — camunda/connectors/connectors/idp-extraction

Draft from the repository; not reviewed by its team.

## 1. Purpose
Lets processes classify documents and extract data from them using OCR services and LLMs. Part of the
[Camunda Connectors system](../../SYSTEM.md); built on the core platform
([root ARCHITECTURE.md](../../ARCHITECTURE.md)).

## 2. Ownership boundary
**Owns:** see `owns` above.

**Does not own (route here instead):**
| If you need… | It belongs to | How to ask |
|---|---|---|
| AI Agent or general LLM tool calling | connectors-agentic-ai, [agentic-ai](../agentic-ai/ARCHITECTURE.md) | Issue in this repo |
| AWS auth model shared with other AWS connectors | connectors-experience (`aws/aws-base`) | Issue in this repo |
| Document storage, `Document` type, runtime document handling | connectors-core, [root](../../ARCHITECTURE.md) | Issue in this repo |

## 3. Structure
One Maven module (`connector-idp-extraction`), package `io.camunda.connector.idp.extraction`:
four connector functions (`ExtractionConnectorFunction`, `ClassificationConnectorFunction`,
`StructuredExtractionConnectorFunction`, `UnstructuredExtractionConnectorFunction`), `request/`
(inputs per operation), `service/`, `client/extraction` (OCR providers) and `client/ai` (LLM
providers), each with a `base/` abstraction, `model/providers/{aws,azure,gcp}`, `error/`, `jackson/`.
Each function has its own generated template (`element-template-generator-maven-plugin`), including
`hybrid/` variants.
`ExtractionConnectorFunction` (template v5) is the deprecated (since 8.9) legacy entry point, superseded by
the structured, unstructured and classification connectors; retained for backward compatibility with
no removal planned.

## 4. Binding decisions
System ADRs: [docs/adr](../../docs/adr/README.md) (ADR-0001 annotation-driven templates). No module ADRs yet.

## 5. Planning constraints
### C1 — Templates
- **Question:** Which of the four templates change; are they regenerated and versioned?
- **Hard:** yes
- **Detail:** [docs/connector-template-versioning.md](../../docs/connector-template-versioning.md)

### C2 — Provider parity
- **Question:** Does the change apply to every OCR and LLM provider, or is a provider gap acceptable and documented?
- **Hard:** no
- **Detail:** `src/main/java/io/camunda/connector/idp/extraction/client`

### C3 — Documents
- **Question:** Are documents read and produced through `Document`, not raw bytes?
- **Hard:** yes
- **Detail:** [AGENTS.md](../../AGENTS.md)

## 6. Data and persistence
None owned. Documents come in as `Document` references; AWS Textract stages documents in an S3 bucket configured on the
connector. TODO(confirm) cleanup and retention rules for staged documents.

## 7. Cross-cutting qualities
Document content is sent to third-party OCR and LLM providers chosen by the modeler; credentials via
secrets. TODO(confirm) data-residency and PII guidance.

## 8. Delivery
As the system ([SYSTEM.md § 6](../../SYSTEM.md#6-shared-qualities-and-delivery)). Shipped in the default bundle.

## 9. Testing expectations
Unit tests per connector function, client and service (`src/test`). No E2E module in
`connectors-e2e-test/`. TODO(confirm) whether real-provider tests exist elsewhere.

## 10. Planning conventions
As the system.

## 11. Glossary
- **Extraction provider:** OCR service that turns a document into text or fields.
- **AI provider:** LLM used to classify a document or map extracted text to a schema.
- **Structured / unstructured extraction:** TODO(confirm) field extraction from forms and tables vs. LLM extraction from free text.
