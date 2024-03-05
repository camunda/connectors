#!/bin/bash
# This Bash script is designed to determine the previous version tag of a software release in a Git repository.
# It handles various tag formats, including normal versions, release candidates (RC), and alpha versions.
# The script defines rules for each tag type and calculates the previous tag based on the current tag.
#
# Rules for Determining Previous Tags
#
# Normal Versions:
# - For a regular release within a minor version (e.g., 8.3.3), the previous tag is the next lower tag in the same minor series (e.g., 8.3.2).
# - When it's the first release of a new minor version (e.g., 8.4.0), the previous tag is the base (x.0) tag of the preceding minor version (e.g., 8.3.0).
#
# Release Candidates (RC) for Normal Versions:
# - For the first RC of a new minor version (e.g., 8.4.0-rc1), the previous tag is the base (x.0) tag of the preceding minor version (e.g., 8.3.0).
# - For subsequent RC releases within the same minor version, the previous tag is the same as the previous RC release (e.g., 8.4.0-rc2).
#
# Alpha Versions:
# - When it's the first alpha release of a new minor version (e.g., 8.4.0-alpha1), the previous tag is the base (x.0) tag of the preceding minor version (e.g., 8.3.0).
# - For subsequent alpha releases within the same minor version, the previous tag is the same as the previous alpha release (e.g., 8.4.2-alpha2).
#
# Release Candidates (RC) for Alpha Versions:
# - For RC releases of alpha versions (e.g., 8.4.0-alpha2-rc1), the previous tag is the corresponding alpha tag (e.g., 8.4.0-alpha1).
#
# Major Version Releases:
# - For the first release of a new major version (e.g., 9.0.0), the previous tag is the base (x.0) tag of the highest minor version from the last major series (e.g., 8.9.0).
#
# Release Candidates (RC) for Major Versions:
# - For RC releases of a major version (e.g., 9.0.0-rc1), the previous tag is the base (x.0) tag of the highest minor version from the last major series (e.g., 8.9.0).

PREV_TAG="not found"

  # Function to determine the previous tag using 'NORMAL' logic
  determine_normal_prev_tag() {
      local candidate=""
      local candidateWithPatch0=""
      # Loop through all previous tags to find the appropriate previous tag
      for tag in $prev_tags; do
          if [[ $tag != "$current_tag" ]] && [[ $tag =~ $NORMAL_PATTERN ]]; then
              IFS='.' read -r tag_major tag_minor tag_patch <<< "$tag"
              IFS='.' read -r curr_major curr_minor curr_patch <<< "$current_tag"
              curr_patch=${curr_patch%%-*}
               if [[ $tag == "$current_tag" ]] || { ((tag_major == curr_major)) && ((tag_minor == curr_minor)) && ((tag_patch == curr_patch)); }; then
                              continue
                          fi

              IFS='.' read -r candidate_major candidate_minor candidate_patch <<< "$candidate"

              # Compare each previous tag with the current tag to find the closest preceding version
              if ((tag_major == curr_major)); then
                  if ((tag_minor == curr_minor)) && ((tag_patch <= curr_patch)) && ([[ -z "$candidate_patch" ]] || ((tag_patch > candidate_patch))); then
                      candidate="$tag"
                  elif ((tag_minor < curr_minor)) && ([[ -z "$candidate_minor" ]] || ((tag_minor > candidate_minor))); then
                      candidate="$tag"
                  fi
              elif ((tag_major < curr_major)) && ([[ -z "$candidate_major" ]] || ((tag_major > candidate_major))); then
                  candidate="$tag"
              fi

              if ((tag_patch == 0)); then
                  IFS='.' read -r candidateWithPatch0_major candidateWithPatch0_minor _ <<< "$candidateWithPatch0"
                  if ((tag_major == curr_major)) && ((tag_minor <= curr_minor)) && ([[ -z "$candidateWithPatch0_minor" ]] || ((tag_minor > candidateWithPatch0_minor))); then
                      candidateWithPatch0="$tag"
                  elif ((tag_major < curr_major)) && ([[ -z "$candidateWithPatch0_major" ]] || ((tag_major > candidateWithPatch0_major))); then
                      candidateWithPatch0="$tag"
                  fi
              fi
          fi
      done

      # Decide the previous tag based on whether the current patch number is 0
      if [[ "$curr_patch" == "0" ]]; then
          if [[ -n "$candidateWithPatch0" ]]; then
              prev_tag="$candidateWithPatch0"
          else
              prev_tag="$candidate"
          fi
      else
          prev_tag="$candidate"
      fi
       echo "$prev_tag"
  }


# Function to determine the previous tag based on the type and version
determine_prev_tag() {
  local current_tag="$1"
  local type="$2"
  local prev_tag=""

  local prev_tags=$(git --no-pager tag --sort=-v:refname)

  if [[ $type == "RC" ]]; then
    # Extract the base version and RC version from the current tag
    local base_version="${current_tag%-rc*}"
    local current_rc_version=$(echo "$current_tag" | grep -oE "rc[0-9]+" | sed 's/rc//')

    # Get the previous RC tag within the same minor version and with a lower RC number
    prev_tag=$(get_previous_rc_tag "$base_version" "$current_rc_version" "$prev_tags")
  fi

  if [[ $type == "ALPHA" ]] || { [[ -z $prev_tag ]] && [[ $current_tag =~ -alpha[0-9]+-rc[0-9]+$ ]]; }; then
    # Extract the alpha base version
    local base_version="${current_tag%-alpha*}"
    local current_alpha_version=$(echo "$current_tag" | grep -oE "alpha[0-9]+" | sed 's/alpha//')
    prev_tag=$(get_previous_alpha_tag "$base_version" "$current_alpha_version" "$prev_tags")
  fi

  # Fallback to 'NORMAL' logic if no previous tag found
  if [[ -z $prev_tag ]]; then
    prev_tag=$(determine_normal_prev_tag)
  fi

  PREV_TAG="$prev_tag"
}


# Function to get the previous RC tag
get_previous_rc_tag() {
    local base_version=$1
    local current_rc_version=$2
    local prev_tags=$3
    local candidate_tag=""
    local candidate_rc_version=0

    for tag in $prev_tags; do
      if [[ $tag == $base_version-rc* ]]; then
        local tag_rc_version=$(echo "$tag" | grep -oE "rc[0-9]+" | sed 's/rc//')
        if (( tag_rc_version < current_rc_version )) && (( tag_rc_version > candidate_rc_version )); then
          candidate_tag="$tag"
          candidate_rc_version=$tag_rc_version
        fi
      fi
    done
    echo "$candidate_tag"
}

# Function to get the previous alpha tag
get_previous_alpha_tag() {
    local base_version=$1
    local current_alpha_version=$2
    local prev_tags=$3
    local candidate_tag=""
    local candidate_alpha_version=0

    for tag in $prev_tags; do
      if [[ $tag == $base_version-alpha* ]] && [[ $tag =~ $ALPHA_PATTERN ]]; then
        local tag_alpha_version=$(echo "$tag" | grep -oE "alpha[0-9]+" | sed 's/alpha//')
        if (( tag_alpha_version < current_alpha_version )) && (( tag_alpha_version > candidate_alpha_version )); then
          candidate_tag="$tag"
          candidate_alpha_version=$tag_alpha_version
        fi
      fi
    done

    echo "$candidate_tag"
}

# validation for tag format
if [[ ! $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-alpha[0-9]+)?(-rc[0-9]+)?$ ]]; then
  echo "Release tag is invalid"
  exit 1
fi
# Determine the tag type
NORMAL_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
ALPHA_PATTERN='-alpha[0-9]+$'
RC_PATTERN='-rc[0-9]+$'

TYPE=""
if [[ $1 =~ $NORMAL_PATTERN ]]; then
  TYPE="NORMAL"
elif [[ $1 =~ $ALPHA_PATTERN ]]; then
  TYPE="ALPHA"
elif [[ $1 =~ $RC_PATTERN ]]; then
  TYPE="RC"
fi

if [[ -z $TYPE ]]; then
  echo "Invalid tag format"
  exit 1
fi


# Determine the previous tag
determine_prev_tag "$1" "$TYPE"
if [[ -z $PREV_TAG ]]; then
  echo "No previous tag found for the given rules"
  exit 1
fi

# Output the previous tag with its type
echo "$TYPE $PREV_TAG"
