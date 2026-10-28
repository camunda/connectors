// Cloud Run functions adapter of the generic Node.js executor.
//
// Cloud Run gives no remaining-time API, so the deadline comes from the function timeout, passed by
// the deployer in EXECUTOR_FUNCTION_TIMEOUT_MS. Requires a request concurrency of 1: the sweep after
// a call kills every process it did not start with.
const functions = require('@google-cloud/functions-framework');

const core = import('./executor_core.mjs');
const FUNCTION_TIMEOUT_MS = Number(process.env.EXECUTOR_FUNCTION_TIMEOUT_MS || 60_000);
const SAFETY_MS = 1_000;

functions.http('executor', async (request, response) => {
  const { run } = await core;
  response.json(await run(request.body ?? {}, FUNCTION_TIMEOUT_MS - SAFETY_MS));
});
