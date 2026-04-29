# Step 1 — Manual Test Cases (Inline Document Type)

Manual scenarios to run before sign-off on the inline document type. Unit tests are already green; this list focuses on what unit tests don't catch — real BPMN execution, FEEL evaluation, Modeler interaction, end-to-end with real connectors.

**Setup once:**
- Local Camunda 8 cluster (Self-Managed or Desktop) running on the new runtime build
- AWS S3 bucket reachable + credentials in a Camunda secret (used for most upload tests)
- Modeler with the AWS S3 element template available
- A test file like `error.json` ready as a process variable input

The Step 2/3 UX changes are NOT in scope here — for Step 1 you are deploying processes that put the inline JSON shape directly into a Document field via the existing raw FEEL input.

---

## 1. Happy paths — string content

### 1.1 Plain text upload
- **BPMN:** Start event → S3 Upload Object task → End event
- **Document input (FEEL):**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": "Hello, world!",
      "name": "greeting.txt"
    }
  ```
- **Expected:** S3 object `greeting.txt` exists in the bucket. Content is exactly `Hello, world!` (13 bytes). Content-Type metadata: `text/plain`.

### 1.2 CSV upload with explicit content type
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": "col1,col2\nval1,val2",
      "name": "data.csv",
      "contentType": "text/csv"
    }
  ```
- **Expected:** S3 object `data.csv` is exactly `col1,col2\nval1,val2`. Content-Type: `text/csv`.

### 1.3 Multi-line text with special characters
- **Document input:** content includes Unicode (e.g., `"Hällo 你好 — line2"`), tabs, newlines.
- **Expected:** bytes uploaded match the input exactly (UTF-8 encoded). Open the S3 object in a UTF-8-aware viewer to verify.

---

## 2. Happy paths — JSON content (the polymorphic case)

### 2.1 JSON object uploaded as a real JSON file
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": {"name": "Jane", "age": 32},
      "name": "me.json"
    }
  ```
- **Expected:** S3 object `me.json` contains valid JSON `{"name":"Jane","age":32}` (or with whatever key ordering, as long as the JSON parses). Content-Type: `application/json`. Crucially, the file should be parseable by any JSON tool — NOT contain Java `Map.toString()` artifacts like `{name=Jane, age=32}`.

### 2.2 JSON array
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": [1, 2, 3],
      "name": "list.json"
    }
  ```
- **Expected:** S3 object content is `[1,2,3]`.

### 2.3 JSON number
- **Document input:** content = `42`, name = `answer.json`.
- **Expected:** S3 object contains `42` (2 bytes).

### 2.4 JSON boolean
- **Document input:** content = `true`, name = `flag.json`.
- **Expected:** S3 object contains `true` (4 bytes).

### 2.5 Dynamic content from process variables
- **Process variable:** `{ user: { name: "Jane" }, errorCode: "E_VAL" }`
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": {"user": user.name, "error": errorCode, "at": now()},
      "name": "error-" + errorCode + ".json"
    }
  ```
- **Expected:** S3 object `error-E_VAL.json` contains valid JSON with the resolved values from the process variables.

---

## 3. Filename and content type behavior

### 3.1 Missing name → UUID generated
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": "anything"
    }
  ```
- **Expected:** S3 object exists with a UUID-style key (e.g., `a1b2c3d4-...`). Content-Type metadata: `application/octet-stream` (no extension to infer from).

### 3.2 Filename without extension
- **Document input:** content = `"hello"`, name = `myfile`.
- **Expected:** S3 object key is `myfile`. Content-Type: `application/octet-stream`.

### 3.3 Filename with extension, no explicit content type
- **Document input:** name = `data.xml`, content = `"<root/>"`.
- **Expected:** Content-Type: `application/xml` (Tika inference from `.xml`).

### 3.4 Explicit content type overriding extension inference
- **Document input:** name = `data.json`, contentType = `application/x-custom`, content = `{}`.
- **Expected:** Content-Type metadata: `application/x-custom` (explicit wins over Tika inference).

### 3.5 Unusual extensions covered by Tika
- Try a few extensions: `.yaml`, `.html`, `.svg`, `.md`, `.zzzunknown`.
- **Expected:** Content-Type matches Tika's known mappings; `.zzzunknown` falls back to `application/octet-stream`.

---

## 4. Error and edge cases

### 4.1 Empty string content
- **Document input:** content = `""`, name = `empty.txt`.
- **Expected:** S3 object exists, size 0 bytes. No errors.

### 4.2 Null content
- **Document input:**
  ```feel
  = {
      "camunda.document.type": "inline",
      "content": null,
      "name": "broken.txt"
    }
  ```
- **Expected:** Process incident with message containing "Inline document content must not be null". Job is failed without retries (since `ConnectorInputException` triggers `retries = 0`).

### 4.2b Content missing

### 4.3 Variable size at the Zeebe limit
- Build content with ~3 MB of data.
- **Expected:** Process executes, file uploaded successfully.

### 4.4 Variable size over the Zeebe limit
- Build content with > 4 MB.
- **Expected:** Process variable assignment fails before the connector even runs (Zeebe rejects oversized variables). Confirm Operate shows a clear error. (Note: the more graceful "fail with size error from connector" is Step 3 scope.)

### 4.5 Content with embedded JSON in a string (edge case)
- **Document input:** content = `"{\"x\":1}"` (a string containing JSON).
- **Expected:** S3 object content is `{"x":1}` (7 bytes). The user provided a string, so it's stored as-is — even though the string happens to be valid JSON.

---

## 5. List\<Document\> connectors

The single-Document tests above cover S3, GCS, Azure, Box, Drive, Textract, IDP. The connectors below take a `List<Document>` as input — the inline type needs to work inside the array too. Run at least 5.1, 5.2 against each affected connector; 5.3, 5.4 are nice-to-have.

Affected connectors: **Slack**, **Microsoft Teams**, **SendGrid**, **Email Outbound**, **AWS Bedrock**, **Embeddings Vector DB**, **Agentic AI**.

### 5.1 Single inline doc inside a list
- **Document input (FEEL):**
  ```feel
  = [
      {
        "camunda.document.type": "inline",
        "content": "Hello from inline!",
        "name": "greeting.txt"
      }
    ]
  ```
- **Expected:** The connector treats this as one attachment named `greeting.txt` with content `Hello from inline!`. For Slack/Teams/SendGrid/Email, the message arrives at the recipient with the file attached and the correct filename.

### 5.2 Multiple inline docs in the same list
- **Document input:**
  ```feel
  = [
      {"camunda.document.type": "inline", "content": "first", "name": "a.txt"},
      {"camunda.document.type": "inline", "content": "second", "name": "b.txt"},
      {"camunda.document.type": "inline", "content": {"x": 1}, "name": "c.json"}
    ]
  ```
- **Expected:** All three attachments arrive in the correct order. `a.txt` and `b.txt` contain raw text, `c.json` contains valid JSON (`{"x":1}`).

### 5.3 Mixed list — inline + Camunda + External
- **Document input:**
  ```feel
  = [
      {"camunda.document.type": "inline", "content": "inline content", "name": "inline.txt"},
      camundaDocRef,
      {"camunda.document.type": "external", "url": "https://example.com/file.pdf", "name": "external.pdf"}
    ]
  ```
  (`camundaDocRef` is a process variable holding an existing Camunda Document reference.)
- **Expected:** All three attachments delivered. Inline content is its raw bytes; Camunda doc unchanged; external doc downloaded and attached.

### 5.4 List with no name on inline items (UUID fallback)
- **Document input:**
  ```feel
  = [
      {"camunda.document.type": "inline", "content": "no name here"}
    ]
  ```
- **Expected:** The attachment arrives with a UUID-style filename (e.g., `a1b2c3-...`). Confirms the UUID fallback works when the connector reads `metadata().getFileName()`. Specifically check Slack/Teams/SendGrid show this UUID as the attachment label.


---

## 6. Regression — existing document types

### 6.1 Camunda Document upload still works
- Upload a Document Store reference to S3 the way it worked before.
- **Expected:** No regression. Behavior unchanged.

### 6.2 External Document upload still works
- Upload an `external` typed document referencing a public URL.
- **Expected:** No regression.

### 6.3 Camunda Document still works in List<Document> connectors
- Pre-existing flow: send a Slack/SendGrid message with a List<Document> containing only Camunda documents (no inline).
- **Expected:** Behavior identical to before — no regression introduced by the inline support.


---


## Notes for the tester

- Look at S3 object metadata (Content-Type, Size) in addition to the bytes — these come from `metadata()` and are easy to overlook.
- For 4.2 (null content), confirm the incident message is the friendly one ("Inline document content must not be null"), not a stack trace from a NullPointerException.
- For 2.1, the most important thing is that the file is **valid JSON**, not the Java Map toString form. If you can `jq .` it, it's good.
- If you find issues, capture: the exact FEEL expression used, the resulting JSON in Operate (process variable view), and the actual bytes/headers on the destination (S3 object).
