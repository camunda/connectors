if [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release tag is valid" >&2
  TYPE=NORMAL
elif [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+.*-rc[0-9]+$ ]]; then
  echo "Release tag is valid (release candidate)" >&2
  TYPE=RC
elif [[ $1 =~ [0-9]+\.[0-9]+\.[0-9]+-alpha[0-9]+$ ]]; then
  echo "Release tag is valid (alpha)" >&2
  TYPE=ALPHA
else
  echo "Release tag is invalid"
  exit 1
fi
CUR_MINOR=$(echo "$1" | cut -d '.' -f 1,2)
PREV_MINOR=$(echo "$1" | cut -d '.' -f 1,2 | awk -F. '{$NF = $NF - 1;} 1' | sed 's/ /./g')

git fetch --tags -f 1>/dev/null
git pull --tags -f 1>/dev/null
PREV_TAGS=$(git --no-pager tag --sort=-creatordate)
NORMAL_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
RC_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+.*-rc[0-9]+$'
ALPHA_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+-alpha[0-9]+$'

# sanity check: the new tag must already exist
if [[ -z $(echo "$PREV_TAGS" | grep "$1") ]]; then
  echo "Tag $1 does not exist" >&2
  exit 1
fi


echo "Current minor: $CUR_MINOR" >&2
echo "Previous minor: $PREV_MINOR" >&2
echo "Type: $TYPE" >&2

if [[ $TYPE == "NORMAL" ]]; then
  # take last normal tag from current minor, if present - otherwise take last normal tag from previous minor
  PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$NORMAL_PATTERN" | grep "$CUR_MINOR" | head -2 | tail -1)
  if [[ -z $PREV_TAG ]]; then
    PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$NORMAL_PATTERN" | grep "$PREV_MINOR" | head -1)
  fi
elif [[ $TYPE == "RC" ]]; then
  # take rc from current minor, if present - otherwise take last normal from previous minor
  PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$RC_PATTERN" | grep "$CUR_MINOR" | head -2 | tail -1)
  if [[ -z $PREV_TAG ]]; then
    PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$NORMAL_PATTERN" | grep "$PREV_MINOR" | head -1)
  fi
elif [[ $TYPE == "ALPHA" ]]; then
  # take alpha from current minor, if present - otherwise take last normal tag from previous minor
  PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$ALPHA_PATTERN" | grep "$CUR_MINOR" | head -2 | tail -1)
  if [[ -z $PREV_TAG ]]; then
    PREV_TAG=$(echo "$PREV_TAGS" | grep -E "$NORMAL_PATTERN" | grep "$PREV_MINOR" | head -1)
  fi
fi

if [[ -z $PREV_TAG ]]; then
  echo "WARNING: No previous tag found for the given rules, taking the previous tag" >&2
  PREV_TAG=$(echo "$PREV_TAGS" | head -2 | tail -1)
fi

echo "Previous tag: $PREV_TAG" >&2
echo $TYPE "$PREV_TAG"
