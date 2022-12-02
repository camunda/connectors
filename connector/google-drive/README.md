# Camunda Google Drive Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/googledrive/).

## Build

```bash
mvn clean package
```

## API

### Create folder

#### Input with bearer token

```json
  {
  "authentication":{
    "authType":"bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "resource": {
    "type": "folder",
    "name": "MyNewFolderName",
    "parent": "secrets.PARENT_ID",
    "additionalGoogleDriveProperties": {
      "description": "description"
    }
  }
}
```

#### Input with refresh token

```json
{
  "authentication":{
    "authType":"refresh",
    "oauthClientId":"secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret":"secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken":"secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "resource":{
    "type":"folder",
    "additionalGoogleDriveProperties":{
      "description": "description"
    },
    "parent":"secrets.PARENT_ID",
    "name":"MyNewFolderName"
  }
}
```

### Create file

#### Input with bearer token

```json
  {
  "authentication":{
    "authType":"bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
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

#### Input with refresh token

```json
  {
  "authentication":{
    "authType":"refresh",
    "oauthClientId":"secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret":"secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken":"secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
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

Use the [Camunda Connector Runtime](https://github.com/camunda-community-hub/spring-zeebe/tree/master/connector-runtime#building-connector-runtime-bundles)
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

Trigger the [release action](../../.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
Depending on the release version (major, minor, release candidate), the release artifacts are deployed to the respective Google Cloud Function by another GitHub workflow.
