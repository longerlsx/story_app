#!/usr/bin/env bash
# Prepare the single pinned ZipVoice reference voice; binaries stay outside Git.
set -euo pipefail
export LC_ALL=C

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VOICE_STORE="$PROJECT_DIR/.local-tts"
AAR_NAME='sherpa-onnx-1.13.8.aar'
MODEL_NAME='sherpa-onnx-zipvoice-distill-int8-zh-en-emilia'
AAR_SHA256='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
MODEL_SHA256='77219c8b40f4ee8d73a7f902305ff6c1128ef9b54461c41b4ca6ed890b6c2803'
VOCODER_SHA256='bcb3b970e384161c4d634f0bb9e999ff1c471b34c9bc0b1049a5014065ed3cc0'
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$AAR_NAME"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"
VOCODER_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx'

usage() {
    cat <<'USAGE'
Usage: scripts/prepare-offline-voice.sh [download-directory]
       scripts/prepare-offline-voice.sh --extracted-models models-directory

No argument: reuse .local-tts downloads/runtime, download missing pinned files.
download-directory: use its AAR, model tar.bz2 and vocos_24khz.onnx, without network.
--extracted-models: reuse an extracted official model directory and vocoder within
models-directory, plus the existing .local-tts/runtime AAR, without network.
All modes validate SHA256 before publishing .local-tts/assets and runtime.
Run this separately from Gradle; successful publication removes managed Kokoro assets.
USAGE
}

if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then usage; exit 0; fi
EXTRACTED_MODELS=''
ALLOW_DOWNLOAD='no'
if [[ $# -eq 2 && "$1" == '--extracted-models' ]]; then
    EXTRACTED_MODELS="$2"
    DOWNLOAD_DIR="$VOICE_STORE/downloads"
elif [[ $# -le 1 && "${1:-}" != --* ]]; then
    DOWNLOAD_DIR="${1:-$VOICE_STORE/downloads}"
    if [[ $# -eq 0 ]]; then ALLOW_DOWNLOAD='yes'; fi
else
    usage >&2
    exit 2
fi
for command_name in curl shasum tar unzip mktemp find sort; do
    command -v "$command_name" >/dev/null || { echo "Missing command: $command_name" >&2; exit 1; }
done
if [[ -n "$EXTRACTED_MODELS" && ! -d "$EXTRACTED_MODELS" ]]; then
    echo "Models directory does not exist: $EXTRACTED_MODELS" >&2; exit 1
fi
if [[ $# -eq 1 && ! -d "$DOWNLOAD_DIR" ]]; then
    echo "Download directory does not exist: $DOWNLOAD_DIR" >&2; exit 1
fi
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

AAR_SOURCE="$DOWNLOAD_DIR/$AAR_NAME"
if [[ -n "$EXTRACTED_MODELS" || ( "$ALLOW_DOWNLOAD" == 'yes' && -f "$VOICE_STORE/runtime/$AAR_NAME" ) ]]; then
    AAR_SOURCE="$VOICE_STORE/runtime/$AAR_NAME"
    verify_file "$AAR_SOURCE" "$AAR_SHA256"
else
    prepare_download "$AAR_NAME" "$AAR_URL" "$AAR_SHA256"
fi
unzip -tq "$AAR_SOURCE" >/dev/null

STAGING_DIR="$(mktemp -d "$VOICE_STORE/.prepare-XXXXXX")"
cleanup() {
    if [[ -d "$STAGING_DIR/previous-model" && ! -e "$VOICE_STORE/assets/$MODEL_NAME" ]]; then
        mv "$STAGING_DIR/previous-model" "$VOICE_STORE/assets/$MODEL_NAME"
    fi
    rm -rf "$STAGING_DIR"
}
trap cleanup EXIT

if [[ -n "$EXTRACTED_MODELS" ]]; then
    SOURCE_MODEL="$EXTRACTED_MODELS/$MODEL_NAME"
    VOCODER_SOURCE="$EXTRACTED_MODELS/vocos_24khz.onnx"
else
    prepare_download "$MODEL_NAME.tar.bz2" "$MODEL_URL" "$MODEL_SHA256"
    prepare_download 'vocos_24khz.onnx' "$VOCODER_URL" "$VOCODER_SHA256"
    mkdir "$STAGING_DIR/unpacked"
    tar -xjf "$DOWNLOAD_DIR/$MODEL_NAME.tar.bz2" -C "$STAGING_DIR/unpacked"
    SOURCE_MODEL="$STAGING_DIR/unpacked/$MODEL_NAME"
    VOCODER_SOURCE="$DOWNLOAD_DIR/vocos_24khz.onnx"
fi

# This also authenticates already-extracted resources without downloading the archive again.
verify_file "$SOURCE_MODEL/encoder.int8.onnx" 'f2de9a761a85e5ddd125dee6e05bad1c7ee92c11b83b4d775dab216a6aa41379'
verify_file "$SOURCE_MODEL/decoder.int8.onnx" '3cc2e08a96610d7ea1b227398e97cdbbe0414499741d3aec0b8113db2a2ab251'
verify_file "$SOURCE_MODEL/tokens.txt" 'ce98c1afc5f7a20c2484dffdd68a1fff0a4a2cc707328833750c4476c37cdbda'
verify_file "$SOURCE_MODEL/lexicon.txt" 'c94b049d970e971acdc2a7e5e7b23ca594222a9d640e417c711f2ad1e7bb7d46'
verify_file "$SOURCE_MODEL/test_wavs/leijun-1.wav" '72e25f11b3fffc402cc9021736e49e7aa7ae97b1edbf514ae1a3ce994013c10d'
verify_file "$VOCODER_SOURCE" "$VOCODER_SHA256"
ESPEAK_SHA256="$(cd "$SOURCE_MODEL" && find espeak-ng-data -type f -exec shasum -a 256 {} + | sort | shasum -a 256 | cut -d ' ' -f 1)"
if [[ "$ESPEAK_SHA256" != '8291f49f39de7ee67e7a383694a5e2b3559bbb4ae9ed657aa1d9d92ffd8f2a01' ]]; then
    echo "eSpeak resource SHA256 mismatch: $ESPEAK_SHA256" >&2; exit 1
fi

STAGED_MODEL="$STAGING_DIR/$MODEL_NAME"
mkdir "$STAGED_MODEL"
for resource in encoder.int8.onnx decoder.int8.onnx tokens.txt lexicon.txt espeak-ng-data; do
    cp -R "$SOURCE_MODEL/$resource" "$STAGED_MODEL/"
done
cp "$SOURCE_MODEL/test_wavs/leijun-1.wav" "$STAGED_MODEL/leijun-1.wav"
cp "$VOCODER_SOURCE" "$STAGED_MODEL/vocos_24khz.onnx"
cp "$AAR_SOURCE" "$STAGING_DIR/$AAR_NAME"
if [[ -e "$VOICE_STORE/assets/$MODEL_NAME" ]]; then
    mv "$VOICE_STORE/assets/$MODEL_NAME" "$STAGING_DIR/previous-model"
fi
mv "$STAGED_MODEL" "$VOICE_STORE/assets/$MODEL_NAME"
mv "$STAGING_DIR/$AAR_NAME" "$VOICE_STORE/runtime/$AAR_NAME"
# Remove only this script's prior managed models, never unrelated assets or source downloads.
rm -rf "$VOICE_STORE/assets/kokoro-int8-multi-lang-v1_1" "$VOICE_STORE/assets/kokoro-multi-lang-v1_1"
echo "Prepared runtime: $VOICE_STORE/runtime/$AAR_NAME"
echo "Prepared single voice: $VOICE_STORE/assets/$MODEL_NAME"
echo 'Dependency sources and maintenance notes: docs/knowledge/offline-voice-dependencies.md'
