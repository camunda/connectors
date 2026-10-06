# AI Agent Chat With Documents Example

This example shows the document flow of the AI Agent Sub-process (v2 template). The agent receives documents in the
prompt, downloads documents with a tool, hands a document to other tools by its tag, and returns documents to the process.
A local file server (go-httpbin) acts as download source and upload target.

It demonstrates:

- **`fromAi` document parameters**: `Upload_File` takes `fromAi(toolCall.document, ..., "document")`, `Upload_Many` takes
  `fromAi(toolCall.documents, ..., "array", {"items": {"type": "document"}})`. Both become string schemas with the format
  `camunda-document`. The model sees a string and passes the `<doc id="..." />` tag of a known document; the connector
  receives the real Camunda document reference.
- **Document registry**: every document of the conversation (prompt, tool results) is remembered in the agent context and
  shown to the model as a `<doc id="..." fileName="..." contentType="..."/>` tag, independent of the memory store and the
  context window.
- **`return_document`**: with `Include documents` on (`data.response.includeDocuments`), the agent gets a built-in tool
  to hand a document back, called once per document with a key, the document and an optional description. They arrive in
  `agent.documents`, a map from the key to `{document, description}`, and are shown in the `User Feedback` form. The
  process reads one document as `agent.documents.<key>.document` and all documents as
  `get entries(agent.documents).value.document`.
- **Eviction**: with a small `data.memory.contextWindowSize` documents leave the window; the agent gets a note listing the
  documents it still knows by tag.
- **Mixed batches**: `Slow_Tool` is a user task, so a tool call batch containing `Slow_Tool` and `return_document`
  keeps the process tool open while the local result of `return_document` is already visible in the agent instance history.
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
- An Anthropic API key (the example uses the `anthropic` provider with the `anthropic-api` backend and model
  `claude-sonnet-5`; any provider of the template works).

## Setup

1. Start the file server:

   ```
   docker compose up
   ```

   (or `docker run --rm -p 8081:8080 mccutchen/go-httpbin`). It listens on `http://localhost:8081`.

   If the connectors runtime itself runs in Docker, `localhost` is the runtime container: change `baseUrl` in the start
   event output mapping to `http://host.docker.internal:8081`.

2. Create the connector secret `ANTHROPIC_API_KEY`:
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
   parameter `document` whose description ends with `Pass the <doc id="..." /> tag of a known document, copied from the conversation.`; `Upload_Many`
   has an array of strings; `return_document` is listed.
2. **Download and upload.** Prompt: `Download <baseUrl>/image/png and upload it with the description "logo".` Check:
   `Download_File` returned a document (`toolCallResult.document`, a Camunda document reference), `Upload_File` has status
   200 and `receivedFiles` contains the field `file`, the log of the file server shows a request of about 8 KB.
3. **Return a document.** Continue with `Give me the file back.` Check: the agent calls `return_document`; after the
   next feedback task `agent.documents` holds one entry (the key the agent chose, the document reference as `document`) and the `User Feedback` form shows it
   (preview/download).
4. **Unknown id.** Prompt: `Upload the document with the id "does-not-exist".` Check: the agent instance history shows an
   error tool result listing the known documents, no `Upload_File` element instance was started, and the agent retries
   with a valid id or explains.
5. **Array upload.** Prompt: `Download <baseUrl>/image/png and <baseUrl>/image/jpeg and upload both in one request.`
   Check: `Upload_Many` ran once and `receivedFiles` contains the field `files` with `parts: 2`.
6. **Eviction.** Set `data.memory.contextWindowSize` of `AI Agent` to a small value such as `3`, redeploy, repeat
   scenario 2 and then ask several unrelated questions followed by `Upload the file you downloaded earlier.` Check:
   the agent still uploads it by its tag (the eviction note lists the document) and the call succeeds.
7. **Mixed batch.** Prompt: `Call Slow_Tool and in the same step return the downloaded file.` (after scenario 2). Check:
   `Slow_Tool` stays active; the agent instance history already shows the `return_document` result while the job of the
   agent has not continued. Complete the `Slow tool` task in Tasklist; the agent continues and answers.
8. **User prompt document.** Start a new instance, pick a file in the start form and ask `Upload the attached file.`
   Check: no download step is needed, `Upload_File` receives the prompt document, `agent.context.documents` lists it.
   Then set `Include documents` of `AI Agent` to `false` (`data.response.includeDocuments`), redeploy and ask the agent to
   return the file: `return_document` is not offered and `agent.documents` stays empty.

Repeat scenario 2 and 3 with `data.memory.storage.type` set to `camunda-document` (`data.memory.storage.timeToLive`
`PT1H`) to check the registry with another memory store.

## Files

- `ai-agent-chat-with-documents.bpmn`: the process
- `ai-agent-chat-with-documents-initial-request.form`: prompt and file picker
- `ai-agent-chat-with-documents-user-feedback.form`: latest answer, returned documents, follow-up
- `ai-agent-chat-with-documents-slow-tool.form`: form of the `Slow_Tool` user task
- `docker-compose.yml`: file server on port 8081
