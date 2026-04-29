# Epic: Improve Storage Connector Flexibility (Direct Creation & Content Conversion)

## Mental Model: Two Paths for Document Handling

**Path 1: Document Store (opaque pass-through)**  The document is a blob moving through the process. The user doesn't need to read or manipulate its content, they just route it. Webhook receives a PDF → upload it to S3. Download an image from GCS → send it via email. The process never "looks inside" the file.

**Path 2: Inline (data the process works with)**  The content is actual process data, not uploaded to the document store. The user either:

- **Writes** it: constructs a JSON/CSV/TXT from process variables and uploads it to storage ("Direct Upload")
- **Reads** it: downloads from storage "As JSON" or "As text" and uses the values directly in FEEL expressions downstream

In Path 2, there's no Document Store involved. The data flows as regular process variables, strings, JSON objects, that happen to be written to or read from cloud storage. Mostly useful for smaller text or json files.

---

## Step 1: Create Files Directly from Process Variables ("Inline Documents")

**What:** Users can create and upload files (JSON, CSV, TXT, etc.) to cloud storage directly from process data, no Document Store intermediary needed.

**Why:** Today, if a user wants to store an error report as `error.json` in S3, they must first create a Document Store entry, then reference it. This adds unnecessary modeling complexity for simple text-based file creation.

Similar to external documents type a inline type is added:  
```json
{
    "camunda.document.type": "inline", 
    "content": {"name": "Jane Doe", "age": 30},
    "name": "Jane.json",
    "contentType": "application/json"
}
```

**File size considerations:** Inline document content is embedded in process variables, which are subject to Zeebe's variable size limit (\~4MB). Documentation should provide clear guidance on when users should use the Document Store path instead (e.g., for large files). Zeebe’s file size limit already protects us from this, but currently processes get stuck if downloading files too big inline. Needs fix with this epic. Reported bug [here](https://github.com/camunda/connectors/issues/7049)

**Encoding**: No base64 encoding is provided, as the generic feel base64 function can be used.

**Details:** [Step 1 spec](document-epic-step-1-inline-document.md)

## Step 2: Unified Document Source Selection Across All Connectors

**What:** Every connector that accepts a document input gets a consistent dropdown: **Camunda Document** / **Inline Content** / **External Document**. The external and inline documents can be easily used with a nice UI.

Solution suggestion:

- Content type is always optional.
- Content type is created from a file ending with [apache tika](https://svn.apache.org/repos/asf/httpd/httpd/trunk/docs/conf/mime.types). a hidden field is added for pro-users where this is not enough, to not confuse most of the users that do not need it.
- Filename. Configurable per connector: there is a FileNameOptional prop that can be set for connectors, that should have an optional filename (for other connectors it is required, see list below). If the filename is empty, but required, we create some random string without a file ending and use application/octet-stream. Three connectors could skip the name (CSV, textract, embedding-vectors)

**User experience:** A clear dropdown replaces the raw field. Each option reveals only the relevant input fields:

- *Camunda Document* → reference field (existing behavior)
- *Inline Content* → content \+ filename (nice UI for the new inline document type)
- *External Document* → URL \+ optional filename (nice UI for the existing external document type)

**List\<Document\> handling — Single/Multiple toggle:** For connectors accepting multiple documents (e.g., Slack attachments, SendGrid attachments), a toggle switches between:

- *Single document* → shows the same source dropdown with its fields
- *Multiple documents* → switches to a FEEL expression field where users construct an array

**Not a breaking change.**

### Connector Inventory

Connectors that CONSUME documents (upload/send) and should get the source dropdown

| Connector | Field | Type | Gets source dropdown? | Filename required |
| :---- | :---- | :---- | :---- | :---- |
| AWS S3 | `document` | `Document` | Yes — dropdown | yes |
| Google Cloud Storage | `document` | `Document` | Yes — dropdown | yes |
| Azure Blob Storage | `document` | `Document` | Yes — dropdown | yes |
| Google Drive | `document` | `Document` | Yes — dropdown | yes |
| Box | `document` | `Document` | Yes — dropdown | yes |
| AWS Textract | `document` | `Document` | Yes — dropdown | no |
| IDP Extraction (4 variants) | `document` | `Document` | **No, stays as it is** | **N/A** |
| Agent AI | `document` | `Document` | **Yes, but reach out to the team first** | **Ask team** |
| CSV | `data` | `Object (currently, should become document)` | Yes dropdown | no |
| Slack | `documents` | `List<Document>` | Single/Multiple toggle | yes |
| Microsoft Teams | `documents` | `List<Document>` | Single/Multiple toggle | yes |
| SendGrid | `attachments` | `List<Document>` | Single/Multiple toggle | yes |
| Email (Outbound) | `attachments` | `List<Document>` | Single/Multiple toggle | yes |
| AWS Bedrock | `newDocuments` | `List<Document>` | Single/Multiple toggle | yes |
| Embeddings Vector DB | `newDocuments` | `List<Document>` | Single/Multiple toggle | No |

### 

**Details:** [Step 2 spec](document-epic-step-2-etg-source-selection.md)

## Step 3: User-Controlled Download Return Format

**What:** When downloading/retrieving files, users choose how they want the content returned instead of the connector guessing based on the file's content type.

**Why:** The current behavior is fragile and inconsistent:

- S3 guesses format from content-type headers (JSON file with wrong content-type → returns unusable base64)
- GCS always returns raw text (binary files come back as garbled text)
- Azure always returns raw text
- Box and Google Drive always return Document Store references with no alternative
- Users have no way to recover from wrong format decisions

**User experience:** A 3-option dropdown replaces the current boolean toggles (`Document reference`, `as text`, `as Json`):

"As text" has an optional encoding sub-field (default UTF-8).

(Optional): A connectors developer can specify which 

**Scope:** 6 connectors get the return format dropdown:

| Connector | Current toggle | Get the new return dropdown |
| :---- | :---- | :---- |
| AWS S3 | `Create document` (boolean) | Yes |
| Google Cloud Storage | `Return document as reference` (boolean) | Yes |
| Azure Blob Storage | `Return document as reference` (boolean) | Yes |
| Box | None (always doc store) | Yes |
| Google Drive | None (always doc store) | Yes |
| HTTP REST / GraphQL | `storeResponse` (boolean) | Yes |
| CSV connector | `createDocument` boolean | Yes, but only with two choices: `Document reference` and `as text` (json does not make sense here) |

**Breaking change:** ~~Old boolean values (`true`/`false`) stop working. New element template version. Migration guide required.~~  
Old boolean values (true/false) should continue to work. So older templates should work with newer runtime.

**Details:** [Step 3 spec](document-epic-step-3-return-format.md)

### Inbound Connectors:

Inbound Connectors that PRODUCE documents (download/receive) and support only the document store way:

| Connector | Direction |
| :---- | :---- |
| Webhook | Inbound |
| Email (IMAP/POP3) | Inbound |
| Microsoft Email | Inbound |

Open:

*  create follow up for inbound inline path. Things to consider:
* If users e.g. use email inbound and a user sends big files with a email this might cause failure as inline only small documents are allowed, but we can not display this properly in operate, as we can not start the process and fail, so emails with big attachments would be silently ignored.
* Do we need a possibility to convert from inline to store or vice versa?

