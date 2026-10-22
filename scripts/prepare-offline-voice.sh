#!/usr/bin/env bash
# Prepare one pinned Piper model with C/F speakers; binaries stay outside Git.
set -euo pipefail
export LC_ALL=C

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VOICE_STORE="$PROJECT_DIR/.local-tts"
AAR_NAME='sherpa-onnx-1.13.8.aar'
MODEL_NAME='piper-zh-cn-aishell3-v1'
AAR_SHA256='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
MODEL_SHA256='f46e009dcfcaa0f8c5e6eefe23dabc36296c69123ea22df82d31078ab6e0174f'
CONFIG_SHA256='54ec178cb1aa0aea290e41e639240b60b2a5fc55087d9957a718817d9f8bafd6'
LEXICON_SHA256='003e76f89fec389ec109b557f47cc315a1ac31a99091f0cc9d15949f9024416b'
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$AAR_NAME"
MODEL_URL='https://huggingface.co/writeAPoemForYou/piper-voices-zh-AISHELL-3/resolve/main/zh/zh_CN/cn_aishell3/medium/zh_CN-cn_aishell3-medium.onnx'
LEXICON_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-zh-g2pw.txt'
PYTHON_BIN="${PIPER_PYTHON:-python3}"

usage() {
    cat <<'USAGE'
Usage: scripts/prepare-offline-voice.sh [--offline | download-directory]

No argument: reuse .local-tts caches, download missing pinned resources/tools.
--offline: reuse the same cached resources and Python conversion tools, no network.
download-directory: use model.onnx, model.onnx.json, lexicon-zh-g2pw.txt and the
pinned AAR from that directory, without network. The existing runtime AAR is reusable.
The default cache also reuses the original .local-tts/audition-models/piper-cn-aishell3
model/config without modifying them. PIPER_PYTHON may select Python 3.9 through 3.12.
Conversion dependencies (onnx 1.17.0, protobuf 5.29.5, numpy 2.0.2) stay in .local-tts.

All modes verify source SHA256, validate the complete lexicon, and stage all output
before publishing. Run separately from Gradle. Old managed ZipVoice/Kokoro assets
are backed up outside assets and removed only after successful Piper publication.
Source downloads, audition models, APKs and unrelated assets are never removed.
USAGE
}

if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then usage; exit 0; fi
ALLOW_DOWNLOAD='yes'
USE_AUDITION='yes'
DOWNLOAD_DIR="$VOICE_STORE/downloads/$MODEL_NAME"
if [[ $# -eq 1 && "$1" == '--offline' ]]; then
    ALLOW_DOWNLOAD='no'
elif [[ $# -eq 1 && "$1" != --* ]]; then
    ALLOW_DOWNLOAD='no'
    USE_AUDITION='no'
    DOWNLOAD_DIR="$1"
    [[ -d "$DOWNLOAD_DIR" ]] || { echo "Download directory does not exist: $DOWNLOAD_DIR" >&2; exit 1; }
elif [[ $# -ne 0 ]]; then
    usage >&2; exit 2
fi
for command_name in curl shasum unzip mktemp cp mv "$PYTHON_BIN"; do
    command -v "$command_name" >/dev/null || { echo "Missing command: $command_name" >&2; exit 1; }
done
"$PYTHON_BIN" -c 'import sys; sys.exit(0 if (3, 9) <= sys.version_info[:2] <= (3, 12) else "Piper resource conversion requires Python 3.9 through 3.12")'
mkdir -p "$VOICE_STORE/runtime" "$VOICE_STORE/assets"
if [[ "$ALLOW_DOWNLOAD" == 'yes' ]]; then mkdir -p "$DOWNLOAD_DIR"; fi

verify_file() {
    local path="$1" expected="$2" actual
    [[ -f "$path" ]] || { echo "Required resource missing: $path" >&2; exit 1; }
    actual="$(shasum -a 256 "$path" | cut -d ' ' -f 1)"
    if [[ "$actual" != "$expected" ]]; then
        echo "SHA256 mismatch for $path: $actual; existing file left untouched." >&2
        exit 1
    fi
}

prepare_download() {
    local name="$1" url="$2" expected="$3" path="$DOWNLOAD_DIR/$1"
    if [[ ! -f "$path" ]]; then
        [[ "$ALLOW_DOWNLOAD" == 'yes' ]] || { echo "Completed download required: $path" >&2; exit 1; }
        curl --fail --location --retry 3 --connect-timeout 30 --output "$path.partial" "$url"
        verify_file "$path.partial" "$expected"
        mv "$path.partial" "$path"
    fi
    verify_file "$path" "$expected"
}

AAR_SOURCE="$VOICE_STORE/runtime/$AAR_NAME"
if [[ -f "$AAR_SOURCE" ]]; then
    verify_file "$AAR_SOURCE" "$AAR_SHA256"
else
    prepare_download "$AAR_NAME" "$AAR_URL" "$AAR_SHA256"
    AAR_SOURCE="$DOWNLOAD_DIR/$AAR_NAME"
fi
unzip -tq "$AAR_SOURCE" >/dev/null

SOURCE_DIR="$DOWNLOAD_DIR"
if [[ "$USE_AUDITION" == 'yes' && -f "$VOICE_STORE/audition-models/piper-cn-aishell3/model.onnx" ]]; then
    SOURCE_DIR="$VOICE_STORE/audition-models/piper-cn-aishell3"
else
    prepare_download 'model.onnx' "$MODEL_URL" "$MODEL_SHA256"
    prepare_download 'model.onnx.json' "$MODEL_URL.json" "$CONFIG_SHA256"
fi
verify_file "$SOURCE_DIR/model.onnx" "$MODEL_SHA256"
verify_file "$SOURCE_DIR/model.onnx.json" "$CONFIG_SHA256"
prepare_download 'lexicon-zh-g2pw.txt' "$LEXICON_URL" "$LEXICON_SHA256"

# Isolate conversion packages from system/site Python and from APK assets. The
# interpreter suffix prevents accidentally reusing another Python ABI's wheels.
PYTHON_TAG="$("$PYTHON_BIN" -c 'import sys; print(f"py{sys.version_info.major}{sys.version_info.minor}")')"
TOOL_DIR="$VOICE_STORE/tools/piper-resources-$PYTHON_TAG"
if ! PYTHONPATH="$TOOL_DIR" "$PYTHON_BIN" -c 'import onnx, numpy, google.protobuf; assert (onnx.__version__, google.protobuf.__version__, numpy.__version__) == ("1.17.0", "5.29.5", "2.0.2")' >/dev/null 2>&1; then
    if [[ "$ALLOW_DOWNLOAD" != 'yes' ]]; then
        echo "Cached ONNX conversion tools missing for $PYTHON_TAG. Run once without --offline." >&2
        exit 1
    fi
    "$PYTHON_BIN" -m pip install --disable-pip-version-check --no-input --upgrade \
        --target "$TOOL_DIR" --cache-dir "$VOICE_STORE/downloads/pip-cache" \
        'onnx==1.17.0' 'protobuf==5.29.5' 'numpy==2.0.2'
fi

STAGING_DIR="$(mktemp -d "$VOICE_STORE/.prepare-XXXXXX")"
FINAL_MODEL="$VOICE_STORE/assets/$MODEL_NAME"
PUBLISHED='no'
COMMITTED='no'
BACKUP_DIR=''
cleanup() {
    local status="$?"
    set +e
    if [[ "$COMMITTED" != 'yes' ]]; then
        if [[ -d "$STAGING_DIR/previous-model" ]]; then
            rm -rf "$FINAL_MODEL"
            mv "$STAGING_DIR/previous-model" "$FINAL_MODEL"
        elif [[ "$PUBLISHED" == 'yes' ]]; then
            rm -rf "$FINAL_MODEL"
        fi
        if [[ -n "$BACKUP_DIR" ]]; then rm -rf "$BACKUP_DIR"; fi
    fi
    rm -rf "$STAGING_DIR"
    return "$status"
}
trap cleanup EXIT

PYTHONPATH="$TOOL_DIR" "$PYTHON_BIN" "$PROJECT_DIR/scripts/prepare_piper_resources.py" \
    --model "$SOURCE_DIR/model.onnx" --config "$SOURCE_DIR/model.onnx.json" \
    --lexicon "$DOWNLOAD_DIR/lexicon-zh-g2pw.txt" --output "$STAGING_DIR/$MODEL_NAME"
cp "$AAR_SOURCE" "$STAGING_DIR/$AAR_NAME"

# Keep a real rollback copy before retiring only these formerly managed assets.
MANAGED_OLD_MODELS=(sherpa-onnx-zipvoice-distill-int8-zh-en-emilia kokoro-int8-multi-lang-v1_1 kokoro-multi-lang-v1_1)
for old_name in "${MANAGED_OLD_MODELS[@]}"; do
    if [[ -e "$VOICE_STORE/assets/$old_name" ]]; then
        if [[ -z "$BACKUP_DIR" ]]; then
            mkdir -p "$VOICE_STORE/backups"
            BACKUP_DIR="$(mktemp -d "$VOICE_STORE/backups/pre-piper-assets-XXXXXX")"
        fi
        cp -R "$VOICE_STORE/assets/$old_name" "$BACKUP_DIR/"
    fi
done

# Complete-directory rename: no partially written model is ever published.
# On an update, the EXIT trap restores the previous directory if publication fails.
if [[ -e "$FINAL_MODEL" ]]; then mv "$FINAL_MODEL" "$STAGING_DIR/previous-model"; fi
mv "$STAGING_DIR/$MODEL_NAME" "$FINAL_MODEL"
PUBLISHED='yes'
mv "$STAGING_DIR/$AAR_NAME" "$VOICE_STORE/runtime/$AAR_NAME"
COMMITTED='yes'
for old_name in "${MANAGED_OLD_MODELS[@]}"; do
    rm -rf "$VOICE_STORE/assets/$old_name"
done

echo "Prepared runtime: $VOICE_STORE/runtime/$AAR_NAME"
echo "Prepared shared C/F model: $FINAL_MODEL"
if [[ -n "$BACKUP_DIR" ]]; then echo "Preserved old voice resources: $BACKUP_DIR"; fi
shasum -a 256 "$FINAL_MODEL/model.onnx" "$FINAL_MODEL/tokens.txt" \
    "$FINAL_MODEL/lexicon.txt" "$FINAL_MODEL/supported-codepoints.txt"
