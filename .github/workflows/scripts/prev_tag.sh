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

# Function to determine the previous tag using 'NORMAL' logic
  determine_normal_prev_tag() {
    local candidate=""
    local candidateWithPatch0=""
    # Loop through all previous tags to find the appropriate previous tag
    for tag in "${prev_tags[@]}"; do
      echo "$tag"
      # Split the tag into major, minor, and patch numbers
      IFS='.' read -r tag_major tag_minor tag_patch <<< "$tag"
      # Split the current tag into major, minor, and patch numbers
      IFS='.' read -r curr_major curr_minor curr_patch <<< "$current_tag"
      # Compare each previous tag with the current tag to find the closest preceding version
      if ((tag_major < curr_major)); then
        candidate="$tag"
      elif ((tag_major == curr_major)); then
        if ((tag_minor < curr_minor)); then
          candidate="$tag"
        elif ((tag_minor == curr_minor)); then
          if ((tag_patch <= curr_patch)); then
            candidate="$tag"
          fi
        fi
      fi
      # Special handling for tags with a patch version of 0
      if ((tag_patch == 0)) && ((tag_major <= curr_major)) && ((tag_minor <= curr_minor)); then
        if [[ -n "$candidateWithPatch0" ]]; then
          # Compare and update the candidate with patch 0 if it's a closer match
          IFS='.' read -r candidate_major candidate_minor _ <<< "$candidateWithPatch0"
          if ((candidate_major < tag_major)) || ((candidate_minor < tag_minor)); then
            candidateWithPatch0="$tag"
          fi
        else
          candidateWithPatch0="$tag"
        fi
      fi
    done
    # Decide the previous tag based on whether the current patch number is 0
    if [[ "$curr_patch" == "0" ]]; then
      # If the current patch is 0, prefer the candidate with patch 0 if available
      if [[ -n "$candidateWithPatch0" ]]; then
        prev_tag="$candidateWithPatch0"
      else
        prev_tag="$candidate"
      fi
    else
      # Otherwise, use the candidate determined above
      prev_tag="$candidate"
    fi
  }

# Function to determine the previous tag based on the type and version
determine_prev_tag() {
  local current_tag=$1
   local type=$2
   local major_minor
   local rc_number
   local prev_tags
   local prev_tag=""
   local prev_rc_tag

   prev_tags=$(git --no-pager tag --sort=-v:refname)
   major_minor=$(echo "$current_tag" | cut -d '.' -f 1,2)
   rc_number=${current_tag//*-rc/}

   case $type in
   NORMAL)
     determine_normal_prev_tag
     ;;
   RC)
  # Check if there's a previous RC within the same minor version and with a lower RC number
  prev_rc_tag=$(get_previous_rc_tag "$major_minor" "$current_tag" "$rc_number" "$prev_tags")
  if [[ -n $prev_rc_tag ]]; then
    prev_tag="$prev_rc_tag"
    fi
   # Fallback to 'NORMAL' logic if no previous tag found
       if [[ -z $prev_tag ]]; then
         determine_normal_prev_tag
        fi
  echo "$prev_tag"
  ;;
ALPHA)
  local alpha_base
  local alpha_version
  local alpha_number
alpha_base=${current_tag##*-alpha[0-9]*}

 if [[ $current_tag =~ -alpha[0-9]+-rc[0-9]+$ ]]; then
   alpha_version=${current_tag%-rc[0-9]*}

   # Check if there's a previous RC within the same alpha version and with a lower RC number
   prev_rc_tag=$(get_previous_rc_tag_for_alpha "$alpha_version" "$current_tag" "$rc_number" "$prev_tags")
   if [[ -z $prev_rc_tag ]]; then
     # If no previous RC found for the same alpha version, find the highest alpha version before the current alpha
     prev_tag=$(get_previous_alpha_tag "$alpha_base" "$alpha_version" "$prev_tags")
     else
     prev_tag=$prev_rc_tag
   fi
  elif [[ $current_tag =~ -alpha[0-9]+$ ]]; then
    alpha_number=$(echo "$current_tag" | grep -oE "alpha[0-9]+$" | sed 's/alpha//')
    prev_tag=$(get_previous_alpha_tag "$alpha_base" "$alpha_number" "$prev_tags")
  fi

  # Fallback to 'NORMAL' logic if no previous tag found
  if [[ -z $prev_tag ]]; then
    determine_normal_prev_tag
  fi
;;

  esac
  echo "$prev_tag"
}

# Function to get the previous RC tag
get_previous_rc_tag() {
  local major_minor=$1
  local current_tag=$2
  local rc_number=$3
  local prev_tags=$4
  local prev_rc_tag
  local awk_script="{split(\$0, a, \"-rc\"); if (\$0 < curTag && \$0 ~ \"-rc[0-9]+$\" && (a[2] + 0 < curRc + 0)) print}"

  prev_rc_tag=$(echo "$prev_tags" | grep -E "^$major_minor\.[0-9]+-rc[0-9]+$" | awk -v curTag="$current_tag" -v curRc="$rc_number" "$awk_script" | sort -n | tail -1)

  echo "$prev_rc_tag"
}

# Function to get the previous RC tag for a given alpha version
get_previous_rc_tag_for_alpha() {
  local alpha_version=$1
  local current_tag=$2
  local rc_number=$3
  local prev_tags=$4
  local prev_rc_tag
  local awk_script="{split(\$0, a, \"-rc\"); if (\$0 < curTag && \$0 ~ \"-rc[0-9]+$\" && (a[2] + 0 < curRc + 0)) print}"

  prev_rc_tag=$(echo "$prev_tags" | grep -E "^$alpha_version-rc[0-9]+$" | awk -v curTag="$current_tag" -v curRc="$rc_number" "$awk_script" | sort -n | tail -1)

  echo "$prev_rc_tag"
}

# Function to get the previous alpha tag
get_previous_alpha_tag() {
  local alpha_base=$1
  local alpha_number=$2
  local prev_tags=$3
  local prev_tag
  local awk_script="BEGIN{FS=\"-alpha\"} \$2+0 < curAlpha+0"

  prev_tag=$(echo "$prev_tags" | grep -E "^$alpha_base-alpha[0-9]+$" | awk -v curAlpha="$alpha_number" "$awk_script" | sort -rV | head -1)

  echo "$prev_tag"
}


# validation for tag format
if [[ ! $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-alpha[0-9]+)?(-rc[0-9]+)?$ ]]; then
  echo "Release tag is invalid"
  exit 1
fi

# Determine the tag type
TYPE=""
if [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  TYPE="NORMAL"
elif [[ $1 =~ -alpha[0-9]+(-rc[0-9]+)?$ ]]; then
  TYPE="ALPHA"
elif [[ $1 =~ -rc[0-9]+$ ]]; then
  TYPE="RC"
fi

if [[ -z $TYPE ]]; then
  echo "Invalid tag format"
  exit 1
fi

# Fetch and pull the tags from the repository
git fetch --tags -f 1>/dev/null
git pull --tags -f 1>/dev/null

# Determine the previous tag
prev_tag=$(determine_prev_tag "$1" "$TYPE")
if [[ -z $prev_tag ]]; then
  echo "No previous tag found for the given rules"
  exit 1
fi

echo "Previous tag: $prev_tag"