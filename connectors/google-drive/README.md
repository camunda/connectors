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

## Element Template

The element templates can be found in
the [element-templates/google-drive-connector.json](element-templates/google-drive-connector.json) file.
