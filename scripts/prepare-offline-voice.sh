#!/usr/bin/env bash
# Prepare pinned local voice assets; binary files stay outside Git.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VOICE_STORE="$PROJECT_DIR/.local-tts"
AAR_NAME='sherpa-onnx-1.13.8.aar'
MODEL_NAME='kokoro-multi-lang-v1_1'
AAR_SHA256='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
MODEL_SHA256='a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad'
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$AAR_NAME"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"

usage() {
    cat <<'USAGE'
Usage: scripts/prepare-offline-voice.sh [download-directory]

Without an argument, reuse .local-tts/downloads or download the pinned assets.
With a directory, use its completed AAR and model archive without downloading.
Prepare .local-tts/runtime/ and .local-tts/assets/ before running Gradle.
This prepares the personal-use build; dependency information is kept in docs.
USAGE
}

if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then
    usage
    exit 0
fi
if [[ $# -gt 1 ]]; then
    usage >&2
    exit 2
fi
for command_name in curl shasum tar unzip mktemp; do
    command -v "$command_name" >/dev/null || { echo "Missing command: $command_name" >&2; exit 1; }
done

DOWNLOAD_DIR="${1:-$VOICE_STORE/downloads}"
if [[ $# -eq 1 && ! -d "$DOWNLOAD_DIR" ]]; then
    echo "Download directory does not exist: $DOWNLOAD_DIR" >&2
    exit 1
fi
mkdir -p "$DOWNLOAD_DIR" "$VOICE_STORE/runtime" "$VOICE_STORE/assets"

prepare_download() {
    local name="$1" url="$2" expected="$3" actual
    local path="$DOWNLOAD_DIR/$name"
    if [[ ! -f "$path" ]]; then
        if [[ "$ALLOW_DOWNLOAD" != 'yes' ]]; then
            echo "Completed download required: $path" >&2
            exit 1
        fi
        curl --fail --location --retry 3 --connect-timeout 30 --output "$path.partial" "$url"
        actual="$(LC_ALL=C shasum -a 256 "$path.partial" | cut -d ' ' -f 1)"
        if [[ "$actual" != "$expected" ]]; then
            echo "SHA256 mismatch for $name: $actual" >&2
            rm -f "$path.partial"
            exit 1
        fi
        mv "$path.partial" "$path"
    fi
    actual="$(LC_ALL=C shasum -a 256 "$path" | cut -d ' ' -f 1)"
    if [[ "$actual" != "$expected" ]]; then
        echo "SHA256 mismatch for $path: $actual; existing file left untouched." >&2
        exit 1
    fi
}

ALLOW_DOWNLOAD='no'
if [[ $# -eq 0 ]]; then ALLOW_DOWNLOAD='yes'; fi
prepare_download "$AAR_NAME" "$AAR_URL" "$AAR_SHA256"
prepare_download "$MODEL_NAME.tar.bz2" "$MODEL_URL" "$MODEL_SHA256"

STAGING_DIR="$(mktemp -d "$VOICE_STORE/.prepare-XXXXXX")"
cleanup() {
    if [[ -d "$STAGING_DIR/previous-model" && ! -e "$VOICE_STORE/assets/$MODEL_NAME" ]]; then
        mv "$STAGING_DIR/previous-model" "$VOICE_STORE/assets/$MODEL_NAME"
    fi
    rm -rf "$STAGING_DIR"
}
trap cleanup EXIT

# Validate and stage everything before replacing the last usable resources.
unzip -tq "$DOWNLOAD_DIR/$AAR_NAME" >/dev/null
LC_ALL=C tar -xjf "$DOWNLOAD_DIR/$MODEL_NAME.tar.bz2" -C "$STAGING_DIR"
for required_path in model.onnx voices.bin tokens.txt lexicon-zh.txt lexicon-us-en.txt \
    espeak-ng-data LICENSE date-zh.fst number-zh.fst phone-zh.fst; do
    if [[ ! -e "$STAGING_DIR/$MODEL_NAME/$required_path" ]]; then
        echo "Required model resource missing: $required_path" >&2
        exit 1
    fi
done
cp "$DOWNLOAD_DIR/$AAR_NAME" "$STAGING_DIR/$AAR_NAME"
if [[ -e "$VOICE_STORE/assets/$MODEL_NAME" ]]; then
    mv "$VOICE_STORE/assets/$MODEL_NAME" "$STAGING_DIR/previous-model"
fi
mv "$STAGING_DIR/$MODEL_NAME" "$VOICE_STORE/assets/$MODEL_NAME"
mv "$STAGING_DIR/$AAR_NAME" "$VOICE_STORE/runtime/$AAR_NAME"
# The previous managed model must not remain in assets and double the APK payload.
rm -rf "$VOICE_STORE/assets/kokoro-int8-multi-lang-v1_1"
echo "Prepared runtime: $VOICE_STORE/runtime/$AAR_NAME"
echo "Prepared model: $VOICE_STORE/assets/$MODEL_NAME"
echo 'Dependency sources and maintenance notes: docs/knowledge/offline-voice-dependencies.md'
