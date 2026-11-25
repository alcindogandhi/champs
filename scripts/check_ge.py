"""Classifica páginas de campeonatos do ge: v1 (API JSON pública), v2 (dados embutidos na página) ou sem tabela.

Uso: python3 -I scripts/check_ge.py arquivo_com_urls.txt   (uma URL de página do ge por linha)
"""
import json, re, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Accept-Encoding": "identity"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r: return r.geturl(), r.read().decode("utf-8", "replace")
    except Exception as e: return url, "ERRO %s" % e
def classify(url):
    url = url.replace("globoesporte.globo.com", "ge.globo.com")
    final, html = fetch(url)
    if html.startswith("ERRO"): return (url, "erro", html[:60], "", "")
    uuid = re.search(r'tUUID:\s*"([0-9a-f-]{36})"', html)
    if uuid:
        m = re.search(r"const classificacao = (\{.*?\});?\s*\n", html, re.S)
        ed = ""
        try: ed = json.loads(m.group(1))["edicao"]["nome"]
        except Exception: pass
        return (url, "v1", ed, uuid.group(1), "")
    m = re.search(r'"getStandingData":\{"status":(\d+)', html)
    if m:
        name = re.search(r'"championshipEdition":\{"name":"([^"]+)"', html)
        eid = re.search(r'"edicaoId":(\d+)', html)
        return (url, "v2", name.group(1) if name else "", eid.group(1) if eid else "", "status " + m.group(1))
    ct = re.search(r'data-bs-content-api="([^"]+)"', html)
    return (url, "sem tabela", ct.group(1) if ct else "", "", final if final != url else "")
urls = [l.strip() for l in open(sys.argv[1]) if l.strip()]
with ThreadPoolExecutor(6) as ex:
    for row in ex.map(classify, urls):
        print(" | ".join(row))
