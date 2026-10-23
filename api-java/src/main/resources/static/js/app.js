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
  const feitas = c.tarefas.filter((t) => t.feita).length;
  const itens = c.tarefas.map((t) => {
    const caixa = el('input', { type: 'checkbox', id: 'tarefa-' + t.id, checked: t.feita, disabled: !base });
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
        }
      });
    }
    return el('li', {}, el('label', { class: 'check', for: 'tarefa-' + t.id }, caixa, el('span', { text: t.texto })));
  });
  return el('details', { class: 'checklist-bloco', open: true },
    el('summary', { text: 'Etapas do serviço (' + feitas + ' de ' + c.tarefas.length + ')' }),
    el('ul', { class: 'checklist' }, itens));
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
    envio = el('div', { class: 'envio-depois' },
      el('label', { for: 'depois-' + c.id, text: 'Foto do serviço pronto (JPG, PNG ou GIF até 5 MB)' }), arquivo, enviar);
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
    return eu;
  } catch (e) {
    window.location.href = '/index.html';
    return null;
  }
}
