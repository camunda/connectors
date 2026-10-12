# AI Agent Chat With Documents Example

This example shows the document flow of the AI Agent Sub-process (v2 template). The agent receives documents in the
prompt, downloads documents with a tool, reads a document on request, hands a document to other tools by its id, and
returns documents to the process.
A local file server (go-httpbin) acts as download source and upload target.

It demonstrates:

- **`fromAi` document parameters**: `Upload_File` takes `fromAi(toolCall.document, ..., "document")`, `Upload_Many` takes
  `fromAi(toolCall.documents, ..., "array", {"items": {"type": "document"}})`. Both become string schemas with the format
  `camunda-document-id`. The model sees a string and passes the id of a known document (the `id` attribute of its `<doc />` tag); the connector
  receives the real Camunda document reference.
- **Reference-only documents**: every document of the conversation (prompt, tool results) is remembered in the agent
  context (the registry) and shown to the model as a `<doc id="..." fileName="..." contentType="..." size="..."/>` tag,
  independent of the memory store and the context window. The model does not see the content of a document unless it
  asks for it. Passing a document id to a tool never loads the content; the tool receives the full document reference.
- **`c8__get_document`**: the agent gets a built-in tool that loads the whole content of a known document into the
  conversation (text, PDF and the PNG, JPEG, GIF and WebP images). A document of another type, or a document above the
  size limit (256 KB for text-based types, 10 MB for PDF and images), is rejected with an error tool result that tells
  the agent to pass the id to a tool that processes it; this is not an incident. A document that is already loaded is not
  loaded again. The loaded content stays in every model request until its message leaves the context window.
- **Load documents into the prompt**: the checkbox of the user prompt group of the template (property
  `data.userPrompt.loadDocuments`, off in this process) puts the content of the prompt documents into the conversation
  instead of tags the model loads on demand. The same guard applies; a document that is too large or of an unsupported
  type stays a tag, followed by a note. A tool whose activity has the extension property
  `io.camunda.agenticai.documents.delivery` = `inline` gets the same treatment for the documents it returns.
- **`c8__return_document`**: the agent gets a built-in tool
  to hand a document back, called once per document with a key, the document and an optional description. They arrive in
  `agent.responseDocuments`, a map from the key to `{document, description}`, and are shown in the `User Feedback` form. The
  process reads one document as `agent.responseDocuments.<key>.document` and all documents as
  `get entries(agent.responseDocuments).value.document`.
- **Tool names**: a tool element of the process must not have the name of a built-in tool (`c8__get_document`,
  `c8__return_document`); tool discovery fails with the error code `LOCAL_TOOL_NAME_CONFLICT` for such an element.
- **Eviction**: with a small `data.memory.contextWindowSize` documents leave the window; the agent gets a note listing the
  documents it still knows by id, so it can still pass them to a tool or load them again.
- **Mixed batches**: `Slow_Tool` is a user task, so a tool call batch containing `Slow_Tool` and `c8__return_document`
  keeps the process tool open while the local result of `c8__return_document` is already visible in the agent instance history.
- **Memory stores**: `in-process` (default), `camunda-document` and `aws-agentcore` all carry the registry.

## Process



`AI Agent` is an ad-hoc sub-process with the following tools. All HTTP tools use the REST outbound connector template.

| Tool            | Type           | What it does |
|-----------------|----------------|--------------|
| `Download_File` | HTTP REST      | GET on a URL chosen by the model (`url`). The response is stored as a Camunda document (`Response format` = `Document reference`); the tool result is the status and the document. |
| `Upload_File`   | HTTP REST      | `POST {baseUrl}/post` as `multipart/form-data` with the part `file` (a document) and the field `description`. |
| `Upload_Many`   | HTTP REST      | `POST {baseUrl}/post` as `multipart/form-data` with the parts `files` (a list of documents, one part each) and `description`. |
| `Slow_Tool`     | user task      | Stays open until a person completes it in Tasklist. |
| `Ping`          | script task    | Answers `pong`. |

The start event sets the process variable `baseUrl` (output mapping, default `http://localhost:8081`). Change it there
(one place); the HTTP tools and the system prompt use it.

## Prerequisites

- A Camunda 8.11 snapshot cluster (8.10+ for the element templates used here) with document handling configured (the
  default in-memory/local document store is enough for testing).
- The v2 connectors runtime built from this branch, with the AI Agent Sub-process v2 and HTTP REST connectors. Build the
  agentic AI connector into the local Maven repository (never with `-am`):

  ```
  mvn -f connectors/agentic-ai/pom.xml -pl connector-agentic-ai -DskipTests install
  ```

- Docker, for the file server.
- AWS Bedrock access (the example uses the `anthropic` provider with the `aws-bedrock-mantle` backend in `eu-west-1`
  and model `anthropic.claude-sonnet-5`; any provider of the template works).

## Setup

1. Start the file server:

   ```
   docker compose up
   ```

   (or `docker run --rm -p 8081:8080 mccutchen/go-httpbin`). It listens on `http://localhost:8081`.

   If the connectors runtime itself runs in Docker, `localhost` is the runtime container: change `baseUrl` in the start
   event output mapping to `http://host.docker.internal:8081`.

2. Create the connector secrets `AWS_BEDROCK_ACCESS_KEY` and `AWS_BEDROCK_SECRET_KEY`:
    - c8run: export it as environment variable and restart `c8run`
    - c8run with Docker: add it to `connector-secrets.txt` and restart
    - SaaS / Self-Managed: create it in the Console / runtime configuration

3. Deploy `ai-agent-chat-with-documents.bpmn` together with the three `.form` files (Web Modeler import or
   `c8ctl deploy`).

4. Start an instance with the start form (a prompt and optional documents) and work in Tasklist: the `User Feedback`
   task shows the latest answer and returned documents; the `Slow tool` task is completed manually.

## What was uploaded?

- Operate: open the instance, select the `Upload File` / `Upload many files` element instance and look at the variable
  `toolCallResult`: HTTP status, the echoed form fields and, per file field, the number of received parts.
- `docker logs <container>` (or the terminal running `docker compose up`) prints one line per request with method,
  path and `request_size_bytes`, so the uploaded size can be checked against the downloaded file.
- go-httpbin echoes file parts as text in the `files` object of its response. Binary files (for example `/image/png`)
  show up as a garbled string, not as a data URI; use the request size in the log to confirm the bytes arrived.

## Manual test script

Use the Operate instance view (variables `agent`, `toolCallResults`) and the agent instance history.

1. **Tool schema.** Start with `List your tools and their parameters.` Check the answer: `Upload_File` has a string
   parameter `document` whose description ends with `Pass the id of a known document (the id attribute of its <doc /> tag).`; `Upload_Many`
   has an array of strings; `c8__get_document` and `c8__return_document` are listed.
2. **Download and upload.** Prompt: `Download <baseUrl>/image/png and upload it with the description "logo".` Check:
   `Download_File` returned a document (`toolCallResult.document`, a Camunda document reference), `Upload_File` has status
   200 and `receivedFiles` contains the field `file`, the log of the file server shows a request of about 8 KB. The agent
   never called `c8__get_document`: the model request shows the tag of the document only (agent instance history), and
   the upload still received the real bytes.
3. **Return a document.** Continue with `Give me the file back.` Check: the agent calls `c8__return_document`; after the
   next feedback task `agent.responseDocuments` holds one entry (the key the agent chose, the document reference as `document`) and the `User Feedback` form shows it
   (preview/download).
4. **Read a document.** Start a new instance and prompt: `Download <baseUrl>/image/svg and describe what it shows.`
   Check: the agent calls `c8__get_document` with the id of the downloaded document, the tool result reads
   `Loaded the document <doc .../>. Its content follows in the next message.`, and the agent instance history shows a follow-up user item with the tag and the content of
   the document. Ask a second question about the same document: the agent answers without another load, or
   `c8__get_document` answers that the document is already in the conversation.
5. **Document too large.** Prompt: `Download <baseUrl>/range/300000, read it and tell me how many characters it has.`
   (a text document above 256 KB). Check: `c8__get_document` is answered with an error that states the size and the limit
   and tells to pass the id to a tool; no content follows, the process has no incident, and the agent explains that it
   cannot read the document or passes it to `Upload_File`, which receives the real bytes.
6. **Unsupported type.** Prompt: `Download <baseUrl>/bytes/100 and read it.` (a binary document of the type
   `application/octet-stream`). Check: `c8__get_document` is answered with `The document is of type
   application/octet-stream which cannot be loaded into the conversation; pass its id to a tool that processes it.`, no
   incident, and the conversation continues.
7. **Unknown id.** Prompt: `Upload the document with the id "does-not-exist".` Check: the agent instance history shows an
   error tool result listing the known documents, no `Upload_File` element instance was started, and the agent retries
   with a valid id or explains. The same answer comes for `c8__get_document` with an unknown id.
8. **Array upload.** Prompt: `Download <baseUrl>/image/png and <baseUrl>/image/jpeg and upload both in one request.`
   Check: `Upload_Many` ran once and `receivedFiles` contains the field `files` with `parts: 2`.
9. **Eviction.** Set `data.memory.contextWindowSize` of `AI Agent` to a small value such as `3`, redeploy, repeat
   scenario 2 and then ask several unrelated questions followed by `Upload the file you downloaded earlier.` Check:
   the agent still uploads it by its id (the eviction note lists the document) and the call succeeds.
10. **Mixed batch.** Prompt: `Call Slow_Tool and in the same step return the downloaded file.` (after scenario 2). Check:
    `Slow_Tool` stays active; the agent instance history already shows the `c8__return_document` result while the job of the
    agent has not continued. Complete the `Slow tool` task in Tasklist; the agent continues and answers.
11. **User prompt document.** Start a new instance, pick a text file in the start form and ask `Summarize the attached
    file.` Check: the first model request shows the tag of the document only (agent instance history); the agent calls
    `c8__get_document` to read it. Then ask `Upload the attached file.`: no download step is needed, `Upload_File` receives
    the prompt document, `agent.context.documents` lists it.

12. **Load documents into the prompt.** Set `data.userPrompt.loadDocuments` of `AI Agent` to `=true` (the checkbox *Load
    documents into the prompt* of the user prompt group), redeploy, start an instance with a small text file and ask
    `Summarize the attached file.` Check: the agent instance history shows the prompt with the tag and the content of
    the document in the first user item, and the agent answers without calling `c8__get_document`. Repeat with a binary
    file (for example a ZIP): the user item shows the tag followed by a note that the document was not loaded because of
    its type, and the agent passes it to `Upload_File` or explains that it cannot read it.

Repeat scenarios 2 to 4 with `data.memory.storage.type` set to `camunda-document` (`data.memory.storage.timeToLive`
`PT1H`) to check the registry with another memory store.

## Files

- `ai-agent-chat-with-documents.bpmn`: the process
- `ai-agent-chat-with-documents-initial-request.form`: prompt and file picker
- `ai-agent-chat-with-documents-user-feedback.form`: latest answer, returned documents, follow-up
- `ai-agent-chat-with-documents-slow-tool.form`: form of the `Slow_Tool` user task
- `docker-compose.yml`: file server on port 8081
