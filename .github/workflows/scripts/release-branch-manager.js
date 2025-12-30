#!/usr/bin/env node

/**
 * Release Branch Manager
 * 
 * 1. Parses the provided version string
 * 2. Determines the corresponding stable and release branch names
 * 3. Checks if the release branch exists; if not, creates it from the stable branch
 * 4. Outputs relevant information for GitHub Actions
 */

const { execSync } = require('child_process');
const fs = require('fs');
const { parseSemver, isPrerelease, findNextRc, getRcNumber } = require('./version-utils');

// Parse command line arguments
const version = process.argv[2];
const dryRun = process.argv[3] === 'true';

if (!version) {
  console.error('❌ ERROR: Version is required');
  console.error('Usage: node release-branch-manager.js <VERSION> [DRY_RUN]');
  process.exit(1);
}

/**
 * Execute a git command
 * @param {string} command - Git command to execute
 * @param {boolean} silent - Whether to suppress output
 * @returns {string} Command output
 */
function git(command, silent = false) {
  try {
    const result = execSync(`git ${command}`, { 
      encoding: 'utf-8',
      stdio: silent ? 'pipe' : 'inherit'
    });
    return result ? result.trim() : '';
  } catch (error) {
    if (!silent) {
      throw error;
    }
    return '';
  }
}

/**
 * Check if a git ref exists
 * @param {string} ref - Git reference to check
 * @returns {boolean}
 */
function refExists(ref) {
  try {
    execSync(`git rev-parse --verify ${ref}`, { 
      encoding: 'utf-8',
      stdio: 'pipe'
    });
    return true;
  } catch {
    return false;
  }
}

/**
 * Manage release branch - create or checkout
 * @param {string} releaseBranch - Name of the release branch
 * @param {string} stableBranch - Name of the stable branch
 */
function manageReleaseBranch(releaseBranch, stableBranch) {
  // Fetch latest to ensure we have all branches and tags
  console.log('📡 Fetching latest from origin...');
  git('fetch origin --tags');
  
  // Check if release branch already exists (locally or remotely)
  if (refExists(`refs/heads/${releaseBranch}`)) {
    console.log(`✅ Release branch ${releaseBranch} exists locally`);
    git(`switch ${releaseBranch}`);
    return;
  }
  
  if (refExists(`refs/remotes/origin/${releaseBranch}`)) {
    console.log(`✅ Release branch ${releaseBranch} exists remotely, checking out`);
    git(`switch -c ${releaseBranch} origin/${releaseBranch}`);
    return;
  }
  
  // Branch doesn't exist, need to create it from stable branch
  console.log(`📌 Release branch ${releaseBranch} does not exist`);
  
  // Check if stable branch exists
  if (!refExists(`refs/remotes/origin/${stableBranch}`)) {
    console.error(`❌ ERROR: Stable branch ${stableBranch} does not exist`);
    process.exit(1);
  }
  
  console.log(`🔀 Creating ${releaseBranch} from ${stableBranch}`);
  
  if (dryRun) {
    console.log(`🔍 DRY-RUN: Would create branch ${releaseBranch} from origin/${stableBranch}`);
  } else {
    git(`switch -c ${releaseBranch} origin/${stableBranch}`);
    console.log(`✅ Created release branch ${releaseBranch}`);
  }
}

/**
 * Write outputs for GitHub Actions
 * @param {Object} outputs - Key-value pairs to output
 */
function setGitHubOutput(outputs) {
  const githubOutput = process.env.GITHUB_OUTPUT;
  if (!githubOutput) {
    return;
  }
  
  const lines = Object.entries(outputs).map(([key, value]) => `${key}=${value}`);
  fs.appendFileSync(githubOutput, lines.join('\n') + '\n');
}

/**
 * Main execution
 */
function main() {
  console.log('🚀 Release Branch Manager');
  console.log(`Version: ${version}`);
  console.log('');
  
  // Parse version
  let parsed;
  try {
    parsed = parseSemver(version);
  } catch (error) {
    console.error(`❌ ${error.message}`);
    process.exit(1);
  }
  
  const { major, minor, patch, suffix, stableBranch, releaseBranch } = parsed;
  
  console.log('Parsed version components:');
  console.log(`  Major: ${major}`);
  console.log(`  Minor: ${minor}`);
  console.log(`  Patch: ${patch}`);
  console.log(`  Suffix: ${suffix || 'none'}`);
  console.log(`  Stable branch: ${stableBranch}`);
  console.log(`  Release branch: ${releaseBranch}`);
  console.log('');
  
  // Check if pre-release
  const isPre = isPrerelease(version);
  if (isPre) {
    console.log('🏷️  This is a pre-release version');
    
    // If it's an RC without a number, suggest next RC number
    if (suffix === 'rc' || suffix.match(/^rc$/)) {
      const nextRc = findNextRc(version);
      console.log(`ℹ️  Next RC number would be: -rc${nextRc}`);
      console.log(`   Suggested version: ${major}.${minor}.${patch}-rc${nextRc}`);
    } else if (suffix.match(/^rc\d+$/)) {
      const rcNum = getRcNumber(version);
      console.log(`ℹ️  This is RC #${rcNum}`);
    }
  } else {
    console.log('📦 This is a stable release version');
  }
  console.log('');
  
  // Manage the release branch
  manageReleaseBranch(releaseBranch, stableBranch);
  
  // Output for GitHub Actions
  setGitHubOutput({
    release_branch: releaseBranch,
    is_prerelease: isPre.toString()
  });
  
  console.log('');
  console.log('✅ Release branch management complete');
}

// Run main function
try {
  main();
} catch (error) {
  console.error(`❌ ERROR: ${error.message}`);
  process.exit(1);
}
