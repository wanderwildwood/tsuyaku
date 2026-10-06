#!/usr/bin/env python3
"""Write the list of language packs the app offers, with every file's address and size.

    ./build-catalog.py ../app/src/main/assets/catalog.json

The translation models are Mozilla's, the ones Firefox translates pages with, listed by
Mozilla in models.json beside the files themselves. The files for reading text in pictures
are Tesseract's "fast" set, pinned to one commit so an address in the app never moves.

For every language this keeps one model each way (to English and from English), picked as
Offline Translator picks them: a released model over a trial one, then the architecture that
fits a phone best. Each file's download size and unpacked size are read from the server
(the last four bytes of a gzip file are its unpacked length), so the app can say how much a
pack costs before anyone presses anything, and check what arrived.
"""
import json
import struct
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

MODELS = "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json"
TESSDATA_REPO = "tesseract-ocr/tessdata_fast"
TESSDATA_COMMIT = "87416418657359cb625c412a48b6e1d6d41c29bd"

# Offline Translator's catalog_upstream.py: which of several models for one direction to take.
RELEASE = {"Release Android": 4, "Release": 3, "Release Desktop": 2, "Nightly": 1}
ARCH = {"tiny": 1, "base": 2, "base-memory": 3}

# Tesseract's name for each language's script data. Languages missing here have no picture
# reading; the pack is then the translation models alone.
TESS = {
    "af": "afr", "ar": "ara", "az": "aze", "be": "bel", "bg": "bul", "bn": "ben", "bs": "bos",
    "ca": "cat", "cs": "ces", "da": "dan", "de": "deu", "el": "ell", "en": "eng", "es": "spa",
    "et": "est", "eu": "eus", "fa": "fas", "fi": "fin", "fr": "fra", "gl": "glg", "gu": "guj",
    "he": "heb", "hi": "hin", "hr": "hrv", "hu": "hun", "id": "ind", "is": "isl", "it": "ita",
    "ja": "jpn", "kn": "kan", "ko": "kor", "lt": "lit", "lv": "lav", "ml": "mal", "mr": "mar",
    "ms": "msa", "nb": "nor", "nl": "nld", "nn": "nor", "no": "nor", "pl": "pol", "pt": "por",
    "ro": "ron", "ru": "rus", "sk": "slk", "sl": "slv", "sq": "sqi", "sr": "srp", "sv": "swe",
    "sw": "swa", "ta": "tam", "te": "tel", "th": "tha", "tl": "tgl", "tr": "tur", "ug": "uig",
    "uk": "ukr", "ur": "urd", "vi": "vie", "zh": "chi_sim", "zh_hant": "chi_tra",
}

# Serbo-Croatian as one language: Serbian, Croatian and Bosnian are each offered on their own.
SKIP = {"hbs"}

ROLES = {"model": "model", "vocab": "vocab", "srcVocab": "srcVocab", "trgVocab": "trgVocab",
         "lexicalShortlist": "lex"}


def fetch(url, headers=None):
    req = urllib.request.Request(url, headers=headers or {"User-Agent": "tsuyaku-catalog"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.status, dict(r.headers), r.read()


def gzip_sizes(url):
    """(download size, unpacked size) of a gzip file, from its last four bytes alone."""
    status, headers, body = fetch(url, {"User-Agent": "tsuyaku-catalog", "Range": "bytes=-4"})
    if status != 206 or len(body) != 4:
        raise SystemExit(f"no ranged answer from {url}: {status}")
    total = int(headers["Content-Range"].rsplit("/", 1)[1])
    return total, struct.unpack("<I", body)[0]


def best(entries):
    return max(entries, key=lambda e: (RELEASE.get(e.get("releaseStatus"), 0), ARCH.get(e.get("architecture"), 0)))


def main(out):
    _, _, raw = fetch(MODELS)
    manifest = json.loads(raw)
    base = manifest["baseUrl"].rstrip("/")

    _, _, raw = fetch(f"https://api.github.com/repos/{TESSDATA_REPO}/git/trees/{TESSDATA_COMMIT}")
    tess_sizes = {e["path"]: e["size"] for e in json.loads(raw)["tree"] if e["path"].endswith(".traineddata")}

    directions = {}  # (code, "to"|"from") -> chosen entry
    for pair, entries in manifest["models"].items():
        e = best(entries)
        src, trg = e["sourceLanguage"], e["targetLanguage"]
        if src in SKIP or trg in SKIP:
            continue
        if trg == "en":
            directions[(src, "toEnglish")] = e
        elif src == "en":
            directions[(trg, "fromEnglish")] = e
        else:
            print(f"skipping {pair}: neither side is English", file=sys.stderr)

    jobs = []
    for (code, way), e in directions.items():
        for key, f in e["files"].items():
            jobs.append((code, way, key, f))

    with ThreadPoolExecutor(8) as pool:
        sizes = list(pool.map(lambda j: gzip_sizes(f"{base}/{j[3]['path']}"), jobs))

    packs = {}
    for (code, way, key, f), (gz, size) in zip(jobs, sizes):
        want = f.get("uncompressedSize")
        if want is not None and want != size:
            raise SystemExit(f"{f['path']}: manifest says {want} bytes, gzip says {size}")
        e = directions[(code, way)]
        d = packs.setdefault(code, {}).setdefault(way, {
            "arch": e["architecture"],
            "released": (e.get("releaseStatus") or "").startswith("Release"),
            "files": [],
        })
        item = {"role": ROLES[key], "path": f["path"], "gz": gz, "size": size}
        if f.get("uncompressedHash"):
            item["sha256"] = f["uncompressedHash"]
        d["files"].append(item)

    order = {"model": 0, "vocab": 1, "srcVocab": 1, "trgVocab": 2, "lex": 3}
    languages = []
    for code in sorted(packs):
        p = packs[code]
        for d in p.values():
            d["files"].sort(key=lambda x: order[x["role"]])
        lang = {"code": code, **p}
        tess = TESS.get(code)
        if tess and f"{tess}.traineddata" in tess_sizes:
            lang["tess"] = tess
            lang["tessSize"] = tess_sizes[f"{tess}.traineddata"]
        else:
            print(f"{code}: no picture reading", file=sys.stderr)
        languages.append(lang)

    catalog = {
        "generated": manifest["generated"],
        "modelBase": base,
        "tessBase": f"https://raw.githubusercontent.com/{TESSDATA_REPO}/{TESSDATA_COMMIT}",
        "english": {"tess": "eng", "tessSize": tess_sizes["eng.traineddata"]},
        "languages": languages,
    }
    with open(out, "w") as f:
        json.dump(catalog, f, indent=1, sort_keys=False)
        f.write("\n")
    print(f"{len(languages)} languages, Mozilla list of {manifest['generated']}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1])
