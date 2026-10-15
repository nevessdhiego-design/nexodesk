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

/** Garante usuário logado com o perfil esperado; senão manda para o login. */
async function exigirPerfil(perfil) {
  try {
    const eu = await api('/api/auth/me');
    if (eu.perfil !== perfil) {
      window.location.href = eu.perfil === 'SINDICO' ? '/sindico.html' : '/morador.html';
      return null;
    }
    return eu;
  } catch (e) {
    window.location.href = '/index.html';
    return null;
  }
}
