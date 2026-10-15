// Painel de acessibilidade do NexoDesk (adaptado do widget do protótipo "aa").
// Diferenças do original: o painel é montado por JS (sem HTML nem estilo inline, por causa da CSP),
// não depende de fonte externa, e o filtro de "simulação de daltonismo" foi removido porque piorava a
// leitura para quem usa o sistema (ele serve a designers, não a usuários).
// As escolhas ficam só neste navegador (localStorage); se ele estiver bloqueado, tudo continua funcionando.
(function () {
  'use strict';

  var CHAVES = {
    fonte: 'nexodesk_a11y_fonte',
    contraste: 'nexodesk_a11y_contraste',
    escuro: 'nexodesk_a11y_escuro',
    movimento: 'nexodesk_a11y_movimento',
    legivel: 'nexodesk_a11y_legivel',
  };
  var FONTE_MIN = 80, FONTE_MAX = 150, FONTE_PASSO = 10, FONTE_PADRAO = 100;
  var CLASSES = {
    contraste: 'a11y-contraste',
    escuro: 'a11y-escuro',
    movimento: 'a11y-sem-movimento',
    legivel: 'a11y-fonte-legivel',
  };

  function ler(chave) {
    try { return localStorage.getItem(chave); } catch (e) { return null; }
  }
  function gravar(chave, valor) {
    try { localStorage.setItem(chave, String(valor)); } catch (e) { /* sem armazenamento: ignora */ }
  }
  function lerFonte() {
    var v = parseInt(ler(CHAVES.fonte), 10);
    return isNaN(v) ? FONTE_PADRAO : Math.min(FONTE_MAX, Math.max(FONTE_MIN, v));
  }
  function aplicarFonte(pct) {
    document.documentElement.style.fontSize = pct + '%'; // CSSOM: permitido pela CSP
    var rotulo = document.getElementById('a11y-fonte-rotulo');
    if (rotulo) rotulo.textContent = pct + '%';
  }
  function aplicarClasse(nome, ligado) {
    document.documentElement.classList.toggle(CLASSES[nome], !!ligado);
  }
  function ligado(nome) { return ler(CHAVES[nome]) === '1'; }

  // Aplica antes de a página desenhar, para não piscar o tema claro.
  aplicarFonte(lerFonte());
  Object.keys(CLASSES).forEach(function (n) { aplicarClasse(n, ligado(n)); });

  function el(tag, atributos, texto) {
    var no = document.createElement(tag);
    Object.keys(atributos || {}).forEach(function (k) { no.setAttribute(k, atributos[k]); });
    if (texto) no.textContent = texto;
    return no;
  }

  function opcao(nome, rotulo) {
    var id = 'a11y-' + nome;
    var caixa = el('input', { type: 'checkbox', id: id });
    caixa.checked = ligado(nome);
    caixa.addEventListener('change', function () {
      gravar(CHAVES[nome], caixa.checked ? '1' : '0');
      aplicarClasse(nome, caixa.checked);
    });
    var linha = el('label', { 'class': 'a11y-opcao', 'for': id });
    linha.append(caixa, el('span', {}, rotulo));
    return linha;
  }

  function montar() {
    var botao = el('button', {
      type: 'button', id: 'a11y-botao', 'aria-expanded': 'false', 'aria-controls': 'a11y-painel',
      'aria-label': 'Opções de acessibilidade', title: 'Acessibilidade',
    }, 'Aa');

    var painel = el('div', { id: 'a11y-painel', role: 'dialog', 'aria-labelledby': 'a11y-titulo', hidden: '' });
    var cabecalho = el('div', { 'class': 'a11y-cabecalho' });
    var fechar = el('button', { type: 'button', id: 'a11y-fechar', 'aria-label': 'Fechar painel' }, '×');
    cabecalho.append(el('h2', { id: 'a11y-titulo' }, 'Acessibilidade'), fechar);

    var menos = el('button', { type: 'button', 'class': 'a11y-btn', 'aria-label': 'Diminuir o texto' }, 'A−');
    var mais = el('button', { type: 'button', 'class': 'a11y-btn', 'aria-label': 'Aumentar o texto' }, 'A+');
    var rotulo = el('span', { id: 'a11y-fonte-rotulo', 'aria-live': 'polite' }, lerFonte() + '%');
    var linhaFonte = el('div', { 'class': 'a11y-linha' });
    linhaFonte.append(menos, rotulo, mais);
    var secFonte = el('div', { 'class': 'a11y-secao' });
    secFonte.append(el('h3', {}, 'Tamanho do texto'), linhaFonte);

    var secModos = el('div', { 'class': 'a11y-secao' });
    secModos.append(
      el('h3', {}, 'Modos'),
      opcao('contraste', 'Alto contraste'),
      opcao('escuro', 'Modo escuro'),
      opcao('movimento', 'Reduzir animações'),
      opcao('legivel', 'Fonte mais legível'));

    var restaurar = el('button', { type: 'button', id: 'a11y-restaurar' }, 'Restaurar padrões');
    painel.append(cabecalho, secFonte, secModos, restaurar);

    function abrir(aberto) {
      painel.hidden = !aberto;
      botao.setAttribute('aria-expanded', aberto ? 'true' : 'false');
      if (aberto) fechar.focus(); else botao.focus();
    }

    botao.addEventListener('click', function () { abrir(painel.hidden); });
    fechar.addEventListener('click', function () { abrir(false); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !painel.hidden) abrir(false);
    });
    menos.addEventListener('click', function () {
      var p = Math.max(FONTE_MIN, lerFonte() - FONTE_PASSO); gravar(CHAVES.fonte, p); aplicarFonte(p);
    });
    mais.addEventListener('click', function () {
      var p = Math.min(FONTE_MAX, lerFonte() + FONTE_PASSO); gravar(CHAVES.fonte, p); aplicarFonte(p);
    });
    restaurar.addEventListener('click', function () {
      gravar(CHAVES.fonte, FONTE_PADRAO); aplicarFonte(FONTE_PADRAO);
      Object.keys(CLASSES).forEach(function (n) {
        gravar(CHAVES[n], '0'); aplicarClasse(n, false);
        var caixa = document.getElementById('a11y-' + n);
        if (caixa) caixa.checked = false;
      });
    });

    document.body.append(botao, painel);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', montar);
  else montar();
})();
