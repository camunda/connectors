# Camunda Google Sheets Connector

`Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/googlesheets/).

## Build

```bash
mvn clean package
```

## API

### Create spreadsheet

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "spreadsheetName": "secrets.SPREADSHEET_NAME",
    "parent": "secrets.PARENT_ID",
    "type": "createSpreadsheet"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "spreadsheetName": "secrets.SPREADSHEET_NAME",
    "parent": "secrets.PARENT_ID",
    "type": "createSpreadsheet"
  }
}
```

### Output

```json
{
  "spreadsheetId": "...",
  "spreadsheetUrl": "..."
}
```

### Create worksheet

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "worksheetName": "secrets.WORKSHEET_NAME",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "index": "0",
    "type": "createWorksheet"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "worksheetName": "secrets.WORKSHEET_NAME",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "index": "0",
    "type": "createWorksheet"
  }
}
```

### Output

```json
{
  "action": "Create worksheet",
  "status": "OK",
  "response": null
}
```

### Get spreadsheet details

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "spreadsheetsDetails"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "spreadsheetsDetails"
  }
}
```

### Output

```json
{
  "autoRecalc": "ON_CHANGE",
  "defaultFormat": {
    "backgroundColor": {
      "blue": 1.0,
      "green": 1.0,
      "red": 1.0
    },
    "backgroundColorStyle": {
      "rgbColor": {
        "blue": 1.0,
        "green": 1.0,
        "red": 1.0
      }
    },
    "padding": {
      "bottom": 2,
      "left": 3,
      "right": 3,
      "top": 2
    },
    "textFormat": {
      "bold": false,
      "fontFamily": "arial,sans,sans-serif",
      "fontSize": 10,
      "foregroundColor": {},
      "foregroundColorStyle": {
        "rgbColor": {}
      },
      "italic": false,
      "strikethrough": false,
      "underline": false
    },
    "verticalAlignment": "BOTTOM",
    "wrapStrategy": "OVERFLOW_CELL"
  },
  "locale": "en_US",
  "spreadsheetTheme": {
    "primaryFontFamily": "Arial",
    "themeColors": [
      {
        "color": {
          "rgbColor": {}
        },
        "colorType": "TEXT"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 1.0,
            "green": 1.0,
            "red": 1.0
          }
        },
        "colorType": "BACKGROUND"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.95686275,
            "green": 0.52156866,
            "red": 0.25882354
          }
        },
        "colorType": "ACCENT1"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.20784314,
            "green": 0.2627451,
            "red": 0.91764706
          }
        },
        "colorType": "ACCENT2"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.015686275,
            "green": 0.7372549,
            "red": 0.9843137
          }
        },
        "colorType": "ACCENT3"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.3254902,
            "green": 0.65882355,
            "red": 0.20392157
          }
        },
        "colorType": "ACCENT4"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.003921569,
            "green": 0.42745098,
            "red": 1.0
          }
        },
        "colorType": "ACCENT5"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.7764706,
            "green": 0.7411765,
            "red": 0.27450982
          }
        },
        "colorType": "ACCENT6"
      },
      {
        "color": {
          "rgbColor": {
            "blue": 0.8,
            "green": 0.33333334,
            "red": 0.06666667
          }
        },
        "colorType": "LINK"
      }
    ]
  },
  "timeZone": "Etc/GMT",
  "title": "title"
}
```

### Delete worksheet

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "worksheetId": "0",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "deleteWorksheet"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "worksheetId": "0",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "deleteWorksheet"
  }
}
```

### Output

```json
{
  "action": "Delete worksheet",
  "status": "OK",
  "response": null
}
```

### Add values to Spreadsheet

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "worksheetName": "secrets.WORKSHEET_NAME",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "cellId": "secrets.CELL_ID",
    "type": "addValues",
    "value": " secrets.VALUE"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "worksheetName": "secrets.WORKSHEET_NAME",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "cellId": "secrets.WORKSHEET_ID",
    "type": "addValues",
    "value": " secrets.VALUE"
  }
}
```

### Output

```json
{
  "action": "Add values to spreadsheet",
  "status": "OK",
  "response": null
}
```

### Create empty column or row

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "startIndex": null,
    "endIndex": null,
    "worksheetId": "0",
    "type": "createEmptyColumnOrRow",
    "dimension": "COLUMNS"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "startIndex": "",
    "endIndex": "",
    "worksheetId": "0",
    "type": "createEmptyColumnOrRow",
    "dimension": "COLUMNS"
  }
}
```

### Output

```json

{
  "action": "Create empty column or row",
  "status": "OK",
  "response": null
}
```

### Create row

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "values": ["secrets.ROW"],
    "rowIndex": "1",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "worksheetName": "secrets.WORKSHEET_NAME",
    "type": "createRow"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "values": ["secrets.ROW"],
    "rowIndex": "1",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "worksheetName": "secrets.WORKSHEET_NAME",
    "type": "createRow"
  }
}
```

### Output

```json

{
  "action": "Create row",
  "status": "OK",
  "response": null
}
```

### Delete column

#### Input with bearer token and column index as a number

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "columnIndexType": "NUMBERS",
    "worksheetId": "0",
    "type": "deleteColumn",
    "columnNumberIndex": "1"
  }
}
```

#### Input with refresh token and column index as a number

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "columnIndexType": "NUMBERS",
    "worksheetId": "0",
    "type": "deleteColumn",
    "columnNumberIndex": "1"
  }
}
```

#### Input with bearer token and column index as a letters

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "columnLetterIndex": "A",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "columnIndexType": "LETTERS",
    "worksheetId": "0",
    "type": "deleteColumn"
  }
}
```

#### Input with refresh token and column index as a letters

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation": {
    "columnLetterIndex": "A",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "columnIndexType": "LETTERS",
    "worksheetId": "0",
    "type": "deleteColumn"
  }
}
```

### Output

```json
{
  "action": "Delete column",
  "status": "OK",
  "response": null
}
```
### Get row by index

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation": {
    "rowIndex": "1",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "worksheetName": "secrets.WORKSHEET_NAME",
    "type": "getRowByIndex"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation":  {
    "rowIndex": "1",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "worksheetName": "secrets.WORKSHEET_NAME",
    "type": "getRowByIndex"
  }
}
```

### Output

```json
{
  "action": "Get row by index",
  "status": "OK",
  "response": []
}
```

### Get worksheet data

#### Input with bearer token

```json
{
  "authentication": {
    "authType": "bearer",
    "bearerToken": "secrets.GDRIVE_BEARER"
  },
  "operation":  {
    "worksheetName": "secrets.WORKSHEET_NAME",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "getWorksheetData"
  }
}
```

#### Input with refresh token

```json
{
  "authentication": {
    "authType": "refresh",
    "oauthClientId": "secrets.GDRIVE_OAUTH_CLIENT_ID",
    "oauthClientSecret": "secrets.GDRIVE_OAUTH_CLIENT_SECRET",
    "oauthRefreshToken": "secrets.GDRIVE_OAUTH_REFRESH_TOKEN"
  },
  "operation":  {
    "worksheetName": "secrets.WORKSHEET_ID",
    "spreadsheetId": "secrets.SPREADSHEET_ID",
    "type": "getWorksheetData"
  }
}
```

### Output

```json
{
  "action": "Get worksheet data",
  "status": "OK",
  "response": [
    []
  ]
}
```

## Element Template

The element templates can be found in
the [element-templates/google-sheets-connector.json](element-templates/google-sheets-connector.json) file.
