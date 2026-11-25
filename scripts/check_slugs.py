"""Confere se cada slug do catálogo responde na ESPN e lista as fases (season.slug) do ano.

Uso: python3 -I scripts/check_slugs.py 2026 bra.1 bra.2 ...
"""
import json
import sys
import urllib.request


def main():
    year, slugs = sys.argv[1], sys.argv[2:]
    for slug in slugs:
        url = (f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard"
               f"?dates={year}&lang=pt&region=br")
        req = urllib.request.Request(url, headers={"User-Agent": "champs-check"})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                d = json.load(r)
        except Exception as exc:  # noqa: BLE001 - script de diagnóstico
            print(f"{slug}: ERRO {exc}")
            continue
        if not d.get("leagues"):
            print(f"{slug}: ERRO {d}")
            continue
        phases = []
        for e in d.get("events", []):
            s = e.get("season", {}).get("slug")
            if s not in phases:
                phases.append(s)
        name = d["leagues"][0]["name"]
        print(f"{slug}: {name} | {len(d.get('events', []))} jogos | fases: {phases}")


if __name__ == "__main__":
    main()
