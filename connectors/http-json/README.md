# cloud-connector-http-json

Camunda Cloud HTTP JSON Connector

## Build

```bash
mvn clean package
```

## API

### Request

```json
{
  "clusterId": "838d40dc-a4e2-42cd-a5fb-7e5ea993a970",
    ...
}
```

### Response

```json
{
  ...
}
```

## Test locally

```bash
mvn compile function:run
```

The function will be available under `http://localhost:9081`.

### Local secrets

To inject secrets during execution export a `CONNECTOR_SECRETS` environment variable

```bash
export CONNECTOR_SECRETS='{...}'
```

And reference the secret in the request payload prefixed with `secrets.MY_SECRET`.

### Send a request

Save the request in a file, i.e. `request.json` and use curl to invoke the function.

```bash
curl -X POST -H "Content-Type: application/json" -d @request.json http://localhost:9081
```

## Element Template

The element templates for sending an email using a template or content can be found in the [element-templates/http-json-connector.json](element-templates/http-json-connector.json) file.

## Build a release

Checkout the repo and branch to build the release from. Run the release script
with the version to release and the next development version. The release
script requires git and maven to be setup correctly, and that the user has push
rights to the repository.

The release artifacts are deployed to Google Cloud Function by a github workflow.

```bash
./release.sh 0.3.0 0.4.0
```
