#!/usr/bin/env python3
"""Prepare the pinned AISHELL-3 Piper model for sherpa-onnx CharacterLexicon.

Run through prepare-offline-voice.sh, which verifies all source hashes and
publishes the completed directory. This helper never edits source downloads.
"""

import argparse
import hashlib
import json
from pathlib import Path


# Mandarin approximations of letter names, not an English phonemizer. The App
# lowercases ASCII letters and separates them with spaces before synthesis.
# CharacterLexicon lowercases lexicon keys on load, but does not lowercase input.
# Explicit words take precedence over similarly named pinyin tokens in sherpa.
LETTER_PHONES = {
    "a": "Ø ei 1 _",                         # 诶
    "b": "b i 4 _",                          # 比 / bee
    "c": "x i 1 _",                          # 西
    "d": "d i 4 _",                          # 迪 / dee
    "e": "y i 1 _",                          # 伊
    "f": "Ø ai 4 _ f u 2 _",                 # 艾弗
    "g": "j i 4 _",                          # 吉 / gee
    "h": "Ø ai 4 _ ch i 3 _",                # 艾尺
    "i": "Ø ai 4 _",                         # 爱
    "j": "j ie 2 _",                         # 杰
    "k": "k ai 1 _",                         # 开
    "l": "Ø ai 4 _ l e 4 _",                 # 艾勒
    "m": "Ø ai 4 _ m u 3 _",                 # 艾姆
    "n": "Ø en 1 _",                         # 恩
    "o": "Ø ou 1 _",                         # 欧
    "p": "p i 1 _",                          # 批
    "q": "q iu 1 _",                         # 丘
    "r": "Ø a 1 _ Ø er 3 _",                 # 阿尔
    "s": "Ø ai 4 _ s i 1 _",                 # 艾丝
    "t": "t i 2 _",                          # 提
    "u": "y ou 1 _",                         # 优
    "v": "w ei 2 _",                         # 维
    "w": "d a 2 _ b u 4 _ l iu 2 _",         # 达布流
    "x": "Ø ai 4 _ k e 4 _ s i 1 _",         # 艾克斯
    "y": "w ai 1 _",                         # 歪
    "z": "z ei 2 _ d e 2 _",                 # 贼德 / zed
}
PUNCTUATION = "。.？！?!—…、，,：:；;"
TONES = {"1", "2", "3", "4", "5"}


def read_tokens(config):
    if (config.get("phoneme_type") != "pinyin"
            or config.get("num_speakers") != 174
            or config.get("audio", {}).get("sample_rate") != 22050):
        raise ValueError("Expected the 174-speaker, 22050 Hz pinyin model config")
    tokens = {}
    for symbol, ids in config["phoneme_id_map"].items():
        if not isinstance(ids, list) or len(ids) != 1 or not isinstance(ids[0], int):
            raise ValueError(f"Expected one integer ID for token {symbol!r}: {ids!r}")
        if not symbol or (any(c.isspace() for c in symbol) and symbol != " "):
            raise ValueError(f"Unsupported token spelling: {symbol!r}")
        if ids[0] < 0:
            raise ValueError(f"Negative token ID: {symbol!r}")
        tokens[symbol] = ids[0]
    if any(tokens.get(k) != v for k, v in {"_": 0, "^": 1, "$": 2}.items()):
        raise ValueError("Unexpected PAD/BOS/EOS IDs")
    return tokens


def validate_phones(word, phones, tokens):
    unknown = sorted(set(phones) - tokens.keys())
    if not phones or unknown:
        raise ValueError(f"Unusable lexicon entry {word!r}: unknown={unknown}, phones={phones}")
    if "^" in phones or "$" in phones:
        raise ValueError(f"Sentence boundaries belong to sherpa, not lexicon entry {word!r}")
    for i, phone in enumerate(phones):
        if phone in TONES and (i + 1 == len(phones) or phones[i + 1] != "_"):
            raise ValueError(f"Missing PAD after tone in {word!r}")
        if phone == "_" and (i == 0 or phones[i - 1] not in TONES | set(PUNCTUATION)):
            raise ValueError(f"Unexpected or repeated PAD in {word!r}")


def read_lexicon(path, tokens):
    entries = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split()
        if len(fields) < 2:
            raise ValueError(f"Empty/malformed lexicon line {line_number}")
        word, phones = fields[0], fields[1:]
        if word in entries:
            raise ValueError(f"Duplicate lexicon key at line {line_number}: {word!r}")
        validate_phones(word, phones, tokens)
        entries[word] = phones
    if not entries:
        raise ValueError("Empty source lexicon")
    return entries


def prepare(model_path, config_path, lexicon_path, output):
    config = json.loads(config_path.read_text(encoding="utf-8"))
    tokens = read_tokens(config)
    entries = read_lexicon(lexicon_path, tokens)
    original_count = len(entries)
    for word, spelling in LETTER_PHONES.items():
        phones = spelling.split()
        validate_phones(word, phones, tokens)
        if word in entries:
            raise ValueError(f"Letter addition would replace source entry {word!r}")
        entries[word] = phones
    for symbol in PUNCTUATION:
        phones = [symbol, "_"]
        validate_phones(symbol, phones, tokens)
        if symbol in entries:
            raise ValueError(f"Punctuation addition would replace source entry {symbol!r}")
        entries[symbol] = phones

    # All single-character entries have already passed token/PAD validation.
    # Do not derive coverage from token keys. Letters require the explicit
    # name entries above; tone digits and boundary tokens are never text.
    codepoints = sorted(ord(word) for word in entries if len(word) == 1)
    import onnx

    if onnx.__version__ != "1.17.0":
        raise ValueError(f"Expected conversion dependency onnx 1.17.0, got {onnx.__version__}")
    model = onnx.load(str(model_path), load_external_data=False)
    if any(t.external_data for t in model.graph.initializer):
        raise ValueError("Expected the self-contained ONNX model, not external weights")
    graph_hash = hashlib.sha256(model.graph.SerializeToString()).digest()
    metadata = {
        "model_type": "vits", "comment": "piper", "language": "Chinese",
        "voice": "zh", "version": "1", "has_espeak": "0", "has_g2pw": "1",
        "n_speakers": "174", "sample_rate": "22050",
    }
    del model.metadata_props[:]
    for key, value in metadata.items():
        entry = model.metadata_props.add()
        entry.key, entry.value = key, value
    if hashlib.sha256(model.graph.SerializeToString()).digest() != graph_hash:
        raise ValueError("Metadata adaptation unexpectedly changed the model graph")

    output.mkdir()  # Refuse to overwrite an existing destination.
    onnx.save(model, str(output / "model.onnx"))
    # Preserve every spelling even when multiple punctuation spellings share ID.
    # A literal space is written as '  72', matching sherpa's official converter.
    (output / "tokens.txt").write_text(
        "".join(f"{symbol} {index}\n" for symbol, index in tokens.items()), encoding="utf-8")
    (output / "lexicon.txt").write_text(
        "".join(f"{word} {' '.join(phones)}\n" for word, phones in entries.items()), encoding="utf-8")
    (output / "supported-codepoints.txt").write_text(
        "".join(f"{codepoint:04X}\n" for codepoint in codepoints), encoding="ascii")
    print(f"Validated {original_count} source words; emitted {len(entries)} words, "
          f"{len(tokens)} token spellings and {len(codepoints)} supported codepoints.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--lexicon", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        prepare(args.model, args.config, args.lexicon, args.output)
    except (ValueError, OSError, ImportError) as error:
        parser.exit(1, f"Piper resource preparation failed: {error}\n")


if __name__ == "__main__":
    main()
