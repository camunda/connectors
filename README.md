# Cloud Connector - Google Drive

A C8 Connector that lets you interact with Google Drive.

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
    "additionalGoogleDriveProperties": "{\\\"description\\\":\\\" description\\\"}"
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

### Test as local Google Cloud Function

Build as Google Cloud Function

```bash
mvn function:run -Pcloud-function
```

The function will be available at http://localhost:9082.

Have a look at the [Camunda Cloud Connector Run-Time](https://github.com/camunda/connector-runtime-cloud) to see how your Connector function is wrapped as a Google Cloud Function.

### Test as local Job Worker

Use the [Camunda Job Worker Connector Run-Time](https://github.com/camunda/connector-framework/tree/main/runtime-job-worker) to run your function as a local Job Worker.

## Element Template

The element templates can be found in the [element-templates/template-connector.json](element-templates/google-drive-connector.json) file.

## Build a release

Checkout the repo and branch to build the release from. Run the release script with the version to release and the next
development version. The release script requires git and maven to be setup correctly, and that the user has push rights
to the repository.

The release artifacts are deployed to Google Cloud Function by a GitHub workflow.

```bash
./release.sh 0.3.0 0.4.0
```
