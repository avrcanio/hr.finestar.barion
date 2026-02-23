#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Avoid writing caches under /root/.gradle when environment is restricted.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-user-home}"
mkdir -p "$GRADLE_USER_HOME"

BUILD_FILE="app/build.gradle.kts"
GRADLE_TASK=":app:assembleDebug"
APK_IN="app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="app/build/outputs/apk/debug/pos.finestar.barion-debug-signed.apk"

# Can be overridden from environment.
SECRETS="${POS_SIGNER_SECRETS:-/srv/keystore/qubit-signer.secrets}"
APKSIGNER="${APKSIGNER_PATH:-}"

if [[ ! -f "$BUILD_FILE" ]]; then
  echo "ERROR: $BUILD_FILE not found."
  exit 1
fi

PYTHON_BIN="$(command -v python3 || command -v python || true)"
if [[ -z "$PYTHON_BIN" ]]; then
  echo "ERROR: python3 (or python) not found in PATH."
  exit 1
fi

if [[ -z "$APKSIGNER" ]]; then
  APKSIGNER="$(find /root/Android/Sdk/build-tools -type f -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"
fi

if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner not found. Set APKSIGNER_PATH to a valid binary."
  exit 1
fi

if [[ ! -f "$SECRETS" ]]; then
  echo "ERROR: signer secrets file not found at: $SECRETS"
  echo "Set POS_SIGNER_SECRETS to your secrets file path."
  echo "Detected available file on this host: /srv/keystore/qubit-signer.secrets"
  exit 1
fi

"$PYTHON_BIN" - <<'PY'
import json
import os
import re
from pathlib import Path
from datetime import datetime, timezone

path = Path("app/build.gradle.kts")
text = path.read_text()

vc_match = re.search(r"versionCode\s*=\s*(\d+)", text)
vn_match = re.search(r'versionName\s*=\s*"([0-9]+(?:\.[0-9]+)+)"', text)
if not vc_match or not vn_match:
    lines = [line for line in text.splitlines() if "versionCode" in line or "versionName" in line]
    print("DEBUG: version lines found:")
    for line in lines:
        print(line)
    raise SystemExit("ERROR: versionCode/versionName not found in app/build.gradle.kts")

current_vc = int(vc_match.group(1))
current_vn = vn_match.group(1)

# Persist last bump state so versioning survives branch switches.
gradle_user_home = Path(os.environ.get("GRADLE_USER_HOME", ""))
state_path = (gradle_user_home / "pos_finestar_version_state.json") if str(gradle_user_home) else Path(".gradle-user-home/pos_finestar_version_state.json")

state = {}
if state_path.exists():
    try:
        state = json.loads(state_path.read_text() or "{}")
    except Exception:
        state = {}

raw_state_vc = state.get("versionCode")
state_vc = int(raw_state_vc) if str(raw_state_vc).isdigit() else 0
state_vn = state.get("versionName") if isinstance(state.get("versionName"), str) else None

if state_vc > current_vc:
    base_vc = state_vc
    base_vn = state_vn or current_vn
    base_src = f"state({state_path})"
else:
    base_vc = current_vc
    base_vn = current_vn
    base_src = "app/build.gradle.kts"

next_vc = base_vc + 1

parts = base_vn.split(".")
if len(parts) >= 2 and all(p.isdigit() for p in parts):
    nums = [int(p) for p in parts]
    nums[-1] += 1
    if len(parts) == 2:
        next_vn = f"{nums[0]}.{nums[1]:03d}"
    else:
        next_vn = ".".join(str(n) for n in nums[:-1]) + f".{nums[-1]:03d}"
else:
    next_vn = base_vn

text = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {next_vc}", text, count=1)
text = re.sub(r'versionName\s*=\s*"[0-9]+(?:\.[0-9]+)+"', f'versionName = "{next_vn}"', text, count=1)
path.write_text(text)

state_path.parent.mkdir(parents=True, exist_ok=True)
state_path.write_text(json.dumps({
    "versionCode": next_vc,
    "versionName": next_vn,
    "updatedAt": datetime.now(timezone.utc).isoformat(),
}, indent=2, sort_keys=True))

print(f"Version base: versionCode={base_vc} versionName={base_vn} source={base_src}")
print(f"Version bump: versionCode={next_vc} versionName={next_vn}")
PY

set -a
source "$SECRETS"
set +a

./gradlew "$GRADLE_TASK"

if [[ ! -f "$APK_IN" ]]; then
  echo "ERROR: expected APK not found: $APK_IN"
  exit 1
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE_PATH" \
  --ks-key-alias "$KEYSTORE_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD \
  --key-pass env:KEY_PASSWORD \
  --out "$APK_OUT" \
  "$APK_IN"

"$APKSIGNER" verify --verbose --print-certs "$APK_OUT" >/dev/null
sha256sum "$APK_OUT"
echo "Signed APK: $APK_OUT"
