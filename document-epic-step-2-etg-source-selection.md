# Step 2: Element Template Generator — Document Source Selection

**Epic:** camunda/product-hub#3224

## Design

The ETG auto-detects `Document` and `List<Document>` typed fields and generates a source selection dropdown with conditional input fields. A hidden FEEL composer field assembles the final document JSON and is the only field sent to the connector via `zeebe:input`.

**Pattern reference:** The GitHub connector already uses this approach for authentication — a hidden field with a FEEL expression composes a JSON object from visible field values (see `github-connector.json`, authentication.token field).

## Three Auto-Detection Patterns

| Java type | ETG generates |
|---|---|
| `Document` | Source dropdown + conditional fields + hidden FEEL composer |
| `List<Document>` | Single/Multiple toggle + source dropdown (single mode) + FEEL field (multiple mode) + hidden composer |
| `DocumentResponse` | Return format dropdown (details in Step 3) |

## Document Field Pattern

For a single `Document` field (e.g., `action.document`), the ETG generates **7 properties**:

### Dropdown

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `action.document.documentSource` | Dropdown | Document source | `zeebe:property` | Choices: Camunda Document / Direct Upload / External |

### Inline fields (conditional: documentSource = "inline")

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `action.document.inline.content` | String (FEEL required) | Content | `zeebe:property` | Document content — string or JSON object |
| `action.document.inline.fileName` | String | File name | `zeebe:property` | Required, including extension (e.g., `report.json`) |

### External fields (conditional: documentSource = "external")

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `action.document.external.url` | String | URL | `zeebe:property` | Required |
| `action.document.external.fileName` | String | File name | `zeebe:property` | Optional |

### Camunda Document field (conditional: documentSource = "store")

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `action.document.store.reference` | String (FEEL required) | Document reference | `zeebe:property` | Existing document reference expression |

### Hidden composer (the only zeebe:input)

| ID | Type | Binding | Notes |
|---|---|---|---|
| `action.document` | Hidden | `zeebe:input` → `action.document` | FEEL expression composing the document JSON |

**FEEL composer expression (auto-generated):**
```feel
= if documentSource = "inline"
  then {"camunda.document.type": "inline", "content": inlineContent, "name": inlineFileName}
  else if documentSource = "external"
  then {"camunda.document.type": "external", "url": externalUrl, "name": externalFileName}
  else storeReference
```
(Field references use the actual generated property IDs)

## List\<Document\> Field Pattern

For a `List<Document>` field (e.g., `data.documents`), the ETG generates a **Single/Multiple toggle** that switches between the source dropdown (for one document) and a FEEL expression field (for multiple).

### Toggle

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `data.documents.documentMode` | Dropdown | Document mode | `zeebe:property` | Choices: Single document / Multiple documents |

### Single mode (conditional: documentMode = "single")

Same 6 properties as the Document field pattern above (dropdown + inline/external/store fields), namespaced under `data.documents`.

### Multiple mode (conditional: documentMode = "multiple")

| ID | Type | Label | Binding | Notes |
|---|---|---|---|---|
| `data.documents.expression` | String (FEEL required) | Documents | `zeebe:property` | FEEL expression returning an array of document objects |

### Hidden composer

| ID | Type | Binding | Notes |
|---|---|---|---|
| `data.documents` | Hidden | `zeebe:input` → `data.documents` | Wraps single document in array `[...]` or passes FEEL expression directly |

**FEEL composer expression (auto-generated):**
```feel
= if documentMode = "single"
  then [composedSingleDocument]
  else documentsExpression
```

## Key Design Decisions

- **Visible fields use `zeebe:property`** — stored in BPMN XML but NOT sent to the connector. No extra fields pollute the connector input.
- **Only the hidden composer uses `zeebe:input`** — this is the actual connector input binding.
- **FEEL composer is auto-generated** by the ETG based on the field IDs it creates. No per-connector hand-writing needed.
- **IDs derived from binding path + source type namespace** — e.g., `action.document.inline.content`, `action.document.external.url`. Collision-free and predictable.
- **All 13 connectors with Document inputs use ETG** — regeneration handles everything, no manual template updates needed.

## Files to Modify

### ETG core — type detection

- `element-template-generator/core/src/main/java/io/camunda/connector/generator/java/util/TemplatePropertiesUtil.java`
  - In `createPropertyBuilder()` (line 344): detect `Document` type before the default String fallback
  - In `isContainerType()` (line 557): handle `Document` and `List<Document>` as special cases (not container types, not simple types — trigger custom generation)
  - New method: `handleDocumentType()` — generates the 7 properties (dropdown + conditional fields + hidden composer)
  - New method: `handleDocumentListType()` — generates the toggle + single/multiple pattern

### ETG DSL — property builders

- `element-template-generator/core/src/main/java/io/camunda/connector/generator/dsl/`
  - May need to add support for `zeebe:property` binding type if not already present
  - Verify `HiddenProperty` builder supports FEEL expressions in `value`

### Tests

- Unit test: ETG generates correct 7-property pattern for a `Document` field
- Unit test: ETG generates correct toggle + single/multiple pattern for `List<Document>` field
- Unit test: Hidden composer FEEL expression is correctly composed with field IDs
- Integration test: Generated element template JSON validates against the element template schema
