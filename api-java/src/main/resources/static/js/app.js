// Utilidades compartilhadas pelas páginas. Todo texto vindo do servidor entra na tela com
// textContent (nunca innerHTML), então um título malicioso não executa script (XSS).

function lerCookie(nome) {
  const par = document.cookie.split('; ').find((c) => c.startsWith(nome + '='));
  return par ? decodeURIComponent(par.split('=')[1]) : null;
}

/** Chama a API. Passe json para corpo JSON ou form (FormData) para multipart. */
async function api(caminho, { metodo = 'GET', json, form } = {}) {
  const cabecalhos = { Accept: 'application/json' };
  const token = lerCookie('XSRF-TOKEN');
  if (token) cabecalhos['X-XSRF-TOKEN'] = token;
  let corpo;
  if (json !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
    corpo = JSON.stringify(json);
  } else if (form) {
    corpo = form; // o navegador define o Content-Type com o boundary
  }
  const resposta = await fetch(caminho, { method: metodo, headers: cabecalhos, body: corpo, credentials: 'same-origin' });
  if (resposta.status === 204) return null;
  let dados = null;
  try { dados = await resposta.json(); } catch (e) { /* corpo vazio */ }
  if (!resposta.ok) {
    const erro = new Error((dados && dados.erro) || 'Algo deu errado. Tente novamente.');
    erro.status = resposta.status;
    throw erro;
  }
  return dados;
}

/** Cria elemento: el('div', {class: 'x', text: 'oi'}, filho1, filho2). */
function el(tag, atributos = {}, ...filhos) {
  const no = document.createElement(tag);
  for (const [chave, valor] of Object.entries(atributos)) {
    if (valor === null || valor === undefined || valor === false) continue;
    if (chave === 'text') no.textContent = valor;
    else if (chave === 'class') no.className = valor;
    else if (chave.startsWith('on')) no.addEventListener(chave.slice(2), valor);
    else no.setAttribute(chave, valor === true ? '' : valor);
  }
  for (const filho of filhos.flat()) {
    if (filho !== null && filho !== undefined) no.append(filho);
  }
  return no;
}

function selo(texto, classe) {
  return el('span', { class: 'selo ' + classe, text: texto });
}

function formatarData(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function mostrarMensagem(caixa, texto, tipo) {
  caixa.className = 'mensagem ' + tipo;
  caixa.textContent = texto;
  caixa.classList.remove('hidden');
}

function limparMensagem(caixa) {
  caixa.classList.add('hidden');
  caixa.textContent = '';
}

async function sair() {
  try { await api('/api/auth/logout', { metodo: 'POST' }); } catch (e) { /* já saiu */ }
  window.location.href = '/index.html';
}

/** Cada perfil tem a sua tela inicial. */
function paginaDoPerfil(perfil) {
  if (perfil === 'SINDICO') return '/sindico.html';
  if (perfil === 'FUNCIONARIO') return '/funcionario.html';
  return '/morador.html';
}

/** "20/10/2026" a partir de "2026-10-20" (sem passar por fuso horário, para o dia não andar). */
function formatarDia(iso) {
  if (!iso) return '';
  const [ano, mes, dia] = iso.split('-');
  return dia + '/' + mes + '/' + ano;
}

/** "Responsável: Zé · previsão 20/10/2026", ou texto vazio se ninguém cuida ainda. */
function textoResponsavel(c) {
  if (!c.responsavelNome) return '';
  return 'Responsável: ' + c.responsavelNome + (c.responsavelFuncao ? ' (' + c.responsavelFuncao + ')' : '') + (c.previsao ? ' · previsão ' + formatarDia(c.previsao) : '');
}

/**
 * Checklist do serviço. A equipe (base = '/api/funcionario' ou '/api/sindico') marca as etapas; sem base é só leitura
 * (o morador vê o andamento). aoErro recebe a mensagem se a API recusar.
 */
function blocoTarefas(c, base, aoErro) {
  if (!c.tarefas || !c.tarefas.length) return null;
  const resumo = el('summary', { text: '' });
  const caixas = [];
  const atualizarResumo = () => { resumo.textContent = 'Etapas do serviço (' + caixas.filter((x) => x.checked).length + ' de ' + c.tarefas.length + ')'; };
  const itens = c.tarefas.map((t) => {
    const caixa = el('input', { type: 'checkbox', id: 'tarefa-' + t.id, checked: t.feita, disabled: !base });
    caixas.push(caixa);
    if (base) {
      caixa.addEventListener('change', async () => {
        caixa.disabled = true;
        try {
          await api(base + '/tarefas/' + t.id, { metodo: 'PATCH', json: { feita: caixa.checked } });
        } catch (erro) {
          caixa.checked = !caixa.checked;
          if (aoErro) aoErro(erro.message);
        } finally {
          caixa.disabled = false;
          atualizarResumo();
        }
      });
    }
    return el('li', {}, el('label', { class: 'check', for: 'tarefa-' + t.id }, caixa, el('span', { text: t.texto })));
  });
  atualizarResumo();
  return el('details', { class: 'checklist-bloco', open: true }, resumo, el('ul', { class: 'checklist' }, itens));
}

function figuraDoChamado(legenda, caminho) {
  return el('figure', { class: 'figura' },
    el('img', { class: 'foto', src: caminho, alt: legenda + ': foto do serviço' }),
    el('figcaption', { text: legenda }));
}

/** "Antes" (foto de abertura) e "Depois" (foto do serviço pronto). Com base, a equipe também envia a foto do depois. */
function blocoAntesDepois(c, base, aoEnviar, aoErro) {
  const figuras = [];
  if (c.temImagem) figuras.push(figuraDoChamado('Antes', '/api/chamados/' + c.id + '/imagem'));
  if (c.temImagemConclusao) figuras.push(figuraDoChamado('Depois', '/api/chamados/' + c.id + '/imagem-conclusao'));
  let envio = null;
  if (base) {
    const arquivo = el('input', { type: 'file', id: 'depois-' + c.id, accept: 'image/jpeg,image/png,image/gif' });
    const enviar = el('button', { type: 'button', class: 'secundario-escuro', text: c.temImagemConclusao ? 'Trocar a foto do depois' : 'Enviar foto do serviço pronto' });
    enviar.addEventListener('click', async () => {
      if (!arquivo.files[0]) { if (aoErro) aoErro('Escolha a foto do serviço pronto.'); return; }
      const form = new FormData();
      form.append('imagem', arquivo.files[0]);
      enviar.disabled = true;
      try {
        await api(base + '/chamados/' + c.id + '/conclusao', { metodo: 'POST', form });
        if (aoEnviar) await aoEnviar();
      } catch (erro) {
        if (aoErro) aoErro(erro.message);
        enviar.disabled = false;
      }
    });
    envio = el('details', { class: 'envio-depois' },
      el('summary', { text: c.temImagemConclusao ? 'Trocar a foto do serviço pronto' : 'Anexar foto do serviço pronto' }),
      el('label', { for: 'depois-' + c.id, text: 'Foto (JPG, PNG ou GIF até 5 MB)' }), arquivo, enviar);
  }
  if (!figuras.length && !envio) return null;
  return el('div', { class: 'antes-depois' }, figuras.length ? el('div', { class: 'figuras' }, figuras) : null, envio);
}

/** Selo do prazo: "Vence em 5 h", "Vence em 2 dias" ou "Prazo vencido há 3 h". Nada para chamado resolvido ou sem prazo. */
function seloPrazo(c) {
  if (c.minutosParaPrazo === null || c.minutosParaPrazo === undefined) return null;
  const m = Math.abs(c.minutosParaPrazo);
  const horas = Math.round(m / 60);
  const tempo = m < 60 ? m + ' min' : horas < 48 ? horas + ' h' : Math.round(horas / 24) + ' dias';
  return c.minutosParaPrazo < 0 ? selo('Prazo vencido há ' + tempo, 'ATRASADO') : selo('Vence em ' + tempo, 'neutro');
}

/** Telefones de emergência do Brasil. O NexoDesk NÃO liga para ninguém: só mostra os números (links tel: no celular). */
const TELEFONES_EMERGENCIA = [
  { numero: '190', nome: 'Polícia' },
  { numero: '192', nome: 'SAMU' },
  { numero: '193', nome: 'Bombeiros' },
  { numero: '199', nome: 'Defesa Civil' },
];

function telefonesEmergencia() {
  return el('div', { class: 'telefones-emergencia', role: 'group', 'aria-label': 'Telefones de emergência' },
    el('strong', { text: 'Em emergência, ligue: ' }),
    TELEFONES_EMERGENCIA.map((t) => el('a', { class: 'telefone', href: 'tel:' + t.numero, text: t.numero + ' ' + t.nome })));
}

/** Garante usuário logado com o perfil esperado; senão manda para o login. */
async function exigirPerfil(perfil) {
  try {
    const eu = await api('/api/auth/me');
    if (eu.perfil !== perfil) {
      window.location.href = paginaDoPerfil(eu.perfil);
      return null;
    }
    montarMenu(eu.perfil);
    return eu;
  } catch (e) {
    window.location.href = '/index.html';
    return null;
  }
}


// ---------- aparência: ícones, menu e topo (tudo montado por JS: a CSP não aceita HTML nem estilo inline) ----------

/** Ícone do arquivo /img/icones.svg (desenhado para o projeto). Decorativo: o texto ao lado diz o que é. */
function icone(nome, classe) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'icone' + (classe ? ' ' + classe : ''));
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  const uso = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  uso.setAttribute('href', '/img/icones.svg#i-' + nome);
  svg.append(uso);
  return svg;
}

/** Estado vazio com ícone e uma frase que orienta o que fazer. */
function vazio(texto) {
  return el('div', { class: 'vazio' }, icone('vazio', 'icone-grande'), el('p', { text: texto }));
}

const MENUS = {
  MORADOR: [['/morador.html', 'chamados', 'Meus chamados'], ['/conta.html', 'conta', 'Minha conta']],
  SINDICO: [['/sindico.html', 'casa', 'Painel'], ['/relatorios.html', 'relatorio', 'Relatórios'],
    ['/usuarios.html', 'usuarios', 'Usuários'], ['/conta.html', 'conta', 'Minha conta']],
  FUNCIONARIO: [['/funcionario.html', 'ferramenta', 'Meus atendimentos'], ['/conta.html', 'conta', 'Minha conta']],
};

/** Preenche <nav id="menu"> conforme o perfil e marca a página atual (aria-current). */
function montarMenu(perfil) {
  const nav = document.getElementById('menu');
  if (!nav || !MENUS[perfil]) return;
  nav.replaceChildren(...MENUS[perfil].map(([caminho, nomeIcone, texto]) =>
    el('a', { href: caminho, 'aria-current': window.location.pathname === caminho ? 'page' : null }, icone(nomeIcone), el('span', { text: texto }))));
}

/** Logotipo no topo, ícones nos botões do topo e ícones marcados com data-icone no HTML. */
(function decorar() {
  document.querySelectorAll('.topo .marca').forEach((marca) =>
    marca.prepend(el('img', { class: 'logo', src: '/img/logo.svg', alt: '', width: '28', height: '28' })));
  const sino = document.getElementById('botao-sino');
  if (sino) sino.prepend(icone('sino'));
  const sairBotao = document.getElementById('botao-sair');
  if (sairBotao) sairBotao.prepend(icone('sair'));
  document.querySelectorAll('[data-icone]').forEach((no) => no.prepend(icone(no.dataset.icone)));
})();
