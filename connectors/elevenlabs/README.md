# ElevenLabs Connector

The ElevenLabs Connector calls the [ElevenLabs API](https://elevenlabs.io/docs/api-reference/) from a
process. It has no runtime code: it runs as the [REST Connector](./../http/rest)
(`io.camunda:http-json:1`) with its own element template.

## Supported operations

| Operation | Endpoint |
|---|---|
| Text to speech | `POST /v1/text-to-speech/{voice_id}` |
| List voices | `GET /v2/voices` |
| Submit batch call | `POST /v1/convai/batch-calling/submit` |
| Get batch call | `GET /v1/convai/batch-calling/{batch_id}` |
| Get conversation | `GET /v1/convai/conversations/{conversation_id}` |
| Transcribe audio | `POST /v1/speech-to-text` |
| Get transcript | `GET /v1/speech-to-text/transcripts/{transcription_id}` |

- Text to speech returns audio. Its response format defaults to "Document reference", so the audio
  is stored in the document store and the reference is returned in `response.document`.
  Transcribe audio accepts such a document as its `file`.
- Authentication uses the `xi-api-key` header. Choose a reusable
  `io.camunda.connectors:elevenlabs-authentication:1` credential, or enter the API key on the task.
- Retries default to 0, because a retried batch call or generation is placed and billed again.

## How the template is built

`element-templates/elevenlabs-connector.json` is generated. Never edit it by hand.

1. `src/test/resources/elevenlabs-openapi.json` is a trimmed copy of the published spec
   (https://api.elevenlabs.io/openapi.json): only the operations above and the components they
   reference, unchanged.
2. `src/test/resources/elevenlabs-openapi-overlay.json` is an
   [OpenAPI Overlay](https://spec.openapis.org/overlay/v1.0.0.html) with what the spec lacks: the
   servers and the `xi-api-key` security scheme.
3. `GenerateElementTemplate` runs the OpenAPI generator (`openapi-parser`) on the patched spec, then
   curates the result: operation names, labels, credential, response format and defaults.
   `GenerateElementTemplateTest` fails when the committed template is stale.

Regenerate the template after changing the generator or the overlay:

```shell
mvn -pl connectors/elevenlabs test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.elevenlabs.GenerateElementTemplate \
  -Dexec.classpathScope=test
```

Refresh the pinned spec, then regenerate and review both diffs:

```shell
mvn -pl connectors/elevenlabs test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.elevenlabs.RefreshOpenApiSpec \
  -Dexec.classpathScope=test
```

To add an operation, add it to `GenerateElementTemplate.OPERATIONS`, refresh the spec and
regenerate.

The end-to-end tests are in
[`connectors-e2e-test-elevenlabs`](../../connectors-e2e-test/connectors-e2e-test-elevenlabs). They
apply the template and run it against a WireMock stand-in for the ElevenLabs API.
