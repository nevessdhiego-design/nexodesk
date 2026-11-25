// Tela de carregamento do NexoDesk: um prédio cujas janelas acendem (cada janela é um chamado atendido).
// Aparece uma vez por sessão do navegador, por pelo menos ~1,7 s e some sozinha quando a página termina de
// carregar (com limite de 5 s). Não aparece com "reduzir movimento" nem com ?semcarregamento.
// Montada por JS com createElement (sem HTML nem estilo inline, por causa da CSP).
(function () {
  'use strict';

  var CHAVE = 'nexodesk_carregamento_visto';
  var MINIMO_MS = 1700;
  var LIMITE_MS = 5000;

  function movimentoReduzido() {
    try {
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
        || localStorage.getItem('nexodesk_a11y_movimento') === '1';
    } catch (e) { return false; }
  }
  function jaVisto() {
    try { return sessionStorage.getItem(CHAVE) === '1'; } catch (e) { return false; }
  }
  if (movimentoReduzido() || jaVisto() || /[?&]semcarregamento\b/.test(window.location.search)) return;
  try { sessionStorage.setItem(CHAVE, '1'); } catch (e) { /* sem armazenamento: aparece só desta vez */ }

  var SVG = 'http://www.w3.org/2000/svg';
  function no(tag, atributos, classe) {
    var el = document.createElementNS(SVG, tag);
    Object.keys(atributos || {}).forEach(function (k) { el.setAttribute(k, atributos[k]); });
    if (classe) el.setAttribute('class', classe);
    return el;
  }
  function html(tag, classe, texto) {
    var el = document.createElement(tag);
    if (classe) el.className = classe;
    if (texto) el.textContent = texto;
    return el;
  }

  // prédio: corpo, caixa d'água no topo, 3 x 5 janelas e a porta
  var predio = no('svg', { viewBox: '0 0 120 150', 'aria-hidden': 'true', focusable: 'false' }, 'carregando-predio');
  predio.appendChild(no('rect', { x: 44, y: 6, width: 32, height: 16, rx: 3 }, 'carregando-topo'));
  predio.appendChild(no('rect', { x: 20, y: 20, width: 80, height: 122, rx: 5 }, 'carregando-corpo'));
  var ORDEM = [0, 7, 3, 11, 5, 13, 1, 9, 4, 12, 6, 14, 2, 10, 8]; // a ordem em que as janelas acendem
  for (var i = 0; i < 15; i++) {
    var coluna = i % 3, linha = Math.floor(i / 3);
    var janela = no('rect', { x: 29 + coluna * 22, y: 31 + linha * 20, width: 16, height: 12, rx: 2 }, 'carregando-janela');
    janela.style.animationDelay = (ORDEM.indexOf(i) * 0.09).toFixed(2) + 's'; // CSSOM: permitido pela CSP
    predio.appendChild(janela);
  }
  predio.appendChild(no('rect', { x: 50, y: 126, width: 20, height: 16, rx: 2 }, 'carregando-porta'));
  predio.appendChild(no('line', { x1: 8, y1: 144, x2: 112, y2: 144 }, 'carregando-chao'));

  var marca = html('p', 'carregando-marca', 'Nexo');
  marca.appendChild(html('span', '', 'Desk'));
  var texto = html('p', 'carregando-texto', 'Conectando o condomínio');
  var pontos = html('span', 'carregando-pontos');
  pontos.setAttribute('aria-hidden', 'true');
  ['.', '.', '.'].forEach(function (p) { pontos.appendChild(html('span', '', p)); });
  texto.appendChild(pontos);

  var raiz = html('div', 'carregando');
  raiz.setAttribute('role', 'status');
  raiz.appendChild(predio);
  raiz.appendChild(marca);
  raiz.appendChild(texto);
  raiz.appendChild(html('span', 'carregando-so-leitor', 'Carregando o NexoDesk'));
  document.documentElement.appendChild(raiz);

  var inicio = Date.now();
  var saiu = false;
  function sair() {
    if (saiu) return;
    saiu = true;
    raiz.classList.add('saindo');
    window.setTimeout(function () { if (raiz.parentNode) raiz.parentNode.removeChild(raiz); }, 700);
  }
  function quandoPronta() {
    var falta = MINIMO_MS - (Date.now() - inicio);
    if (falta > 0) window.setTimeout(sair, falta); else sair();
  }
  if (document.readyState === 'complete') quandoPronta(); else window.addEventListener('load', quandoPronta);
  window.setTimeout(sair, LIMITE_MS);
})();
