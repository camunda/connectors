# Camunda Google Drive Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/googledrive/).

## Build

```bash
mvn clean package
```

## API

### Create folder

### Input

```json
  {
  "token": "secrets.GDRIVE_BEARER",
  "resource": {
    "type": "folder",
    "name": "MyNewFolder",
    "parent": "secrets.PARENT_ID",
    "additionalGoogleDriveProperties": {
      "description": " description"
    }
  }
}
```

### Create file

### Input

```json
  {
  "token": "secrets.GDRIVE_BEARER",
  "resource": {
    "type": "folder",
    "name": "MyNewFolder",
    "parent": "secrets.PARENT_ID",
    "additionalGoogleDriveProperties": {
      "description": " description"
    },
    "template": {
      "id": "myTemplateId",
      "variables": {
        "requests": [
          {
            "replaceAllText": {
              "containsText": {
                "text": "replaceFrom",
                "matchCase": "true"
              },
              "replaceText": "replaceTo"
            }
          }
        ]
      }
    }
  }
}
```

### Output

```json
{
  "result": {
    "googleDriveResourceId": ".....",
    "googleDriveResourceUrl": "....."
  }
}
```

## Test locally

Run unit tests

```bash
mvn clean verify
```

### Test as local Job Worker

Use the [Camunda Job Worker Connector Run-Time](https://github.com/camunda/connector-framework/tree/main/runtime-job-worker)
to run your function as a local Job Worker.

### :lock: Test as local Google Cloud Function

> **Warning**
> This is Camunda-internal only. The Maven profile `cloud-function` accesses an internal artifact.

Build as Google Cloud Function

```bash
mvn function:run -Pcloud-function
```

See also the [:lock:Camunda Cloud Connector Run-Time](https://github.com/camunda/connector-runtime-cloud) on how your function
is run as a Google Cloud Function.

## Element Template

The element templates can be found in
the [element-templates/google-drive-connector.json](element-templates/google-drive-connector.json) file.

## Build a release

Checkout the repo and branch to build the release from. Run the release script with the version to release and the next
development version. The release script requires git and maven to be setup correctly, and that the user has push rights
to the repository.

The release artifacts are deployed to Google Cloud Function by a GitHub workflow.

```bash
./release.sh 0.3.0 0.4.0
```
