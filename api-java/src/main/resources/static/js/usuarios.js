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
    el('td', { class: 'nome', text: u.nome + (ehEu ? ' (você)' : '') }),
    el('td', { class: 'email', text: u.email }),
    el('td', { text: u.unidade || '—' }),
    el('td', {}, el('div', { class: 'acoes-linha' }, perfil, funcao)),
    el('td', {}, selo(u.ativo ? 'Ativo' : 'Desativado', u.ativo ? 'RESOLVIDO' : 'neutro')),
    el('td', {}, el('div', { class: 'acoes-linha' }, salvar, alternarAtivo, redefinir)));
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

async function carregar() {
  [todos, funcoes] = await Promise.all([api('/api/sindico/usuarios'), api('/api/sindico/funcoes')]);
  desenhar();
}

busca.addEventListener('input', desenhar);
document.getElementById('botao-sair').addEventListener('click', sair);

(async function iniciar() {
  eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  await carregar();
})();
