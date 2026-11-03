// Gestão de usuários (só síndico). Todo texto vindo do servidor entra na tela com textContent.
const caixa = document.getElementById('mensagem');
const corpo = document.getElementById('lista-usuarios');
const busca = document.getElementById('busca');
let todos = [];
let funcoes = [];   // vem da API (/api/sindico/funcoes): uma só fonte para o que cada função cobre
let eu = null;

const PERFIS = [
  { valor: 'MORADOR', rotulo: 'Morador' },
  { valor: 'FUNCIONARIO', rotulo: 'Funcionário' },
  { valor: 'SINDICO', rotulo: 'Síndico' },
];

function rotuloPerfil(valor) {
  const p = PERFIS.find((x) => x.valor === valor);
  return p ? p.rotulo : valor;
}

async function alterar(usuario, mudanca, textoSucesso) {
  limparMensagem(caixa);
  try {
    await api('/api/sindico/usuarios/' + usuario.id, { metodo: 'PATCH', json: mudanca });
    mostrarMensagem(caixa, textoSucesso, 'ok');
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  }
  await carregar();
}

/** Iniciais para o avatar (decorativo; o nome completo está na célula). */
function iniciais(nome) {
  const partes = nome.replace(/\(.*?\)/g, '').trim().split(/\s+/).filter(Boolean);
  return ((partes[0] || '?')[0] + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
}

function linha(u) {
  const ehEu = u.email === eu.email;
  const perfil = el('select', { id: 'perfil-' + u.id, 'aria-label': 'Perfil de ' + u.nome, disabled: ehEu },
    PERFIS.map((p) => el('option', { value: p.valor, text: p.rotulo, selected: p.valor === u.perfil })));
  // a função (zelador, eletricista...) só vale para funcionário
  const funcao = el('select', { id: 'funcao-' + u.id, 'aria-label': 'Função de ' + u.nome, disabled: ehEu || u.perfil !== 'FUNCIONARIO' },
    funcoes.map((f) => el('option', { value: f.codigo, text: f.rotulo, selected: f.codigo === (u.funcao || 'ZELADOR') })));
  perfil.addEventListener('change', () => { funcao.disabled = ehEu || perfil.value !== 'FUNCIONARIO'; });
  const salvar = el('button', { type: 'button', text: 'Salvar', disabled: ehEu, 'aria-label': 'Salvar perfil e função de ' + u.nome });
  salvar.addEventListener('click', () => {
    const mudanca = { perfil: perfil.value };
    if (perfil.value === 'FUNCIONARIO') mudanca.funcao = funcao.value;
    if (perfil.value === u.perfil && (perfil.value !== 'FUNCIONARIO' || funcao.value === u.funcao)) {
      mostrarMensagem(caixa, 'Nada mudou para ' + u.nome + '.', 'ok');
      return;
    }
    const rotuloFuncao = funcoes.find((f) => f.codigo === funcao.value);
    alterar(u, mudanca, u.nome + ' agora é ' + (perfil.value === 'FUNCIONARIO' && rotuloFuncao ? rotuloFuncao.rotulo : rotuloPerfil(perfil.value)) + '.');
  });

  const alternarAtivo = el('button', {
    type: 'button',
    class: u.ativo ? 'perigo' : '',
    text: u.ativo ? 'Desativar' : 'Reativar',
    disabled: ehEu,
    'aria-label': (u.ativo ? 'Desativar ' : 'Reativar ') + u.nome,
  });
  alternarAtivo.addEventListener('click', () => {
    if (u.ativo && !window.confirm('Desativar ' + u.nome + '? A pessoa perde o acesso e as sessões abertas são encerradas.')) return;
    alterar(u, { ativo: !u.ativo }, u.nome + (u.ativo ? ' foi desativado.' : ' foi reativado.'));
  });

  // "esqueci minha senha": só para morador e zelador ativos; a senha temporária aparece uma vez, no aviso do alto
  const redefinir = el('button', {
    type: 'button', class: 'secundario-escuro', text: 'Redefinir senha',
    disabled: ehEu || u.perfil === 'SINDICO' || !u.ativo, 'aria-label': 'Redefinir a senha de ' + u.nome,
  });
  redefinir.addEventListener('click', async () => {
    if (!window.confirm('Gerar uma senha temporária para ' + u.nome + '? A senha atual deixa de valer e as sessões abertas dela são encerradas.')) return;
    limparMensagem(caixa);
    try {
      const r = await api('/api/sindico/usuarios/' + u.id + '/redefinir-senha', { metodo: 'POST' });
      mostrarMensagem(caixa, 'Senha temporária de ' + r.nome + ': ' + r.senhaTemporaria
        + '. Anote e entregue pessoalmente: ela aparece só desta vez. Peça para trocar em "Minha conta" no primeiro acesso.', 'ok');
    } catch (erro) {
      mostrarMensagem(caixa, erro.message, 'erro');
    }
  });

  return el('tr', { class: u.ativo ? '' : 'inativo' },
    el('td', { class: 'nome', 'data-rotulo': 'Nome' },
      el('span', { class: 'avatar', 'aria-hidden': 'true', text: iniciais(u.nome) }),
      el('span', { text: u.nome + (ehEu ? ' (você)' : '') })),
    el('td', { class: 'email', 'data-rotulo': 'E-mail', text: u.email }),
    el('td', { 'data-rotulo': 'Unidade', text: u.unidade || '—' }),
    el('td', { 'data-rotulo': 'Perfil e função' }, el('div', { class: 'acoes-linha' }, perfil, funcao)),
    el('td', { 'data-rotulo': 'Situação' }, selo(u.ativo ? 'Ativo' : 'Desativado', u.ativo ? 'RESOLVIDO' : 'neutro')),
    el('td', { 'data-rotulo': 'Ações' }, el('div', { class: 'acoes-linha' }, salvar, alternarAtivo, redefinir)));
}

function desenhar() {
  const termo = busca.value.trim().toLowerCase();
  const filtrados = todos.filter((u) => !termo || u.nome.toLowerCase().includes(termo) || u.email.toLowerCase().includes(termo));
  corpo.replaceChildren(...(filtrados.length
    ? filtrados.map(linha)
    : [el('tr', {}, el('td', { colspan: '6', class: 'vazio', text: 'Nenhum usuário encontrado.' }))]));
  const ativos = todos.filter((u) => u.ativo).length;
  document.getElementById('contagem').textContent = filtrados.length + ' de ' + todos.length + ' usuários · ' + ativos + ' ativos';
}

const DIAS = [[1, 'Seg'], [2, 'Ter'], [3, 'Qua'], [4, 'Qui'], [5, 'Sex'], [6, 'Sáb'], [7, 'Dom']];
const caixaEquipe = document.getElementById('mensagem-equipe');

function funcionarios() {
  return todos.filter((u) => u.perfil === 'FUNCIONARIO' && u.ativo);
}

async function carregarEquipe() {
  const pessoas = funcionarios();
  for (const id of ['aus-pessoa', 'esc-pessoa']) {
    const seletor = document.getElementById(id);
    const atual = seletor.value;
    seletor.replaceChildren(...pessoas.map((u) => el('option', { value: String(u.id), text: u.nome + (u.funcaoRotulo ? ' (' + u.funcaoRotulo + ')' : ''), selected: String(u.id) === atual })));
  }
  mostrarEscala();
  const lista = await api('/api/sindico/ausencias');
  document.getElementById('lista-ausencias').replaceChildren(...(lista.length
    ? lista.map((a) => {
      const remover = el('button', { type: 'button', class: 'link', text: 'Remover', 'aria-label': 'Remover ' + a.tipoRotulo.toLowerCase() + ' de ' + a.nome });
      remover.addEventListener('click', async () => {
        try { await api('/api/sindico/ausencias/' + a.id, { metodo: 'DELETE' }); await carregarEquipe(); }
        catch (erro) { mostrarMensagem(caixaEquipe, erro.message, 'erro'); }
      });
      return el('li', {}, el('span', { text: a.nome + ': ' + a.tipoRotulo + ' de ' + formatarDia(a.de) + ' a ' + formatarDia(a.ate) + (a.observacao ? ' (' + a.observacao + ')' : '') + ' ' }), remover);
    })
    : [el('li', { class: 'muted', text: 'Nenhuma ausência registrada.' })]));
}

/** Marca os dias da semana da pessoa escolhida (sem restrição = os sete dias). */
function mostrarEscala() {
  const pessoa = funcionarios().find((u) => String(u.id) === document.getElementById('esc-pessoa').value);
  const dias = pessoa && pessoa.diasTrabalho ? pessoa.diasTrabalho.split(',').map(Number) : DIAS.map((d) => d[0]);
  document.getElementById('dias-semana').replaceChildren(el('legend', { text: 'Dias de trabalho' }),
    ...DIAS.map(([n, nome]) => el('label', { class: 'check dia', for: 'dia-' + n }, el('input', { type: 'checkbox', id: 'dia-' + n, value: String(n), checked: dias.includes(n) }), el('span', { text: nome }))));
}

document.getElementById('esc-pessoa').addEventListener('change', mostrarEscala);
document.getElementById('form-ausencia').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixaEquipe);
  try {
    await api('/api/sindico/usuarios/' + document.getElementById('aus-pessoa').value + '/ausencias', { metodo: 'POST', json: {
      tipo: document.getElementById('aus-tipo').value, de: document.getElementById('aus-de').value, ate: document.getElementById('aus-ate').value,
      observacao: document.getElementById('aus-obs').value } });
    e.target.reset();
    mostrarMensagem(caixaEquipe, 'Ausência registrada. O painel deixa de indicar essa pessoa nesse período.', 'ok');
    await carregarEquipe();
  } catch (erro) { mostrarMensagem(caixaEquipe, erro.message, 'erro'); }
});
document.getElementById('form-escala').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixaEquipe);
  const dias = [...document.querySelectorAll('#dias-semana input:checked')].map((c) => Number(c.value));
  try {
    await api('/api/sindico/usuarios/' + document.getElementById('esc-pessoa').value + '/escala', { metodo: 'PUT', json: { dias } });
    mostrarMensagem(caixaEquipe, 'Escala salva.', 'ok');
    todos = await api('/api/sindico/usuarios');
    mostrarEscala();
  } catch (erro) { mostrarMensagem(caixaEquipe, erro.message, 'erro'); }
});

async function carregar() {
  [todos, funcoes] = await Promise.all([api('/api/sindico/usuarios'), api('/api/sindico/funcoes')]);
  desenhar();
  await carregarEquipe();
}

busca.addEventListener('input', desenhar);
document.getElementById('botao-sair').addEventListener('click', sair);

(async function iniciar() {
  eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  await carregar();
})();
