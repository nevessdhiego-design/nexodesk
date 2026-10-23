const caixa = document.getElementById('mensagem');
const formEntrar = document.getElementById('form-entrar');
const formCadastrar = document.getElementById('form-cadastrar');
const abaEntrar = document.getElementById('aba-entrar');
const abaCadastrar = document.getElementById('aba-cadastrar');

function trocarAba(entrar) {
  formEntrar.classList.toggle('hidden', !entrar);
  formCadastrar.classList.toggle('hidden', entrar);
  abaEntrar.classList.toggle('ativa', entrar);
  abaCadastrar.classList.toggle('ativa', !entrar);
  limparMensagem(caixa);
}
abaEntrar.addEventListener('click', () => trocarAba(true));
abaCadastrar.addEventListener('click', () => trocarAba(false));

function irParaPainel(usuario) {
  window.location.href = paginaDoPerfil(usuario.perfil);
}

async function entrar(email, senha) {
  const usuario = await api('/api/auth/login', { metodo: 'POST', json: { email, senha } });
  irParaPainel(usuario);
}

formEntrar.addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  try {
    await entrar(document.getElementById('login-email').value, document.getElementById('login-senha').value);
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  }
});

formCadastrar.addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  const email = document.getElementById('cad-email').value;
  const senha = document.getElementById('cad-senha').value;
  try {
    await api('/api/auth/register', {
      metodo: 'POST',
      json: {
        nome: document.getElementById('cad-nome').value,
        email,
        unidade: document.getElementById('cad-unidade').value,
        telefone: document.getElementById('cad-telefone').value,
        senha,
      },
    });
    await entrar(email, senha);
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  }
});

// No perfil "demo" a API lista as contas de exemplo (sem senha); fora dele a rota não existe e nada aparece.
fetch('/api/demo/contas', { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
  .then((r) => (r.ok ? r.json() : []))
  .then((contas) => {
    if (!contas.length) return;
    document.getElementById('botoes-contas-demo').replaceChildren(...contas.map((c) => {
      const botao = el('button', { type: 'button', class: 'conta-demo', text: c.rotulo, title: c.email });
      botao.addEventListener('click', () => {
        document.getElementById('login-email').value = c.email;
        document.getElementById('login-senha').focus();
      });
      return botao;
    }));
    document.getElementById('contas-demo').classList.remove('hidden');
  })
  .catch(() => { /* sem demonstração: segue o login normal */ });

// Quem já está logado vai direto para o painel.
api('/api/auth/sessao')
  .then((s) => { if (s.autenticado) irParaPainel(s.usuario); })
  .catch(() => { /* API fora do ar: fica no login */ });
