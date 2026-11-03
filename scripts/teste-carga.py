"""Teste de carga simples do NexoDesk (só biblioteca padrão do Python).

Simula N moradores ao mesmo tempo (login, ver os próprios chamados, ver avisos, abrir chamado, ver o detalhe) e alguns
síndicos vendo a fila e as estatísticas, e imprime tempo médio, p50, p95 e máximo por tipo de requisição.

Uso (com o sistema no ar, por exemplo com o perfil demo):
    $env:NEXODESK_CARGA_MORADOR = 'ana.demo@nexodesk.local'
    $env:NEXODESK_CARGA_SINDICO = 'sindico@nexodesk.local'
    $env:NEXODESK_CARGA_SENHA   = '<senha das contas de demonstração>'        # nunca fica em arquivo
    $env:NEXODESK_CARGA_SENHA_SINDICO = '<senha do síndico>'
    python scripts/teste-carga.py --base http://127.0.0.1:8080 --usuarios 25 --rodadas 3

Cria chamados de verdade (títulos "Carga ..."): use um banco de demonstração. Termina com código 1 se algum p95 passar do limite
(padrão 3 s, o RNF01) ou se houver erro.
"""
import argparse
import http.cookiejar
import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request


class Cliente:
    def __init__(self, base):
        self.base = base
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _csrf(self):
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return c.value
        return None

    def chamar(self, metodo, caminho, corpo=None, multipart=None):
        cab = {"Accept": "application/json"}
        if self._csrf():
            cab["X-XSRF-TOKEN"] = self._csrf()
        dados = None
        if corpo is not None:
            dados = json.dumps(corpo).encode()
            cab["Content-Type"] = "application/json"
        if multipart is not None:
            lim = "----carga"
            dados = ("".join(f'--{lim}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n' for k, v in multipart.items())
                     + f"--{lim}--\r\n").encode("utf-8")
            cab["Content-Type"] = f"multipart/form-data; boundary={lim}"
        req = urllib.request.Request(self.base + caminho, data=dados, method=metodo, headers=cab)
        with self.op.open(req, timeout=30) as r:
            texto = r.read()
        try:
            return json.loads(texto) if texto else None
        except ValueError:
            return None


class Medidas:
    def __init__(self):
        self.tempos = {}
        self.erros = {}
        self.trava = threading.Lock()

    def medir(self, nome, fn):
        t = time.perf_counter()
        try:
            resultado = fn()
            ok = True
        except (urllib.error.URLError, OSError, ValueError) as e:
            resultado, ok = None, False
            with self.trava:
                self.erros.setdefault(nome, []).append(str(e)[:80])
        with self.trava:
            self.tempos.setdefault(nome, []).append(time.perf_counter() - t)
        return resultado if ok else None


def usuario(base, email, senha, rodadas, medidas, papel):
    c = Cliente(base)
    c.chamar("GET", "/index.html")
    medidas.medir("login (bcrypt)", lambda: c.chamar("POST", "/api/auth/login", {"email": email, "senha": senha}))
    for i in range(rodadas):
        if papel == "morador":
            medidas.medir("meus chamados", lambda: c.chamar("GET", "/api/chamados/meus"))
            medidas.medir("avisos", lambda: c.chamar("GET", "/api/notificacoes"))
            criado = medidas.medir("abrir chamado (com o Python)", lambda: c.chamar("POST", "/api/chamados", multipart={
                "titulo": f"Carga {threading.get_ident()}-{i}: lampada queimada no corredor",
                "descricao": "Chamado criado pelo teste de carga.", "categoria": "ILUMINACAO", "anonimo": "false", "emergencia": "false"}))
            if criado:
                medidas.medir("detalhe do chamado", lambda: c.chamar("GET", f"/api/chamados/{criado['id']}"))
        else:
            medidas.medir("fila do síndico", lambda: c.chamar("GET", "/api/sindico/chamados?status=todos"))
            medidas.medir("estatísticas", lambda: c.chamar("GET", "/api/sindico/estatisticas"))
            medidas.medir("relatório do mês", lambda: c.chamar("GET", "/api/sindico/relatorio"))


def p95(valores):
    ordenados = sorted(valores)
    return ordenados[max(0, int(len(ordenados) * 0.95) - 1)]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    ap.add_argument("--usuarios", type=int, default=25, help="moradores ao mesmo tempo")
    ap.add_argument("--sindicos", type=int, default=3, help="síndicos ao mesmo tempo")
    ap.add_argument("--rodadas", type=int, default=3, help="repetições por usuário")
    ap.add_argument("--limite", type=float, default=3.0, help="p95 máximo aceito, em segundos")
    a = ap.parse_args()

    morador = os.environ.get("NEXODESK_CARGA_MORADOR")
    sindico = os.environ.get("NEXODESK_CARGA_SINDICO")
    senha = os.environ.get("NEXODESK_CARGA_SENHA")
    senha_sindico = os.environ.get("NEXODESK_CARGA_SENHA_SINDICO", senha)
    if not (morador and sindico and senha):
        print("Defina NEXODESK_CARGA_MORADOR, NEXODESK_CARGA_SINDICO e NEXODESK_CARGA_SENHA (veja o topo deste arquivo).")
        return 2

    medidas = Medidas()
    threads = ([threading.Thread(target=usuario, args=(a.base, morador, senha, a.rodadas, medidas, "morador")) for _ in range(a.usuarios)]
               + [threading.Thread(target=usuario, args=(a.base, sindico, senha_sindico, a.rodadas, medidas, "sindico")) for _ in range(a.sindicos)])
    inicio = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    total = time.perf_counter() - inicio

    pedidos = sum(len(v) for v in medidas.tempos.values())
    print(f"{a.usuarios} moradores + {a.sindicos} síndicos ao mesmo tempo, {a.rodadas} rodadas: {pedidos} requisições em {total:.1f} s "
          f"({pedidos / total:.1f} req/s)\n")
    print(f"{'requisição':32s} {'n':>5s} {'média':>8s} {'p50':>8s} {'p95':>8s} {'máx':>8s} {'erros':>6s}")
    pior = 0.0
    for nome in sorted(medidas.tempos):
        v = medidas.tempos[nome]
        erros = len(medidas.erros.get(nome, []))
        pior = max(pior, p95(v))
        print(f"{nome:32s} {len(v):5d} {statistics.mean(v):7.3f}s {statistics.median(v):7.3f}s {p95(v):7.3f}s {max(v):7.3f}s {erros:6d}")
    total_erros = sum(len(v) for v in medidas.erros.values())
    print(f"\nPior p95: {pior:.3f} s (limite {a.limite} s). Erros: {total_erros}.")
    return 1 if pior > a.limite or total_erros else 0


if __name__ == "__main__":
    sys.exit(main())
