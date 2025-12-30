#!/usr/bin/env node

/**
 * Version Utilities
 * Helper functions for version parsing and comparison
 */

const { execSync } = require('child_process');

/**
 * Parse a semantic version string
 * @param {string} version - Version string (e.g., "8.7.0-rc1")
 * @returns {Object} Parsed version components
 */
function parseSemver(version) {
  const regex = /^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$/;
  const match = version.match(regex);
  
  if (!match) {
    throw new Error(`Invalid version format: ${version}`);
  }
  
  const [, major, minor, patch, suffix = ''] = match;
  
  // Strip -rcN suffix from branch name (RC is only for tags/artifacts)
  // Examples: 
  //   8.7.0-rc1 -> release-8.7.0
  //   8.7.0-alpha4-rc8 -> release-8.7.0-alpha4
  const branchSuffix = suffix.replace(/-?rc\d*$/, '');
  
  return {
    major,
    minor,
    patch,
    suffix,
    isPrerelease: !!suffix,
    stableBranch: `stable/${major}.${minor}`,
    releaseBranch: `release-${major}.${minor}.${patch}${branchSuffix ? '-' + branchSuffix : ''}`
  };
}

/**
 * Check if version is a release candidate
 * @param {string} version - Version string
 * @returns {boolean}
 */
function isRcVersion(version) {
  return /-(rc\d*)/.test(version);
}

/**
 * Check if version is an alpha release
 * @param {string} version - Version string
 * @returns {boolean}
 */
function isAlphaVersion(version) {
  return /-alpha\d*/.test(version);
}

/**
 * Check if version is a beta release
 * @param {string} version - Version string
 * @returns {boolean}
 */
function isBetaVersion(version) {
  return /-beta\d*/.test(version);
}

/**
 * Check if version is any type of prerelease
 * @param {string} version - Version string
 * @returns {boolean}
 */
function isPrerelease(version) {
  return isRcVersion(version) || isAlphaVersion(version) || isBetaVersion(version);
}

/**
 * Extract RC number from version
 * @param {string} version - Version string
 * @returns {number} RC number or 0 if not an RC
 */
function getRcNumber(version) {
  const match = version.match(/-rc(\d+)$/);
  return match ? parseInt(match[1], 10) : 0;
}

/**
 * Get base version without suffix
 * @param {string} version - Version string
 * @returns {string} Base version (e.g., "8.7.0")
 */
function getBaseVersion(version) {
  const match = version.match(/^(\d+\.\d+\.\d+)/);
  return match ? match[1] : version;
}

/**
 * Find next RC number for a version
 * @param {string} version - Version string
 * @returns {number} Next RC number
 */
function findNextRc(version) {
  const baseVersion = getBaseVersion(version);
  let maxRc = 0;
  
  try {
    // Get all tags matching this version with -rc suffix
    const tags = execSync(`git tag -l "${baseVersion}-rc*"`, { encoding: 'utf-8' })
      .trim()
      .split('\n')
      .filter(Boolean);
    
    tags.forEach(tag => {
      const match = tag.match(new RegExp(`${baseVersion.replace(/\./g, '\\.')}-rc(\\d+)$`));
      if (match) {
        const rcNum = parseInt(match[1], 10);
        if (rcNum > maxRc) {
          maxRc = rcNum;
        }
      }
    });
  } catch (error) {
    // No tags found or git error, return 1
  }
  
  return maxRc + 1;
}

/**
 * Compare two semantic versions
 * @param {string} v1 - First version
 * @param {string} v2 - Second version
 * @returns {number} -1 if v1 < v2, 0 if v1 == v2, 1 if v1 > v2
 */
function compareVersions(v1, v2) {
  const parseNumeric = (version) => {
    const match = version.match(/^(\d+)\.(\d+)\.(\d+)/);
    return match ? {
      major: parseInt(match[1], 10),
      minor: parseInt(match[2], 10),
      patch: parseInt(match[3], 10)
    } : null;
  };
  
  const p1 = parseNumeric(v1);
  const p2 = parseNumeric(v2);
  
  if (!p1 || !p2) {
    throw new Error('Invalid version format for comparison');
  }
  
  // Compare major
  if (p1.major < p2.major) return -1;
  if (p1.major > p2.major) return 1;
  
  // Compare minor
  if (p1.minor < p2.minor) return -1;
  if (p1.minor > p2.minor) return 1;
  
  // Compare patch
  if (p1.patch < p2.patch) return -1;
  if (p1.patch > p2.patch) return 1;
  
  return 0;
}

// Export functions
module.exports = {
  parseSemver,
  isRcVersion,
  isAlphaVersion,
  isBetaVersion,
  isPrerelease,
  getRcNumber,
  getBaseVersion,
  findNextRc,
  compareVersions
};

// CLI usage
if (require.main === module) {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Version Utils - Usage examples:');
    console.log('  node version-utils.js parse 8.7.0-rc1');
    console.log('  node version-utils.js is-rc 8.7.0-rc1');
    console.log('  node version-utils.js next-rc 8.7.0');
    process.exit(0);
  }
  
  const command = args[0];
  const version = args[1];
  
  try {
    switch (command) {
      case 'parse':
        console.log(JSON.stringify(parseSemver(version), null, 2));
        break;
      case 'is-rc':
        console.log(isRcVersion(version));
        break;
      case 'is-alpha':
        console.log(isAlphaVersion(version));
        break;
      case 'is-prerelease':
        console.log(isPrerelease(version));
        break;
      case 'next-rc':
        console.log(findNextRc(version));
        break;
      case 'base':
        console.log(getBaseVersion(version));
        break;
      default:
        console.error(`Unknown command: ${command}`);
        process.exit(1);
    }
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
