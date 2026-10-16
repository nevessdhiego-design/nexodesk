// Gestão de usuários (só síndico). Todo texto vindo do servidor entra na tela com textContent.
const caixa = document.getElementById('mensagem');
const corpo = document.getElementById('lista-usuarios');
const busca = document.getElementById('busca');
let todos = [];
let eu = null;

const PERFIS = [
  { valor: 'MORADOR', rotulo: 'Morador' },
  { valor: 'ZELADOR', rotulo: 'Zelador' },
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
  const salvar = el('button', { type: 'button', text: 'Salvar perfil', disabled: ehEu, 'aria-label': 'Salvar perfil de ' + u.nome });
  salvar.addEventListener('click', () => {
    if (perfil.value === u.perfil) {
      mostrarMensagem(caixa, 'O perfil de ' + u.nome + ' já é ' + rotuloPerfil(u.perfil) + '.', 'ok');
      return;
    }
    alterar(u, { perfil: perfil.value }, u.nome + ' agora é ' + rotuloPerfil(perfil.value) + '.');
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

  return el('tr', { class: u.ativo ? '' : 'inativo' },
    el('td', { class: 'nome', text: u.nome + (ehEu ? ' (você)' : '') }),
    el('td', { class: 'email', text: u.email }),
    el('td', { text: u.unidade || '—' }),
    el('td', {}, perfil),
    el('td', {}, selo(u.ativo ? 'Ativo' : 'Desativado', u.ativo ? 'RESOLVIDO' : 'neutro')),
    el('td', {}, el('div', { class: 'acoes-linha' }, salvar, alternarAtivo)));
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
  todos = await api('/api/sindico/usuarios');
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
