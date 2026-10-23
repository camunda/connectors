const CONTRACT_VERSION = '1';
const MAX_MESSAGE_LENGTH = 1024;
const MAX_STACK_TRACE_LENGTH = 8192;

function failure(code, error) {
  const message = error instanceof Error ? error.message : String(error);
  const stackTrace = error instanceof Error && error.stack ? error.stack : message;
  return {
    contractVersion: CONTRACT_VERSION,
    outcome: 'FAILED',
    error: {
      code,
      message: message.slice(0, MAX_MESSAGE_LENGTH),
      stackTrace: stackTrace.slice(0, MAX_STACK_TRACE_LENGTH),
      retryable: false,
      truncated: stackTrace.length > MAX_STACK_TRACE_LENGTH,
    },
  };
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export async function handler(request) {
  let result;
  try {
    if (!isObject(request) || request.contractVersion !== CONTRACT_VERSION) {
      throw new TypeError('unsupported or missing contractVersion');
    }
    if (!isObject(request.variables)) {
      throw new TypeError("'variables' must be a JSON object");
    }
    if (request.context !== undefined && !isObject(request.context)) {
      throw new TypeError("'context' must be a JSON object");
    }
    // Imported here, not at module load, so that a syntax error is reported as a script failure
    // instead of a Lambda initialization error.
    const { execute } = await import('./user_script.mjs');
    result = await execute(request.variables, request.context ?? {});
  } catch (error) {
    return failure('SCRIPT_ERROR', error);
  }

  try {
    if (!isObject(result)) {
      throw new TypeError('execute() must return a JSON object');
    }
    const response = {
      contractVersion: CONTRACT_VERSION,
      outcome: 'COMPLETED',
      variables: result,
    };
    JSON.stringify(response);
    return response;
  } catch (error) {
    return failure('INVALID_RESULT', error);
  }
}
