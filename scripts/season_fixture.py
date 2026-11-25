"""Baixa a temporada inteira de um campeonato (mês a mês) e grava um scoreboard enxuto para testes.

Uso: python3 -I scripts/season_fixture.py bra.1 2026 saida.json
"""
import json
import sys
import urllib.request

KEEP_EVENT = ("id", "date", "name", "shortName", "season", "status")
KEEP_COMP = ("id", "date", "altGameNote", "notes", "series", "status")
KEEP_COMPETITOR = ("id", "homeAway", "winner", "score", "shootoutScore")
KEEP_TEAM = ("id", "abbreviation", "displayName", "shortDisplayName", "logo")


def fetch(slug, month):
    url = (f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard"
           f"?dates={month}&lang=pt&region=br")
    req = urllib.request.Request(url, headers={"User-Agent": "champs-fixtures"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def slim(event):
    e = {k: event[k] for k in KEEP_EVENT if k in event}
    comps = []
    for c in event.get("competitions", []):
        sc = {k: c[k] for k in KEEP_COMP if k in c}
        sc["competitors"] = []
        for t in c.get("competitors", []):
            st = {k: t[k] for k in KEEP_COMPETITOR if k in t}
            st["team"] = {k: t["team"][k] for k in KEEP_TEAM if k in t["team"]}
            sc["competitors"].append(st)
        comps.append(sc)
    e["competitions"] = comps
    return e


def main():
    slug, year, out = sys.argv[1], sys.argv[2], sys.argv[3]
    events, seen, leagues = [], set(), None
    for m in range(1, 13):
        d = fetch(slug, f"{year}{m:02d}")
        leagues = leagues or [{k: v for k, v in d["leagues"][0].items() if k in ("id", "name", "slug", "abbreviation")}]
        for ev in d.get("events", []):
            if ev["id"] not in seen:
                seen.add(ev["id"])
                events.append(slim(ev))
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"leagues": leagues, "events": events}, f, ensure_ascii=False)
    print(len(events), "jogos")


if __name__ == "__main__":
    main()
