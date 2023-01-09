# Camunda Connector Template

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "myProperty": "....."
}
```

### Output

```json
{
  "result": {
    "myProperty": "....."
  }
}
```

### Error codes

| Code | Description |
| - | - |
| FAIL | Message starts with 'fail' (ignoring case) |

## Test locally

Run unit tests

```bash
mvn clean verify
```

## Element Template

The element templates can be found in the [element-templates](element-templates) directory.
