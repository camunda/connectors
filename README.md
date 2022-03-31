# cloud-connector-sendgrid

Camunda Cloud SendGrid Connector

## Build

```bash
mvn clean package
```

## API

### Request

```json
{
  "clusterId": "838d40dc-a4e2-42cd-a5fb-7e5ea993a970",
  "apiKey": "secrets.SENDGRID_API_KEY",
  "from": {
    "name": "John Doe",
    "Email": "john.doe@example.com"
  },
  "to": {
    "name": "Jane Doe",
    "Email": "jane.doe@example.com"
  },
  "template": {
    "id": "d-0b51e8f77bf8450fae379e0639ca0d11",
    "data": {
      "accountName": "Feuerwehrmann Sam",
      "shipAddress": "Krossener Str. 24",
      "shipZip": "10245",
      "shipCity": "Berlin",
      "total": 75.12
    }
  }
}
```

### Response

```json
{
  "error": "Error message if something went wrong"
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
export CONNECTOR_SECRETS='{"SENDGRID_API_KEY": "SG.zJvb3BMkSoO_skhdoS5Nvw.xxxxvckatYp4i5ALzUoZB28JTQhMKhBh5BpO_1T6gE"}'
```

And reference the secret in the request payload as `apiKey`.

```json
{
  "clusterId": "838d40dc-a4e2-42cd-a5fb-7e5ea993a970",
  "apiKey": "secrets.SENDGRID_API_KEY",
  "from": {
    ...
  },
  "to": {
    ...
  },
  "template": {
    ...
  }
}
```

### Send a request

Save the request in a file, i.e. `request.json` and use curl to invoke the function.

```bash
curl -X POST -H "Content-Type: application/json" -d @request.json http://localhost:9081
```

#### Email Template

If the email should be send with a template the request has to contain a `template` object.

```json
{
  "clusterId": "838d40dc-a4e2-42cd-a5fb-7e5ea993a970",
  "apiKey": "secrets.SENDGRID_API_KEY",
  "from": {
    "name": "John Doe",
    "Email": "john.doe@example.com"
  },
  "to": {
    "name": "Jane Doe",
    "Email": "jane.doe@example.com"
  },
  "template": {
    "id": "d-0b51e8f77bf8450fae379e0639ca0d11",
    "data": {
      "accountName": "Feuerwehrmann Sam",
      "shipAddress": "Krossener Str. 24",
      "shipZip": "10245",
      "shipCity": "Berlin",
      "total": 75.12
    }
  }
}
```

#### Email Content

```json
{
  "clusterId": "838d40dc-a4e2-42cd-a5fb-7e5ea993a970",
  "apiKey": "secrets.SENDGRID_API_KEY",
  "from": {
    "name": "John Doe",
    "Email": "john.doe@example.com"
  },
  "to": {
    "name": "Jane Doe",
    "Email": "jane.doe@example.com"
  },
  "content": {
    "subject": "Testing with plain content",
    "type": "text/plain",
    "value": "Hello you, thanks for trying Camunda Cloud"
  }
}
```

## Element Template

The element templates for sending an email using a template or content can be found in
the [element-templates/sendgrid-connector.json](element-templates/sendgrid-connector.json) file.

### Properties: Send an Email using Template

![](element-templates/properties-template.png)

### Properties: Send an Email using Content

![](element-templates/properties-content.png)

## Build a release

Checkout the repo and branch to build the release from. Run the release script with the version to release and the next
development version. The release script requires git and maven to be setup correctly, and that the user has push rights
to the repository.

The release artifacts are deployed to Google Cloud Function by a github workflow.

```bash
./release.sh 0.3.0 0.4.0
```
