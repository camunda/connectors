import json
# for test need to compile this in zip : `zip function.zip lambda_function.py`
def lambda_handler(event, context):
    response_body = {
        'message': 'Hello from your Python Lambda function!',
        'receivedEvent': event
    }

    return {
        json.dumps(response_body)
    }