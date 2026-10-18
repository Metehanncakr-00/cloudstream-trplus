#!/usr/bin/env python3
"""
legacy_inventory.py

Processes legacy plugins.json from Kraptor123/cs-kraptor, extracts all legacy
metadata, maps known domains, assigns migration statuses, and generates
documentation and machine-readable migration matrices.
"""

import json
import os
import sys

KNOWN_DOMAINS = {
    "AnimeciX": "https://animecix.tv",
    "Animeler": "https://animeler.pw",
    "Animely": "https://animecim.tv",
    "AnimPow": "https://animpow.com",
    "Anizium": "https://anizium.co",
    "AsyaAnimeleri": "https://asyaanimeleri.top",
    "AsyaFanatiklerim": "https://asyafanatiklerim.com",
    "AsyaMinik": "https://asyaminik.com",
    "AsyaWatch": "https://asyawatch.com",
    "BelgeselX": "https://belgeselx.com",
    "CizgiMax": "https://cizgimax.online",
    "CizgiveDizi": "https://cizgivedizi.com",
    "Ddizi": "https://www.ddizi.im",
    "DiziAsia": "https://diziasia.com",
    "DiziAsya": "https://api.diziasya.com",
    "DiziBox": "https://www.dizibox.live",
    "DiziFilmORG": "https://dizifilmizle.to",
    "Dizigecesi": "https://dizigecesi.com",
    "DiziKorea": "https://dizikorea3.com",
    "DiziLife": "https://dizi74.life",
    "Dizilla": "https://dizilla.now",
    "DiziMom": "https://www.dizimom.diy",
    "DiziPal": "https://dizipal.bid",
    "DiziPalOrijinal": "https://dizipal1580.com",
    "Dizipod": "https://dizipod.com",
    "DiziYo": "https://www.diziyo.so",
    "DiziYou": "https://www.diziyou.one",
    "DramaDizilerim": "https://dramadizilerim.com",
    "FilmEkseni": "https://filmekseni.vip",
    "FilmHane": "https://www.filmhane.shop",
    "FilmMakinesi": "https://filmmakinesi.to",
    "FilmModu": "https://www.filmmodu.one",
    "Filmzal": "https://filmzal.me",
    "FullHDFilmizlesene": "https://www.fullhdfilmizlesene.now",
    "GinikoCanli": "https://www.giniko.com",
    "HDFilmCehennemi": "https://www.hdfilmcehennemi.nl",
    "HDFilmDelisi": "https://hdfilmdelisi.one",
    "HDFilmizle": "https://www.hdfilmizle.vip",
    "InatBox": None,  # Blocked / IPTV access control
    "JetFilmizle": "https://jetfilmizle.now",
    "KickTR": "https://kick.com",
    "KraptorPlus": None, # Aggregator / Meta
    "KultFilmler": "https://kultfilmler.net",
    "MirrorVerse": "https://mirrorverse.online",
    "OnePaceTr": "https://onepacetr.net",
    "OpenAnime": "https://openani.me",
    "RareFilmm": "https://rarefilmm.com",
    "RecTV": None,  # Blocked / IPTV access control
    "SeiCode": "https://seiwatch.net",
    "SelcukFlix": "https://selcukflix.com",
    "SetFilmIzle": "https://www.setfilmizle.ltd",
    "SezonlukDizi": "https://sezonlukdizi.cc",
    "SinemaCX": "https://www.sinema.gg",
    "SineWix": None,  # App-only / blocked
    "Sinezy": "https://sinezy.to",
    "TrAnimeIzle": "https://www.tranimeizle.io",
    "TurkAnime": "https://turkanime.co",
    "Turkdizileri": "https://turkdizileri.tv",
    "TvDiziler": "https://tvdiziler.tv",
    "WebDramaTurkey": "https://webdramaturkey2.com",
    "WebteIzle": "https://webteizle.vip",
    "WFilmizle": "https://www.wfilmizle.pw",
    "YabanciDizi": "https://yabancidizi.news",
    "YeniKaynak": "https://www.yenikaynak.com",
    "YesilCamTv": "https://yesilcamtv.com.tr",
    "Youtube": "https://www.youtube.com",
    "YTS": "https://web.yts.gg",
}

BLOCKED_PLUGINS = {
    "InatBox": "IPTV private backend / subscription bypass / reverse engineered token",
    "RecTV": "IPTV private backend / encrypted session tokens",
    "SineWix": "App-only encrypted API without public endpoints",
    "KraptorPlus": "Meta-aggregator with mixed unknown upstream endpoints"
}

def determine_migration_status(name, legacy_status):
    if name in BLOCKED_PLUGINS:
        return "blocked", BLOCKED_PLUGINS[name]
    if legacy_status == 0:
        return "deprecated", "Marked status=0 (Down) in legacy builds repository"
    domain = KNOWN_DOMAINS.get(name)
    if not domain:
        return "unreviewed", "Pending domain and source investigation"
    return "eligible", "Public domain identified, eligible for migration"

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    legacy_file = os.path.join(repo_root, "legacy_plugins_decoded.json")
    if not os.path.exists(legacy_file):
        legacy_file = os.path.join(repo_root, "legacy", "legacy-plugins.json")
    
    if not os.path.exists(legacy_file):
        print(f"Error: {legacy_file} not found!", file=sys.stderr)
        sys.exit(1)

    with open(legacy_file, "r", encoding="utf-8") as f:
        legacy_plugins = json.load(f)

    # Save to legacy/legacy-plugins.json
    out_legacy_json = os.path.join(repo_root, "legacy", "legacy-plugins.json")
    with open(out_legacy_json, "w", encoding="utf-8") as f:
        json.dump(legacy_plugins, f, indent=2, ensure_ascii=False)
    print(f"Saved {len(legacy_plugins)} plugins to {out_legacy_json}")

    # Build Migration Matrix
    migration_matrix = []
    for p in legacy_plugins:
        name = p.get("name", "")
        internal_name = p.get("internalName", name)
        version = p.get("version", 0)
        status = p.get("status", 1)
        lang = p.get("language", "tr")
        tv_types = p.get("tvTypes", [])
        artifact_url = p.get("url", "")
        file_hash = p.get("fileHash", "")
        current_domain = KNOWN_DOMAINS.get(name)
        
        impl_status, notes = determine_migration_status(name, status)

        record = {
            "name": name,
            "internalName": internal_name,
            "legacyVersion": version,
            "legacyStatus": status,
            "language": lang,
            "tvTypes": tv_types,
            "legacyArtifactUrl": artifact_url,
            "legacyHash": file_hash,
            "currentDomain": current_domain,
            "sourceCodeFound": False,
            "sourceRepository": "Kraptor123/cs-kraptor",
            "licenseVerified": False,
            "implementationStatus": impl_status,
            "healthStatus": "unknown",
            "replacementModule": None,
            "notes": notes
        }
        migration_matrix.append(record)

    out_matrix_json = os.path.join(repo_root, "legacy", "migration-matrix.json")
    with open(out_matrix_json, "w", encoding="utf-8") as f:
        json.dump(migration_matrix, f, indent=2, ensure_ascii=False)
    print(f"Saved migration matrix to {out_matrix_json}")

    # Generate Markdown documentation: docs/LEGACY_INVENTORY.md
    out_doc = os.path.join(repo_root, "docs", "LEGACY_INVENTORY.md")
    with open(out_doc, "w", encoding="utf-8") as f:
        f.write("# Legacy Plugin Inventory & Migration Matrix\n\n")
        f.write("Bu doküman, eski `Kraptor123/cs-kraptor` repository'sinden çıkarılan **67 adet** eklentinin dökümünü ve güncel durum analizini içerir.\n\n")
        f.write("## Özet İstatistikler\n\n")
        
        status_counts = {}
        for r in migration_matrix:
            s = r["implementationStatus"]
            status_counts[s] = status_counts.get(s, 0) + 1

        f.write(f"- **Toplam Legacy Eklenti:** {len(migration_matrix)}\n")
        for s, cnt in sorted(status_counts.items()):
            f.write(f"- **{s.capitalize()}:** {cnt}\n")
        f.write("\n---\n\n")
        f.write("## Eklenti Listesi\n\n")
        f.write("| # | Eklenti Adı | Internal Name | Legacy Sürüm | Legacy Status | Dil | Türler | Bilinen Domain | Migration Durumu | Notlar |\n")
        f.write("|---|-------------|---------------|--------------|---------------|-----|--------|----------------|------------------|--------|\n")
        
        for idx, r in enumerate(migration_matrix, 1):
            tv = ", ".join(r["tvTypes"])
            dom = r["currentDomain"] or "-"
            f.write(f"| {idx} | **{r['name']}** | `{r['internalName']}` | {r['legacyVersion']} | {r['legacyStatus']} | {r['language']} | {tv} | {dom} | `{r['implementationStatus']}` | {r['notes']} |\n")

    print(f"Generated inventory documentation: {out_doc}")

if __name__ == "__main__":
    main()
