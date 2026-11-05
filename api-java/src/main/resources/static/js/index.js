const caixa = document.getElementById('mensagem');
const formEntrar = document.getElementById('form-entrar');
const formCadastrar = document.getElementById('form-cadastrar');
const abaEntrar = document.getElementById('aba-entrar');
const abaCadastrar = document.getElementById('aba-cadastrar');
const abas = document.getElementById('abas');
const titulo = document.getElementById('titulo-cartao');
const subtitulo = document.getElementById('subtitulo-cartao');
const botaoAvancar = document.getElementById('cad-avancar');
const botaoVoltar = document.getElementById('cad-voltar');

// ---------- abas: Entrar / Criar conta (tudo cabe na tela, sem rolagem) ----------
function trocarAba(entrar) {
  formEntrar.classList.toggle('hidden', !entrar);
  formCadastrar.classList.toggle('hidden', entrar);
  abaEntrar.classList.toggle('ativa', entrar);
  abaCadastrar.classList.toggle('ativa', !entrar);
  abaEntrar.setAttribute('aria-selected', String(entrar));
  abaCadastrar.setAttribute('aria-selected', String(!entrar));
  abas.dataset.ativa = entrar ? 'entrar' : 'cadastrar';
  titulo.textContent = entrar ? 'Bem-vindo de volta' : 'Crie a sua conta';
  subtitulo.textContent = entrar ? 'Entre para abrir e acompanhar chamados.' : 'Leva menos de um minuto, em dois passos.';
  if (!entrar) irParaPasso(1);
  limparMensagem(caixa);
  (document.getElementById(entrar ? 'login-email' : 'cad-nome')).focus();
}
abaEntrar.addEventListener('click', () => trocarAba(true));
abaCadastrar.addEventListener('click', () => trocarAba(false));

// ---------- cadastro em dois passos ----------
let passoAtual = 1;

function irParaPasso(n) {
  passoAtual = n;
  document.getElementById('passo-1').classList.toggle('hidden', n !== 1);
  document.getElementById('passo-2').classList.toggle('hidden', n !== 2);
  const etapa1 = document.getElementById('etapa-1');
  const etapa2 = document.getElementById('etapa-2');
  etapa1.classList.toggle('atual', n === 1);
  etapa1.classList.toggle('feita', n === 2);
  etapa2.classList.toggle('atual', n === 2);
  etapa1.toggleAttribute('aria-current', n === 1);
  etapa2.toggleAttribute('aria-current', n === 2);
  if (n === 1) etapa1.setAttribute('aria-current', 'step'); else etapa2.setAttribute('aria-current', 'step');
  botaoVoltar.classList.toggle('hidden', n === 1);
  botaoAvancar.textContent = n === 1 ? 'Continuar' : 'Criar conta';
}

/** Valida só os campos do passo que está na tela (o formulário não usa a validação do navegador no envio). */
function passoValido(n) {
  const campos = document.querySelectorAll('#passo-' + n + ' input');
  for (const campo of campos) {
    if (!campo.checkValidity()) {
      campo.reportValidity();
      return false;
    }
  }
  return true;
}

botaoVoltar.addEventListener('click', () => { limparMensagem(caixa); irParaPasso(1); document.getElementById('cad-senha').focus(); });

// ---------- botão com estado de carregamento ----------
async function comCarregando(botao, tarefa) {
  botao.classList.add('carregando');
  botao.disabled = true;
  botao.setAttribute('aria-busy', 'true');
  try {
    await tarefa();
  } finally {
    botao.classList.remove('carregando');
    botao.disabled = false;
    botao.removeAttribute('aria-busy');
  }
}

function erroNaTela(erro) {
  mostrarMensagem(caixa, erro.message, 'erro');
}

function irParaPainel(usuario) {
  window.location.href = usuario.trocarSenha ? '/conta.html?trocar=1' : paginaDoPerfil(usuario.perfil);
}

async function entrar(email, senha) {
  const usuario = await api('/api/auth/login', { metodo: 'POST', json: { email, senha } });
  irParaPainel(usuario);
}

formEntrar.addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  const botao = formEntrar.querySelector('button[type=submit]');
  await comCarregando(botao, async () => {
    try {
      await entrar(document.getElementById('login-email').value, document.getElementById('login-senha').value);
    } catch (erro) {
      erroNaTela(erro);
    }
  });
});

formCadastrar.addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  if (passoAtual === 1) {
    if (passoValido(1)) { irParaPasso(2); document.getElementById('cad-bloco').focus(); }
    return;
  }
  if (!passoValido(2)) return;
  const email = document.getElementById('cad-email').value;
  const senha = document.getElementById('cad-senha').value;
  await comCarregando(botaoAvancar, async () => {
    try {
      await api('/api/auth/register', {
        metodo: 'POST',
        json: {
          nome: document.getElementById('cad-nome').value,
          email,
          bloco: document.getElementById('cad-bloco').value,
          apartamento: document.getElementById('cad-apto').value,
          telefone: document.getElementById('cad-telefone').value,
          senha,
        },
      });
      await entrar(email, senha);
    } catch (erro) {
      erroNaTela(erro);
    }
  });
});

// ---------- senha: mostrar/ocultar, aviso de Caps Lock e medidor de força ----------
function ligarVerSenha(botaoId, campoId) {
  const botao = document.getElementById(botaoId);
  const campo = document.getElementById(campoId);
  botao.addEventListener('click', () => {
    const mostrar = campo.type === 'password';
    campo.type = mostrar ? 'text' : 'password';
    botao.textContent = mostrar ? 'Ocultar' : 'Mostrar';
    botao.setAttribute('aria-pressed', String(mostrar));
    campo.focus();
  });
}
ligarVerSenha('ver-login-senha', 'login-senha');
ligarVerSenha('ver-cad-senha', 'cad-senha');

const avisoCaps = document.getElementById('aviso-caps');
const campoLoginSenha = document.getElementById('login-senha');
function checarCaps(ev) {
  if (typeof ev.getModifierState === 'function') avisoCaps.classList.toggle('hidden', !ev.getModifierState('CapsLock'));
}
campoLoginSenha.addEventListener('keydown', checarCaps);
campoLoginSenha.addEventListener('keyup', checarCaps);
campoLoginSenha.addEventListener('blur', () => avisoCaps.classList.add('hidden'));

/** Só uma dica visual (comprimento e variedade de caracteres). A regra de verdade, de 8 a 72 caracteres, é conferida pelo servidor. */
function forcaDaSenha(senha) {
  if (!senha) return 0;
  if (senha.length < 8) return 1;
  let pontos = 2;
  if (senha.length >= 12) pontos += 1;
  if (/[a-z]/.test(senha) && /[A-Z]/.test(senha) && /\d/.test(senha)) pontos += 1;
  else if (/[^A-Za-z0-9]/.test(senha)) pontos += 1;
  return Math.min(pontos, 4);
}
const ROTULOS_FORCA = ['', 'Curta demais', 'Razoável', 'Boa', 'Forte'];
document.getElementById('cad-senha').addEventListener('input', (ev) => {
  const nivel = forcaDaSenha(ev.target.value);
  document.getElementById('forca').dataset.nivel = String(nivel);
  document.getElementById('forca-texto').textContent = ROTULOS_FORCA[nivel];
});

// ---------- contas de demonstração: menu flutuante (não empurra o formulário) ----------
const detalhesDemo = document.getElementById('contas-demo');
// No perfil "demo" a API lista as contas de exemplo (sem senha); fora dele a rota não existe e nada aparece.
fetch('/api/demo/contas', { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
  .then((r) => (r.ok ? r.json() : []))
  .then((contas) => {
    if (!contas.length) return;
    document.getElementById('botoes-contas-demo').replaceChildren(...contas.map((c) => {
      const [nome, ...resto] = c.rotulo.split(' · ');
      const botao = el('button', { type: 'button', class: 'conta-demo', title: c.email },
        el('span', { class: 'conta-nome', text: nome }),
        resto.length ? el('span', { class: 'conta-funcao', text: resto.join(' · ') }) : null);
      botao.addEventListener('click', () => {
        document.getElementById('login-email').value = c.email;
        detalhesDemo.open = false;
        document.getElementById('login-senha').focus();
      });
      return botao;
    }));
    detalhesDemo.classList.remove('hidden');
  })
  .catch(() => { /* sem demonstração: segue o login normal */ });
document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && detalhesDemo.open) { detalhesDemo.open = false; detalhesDemo.querySelector('summary').focus(); } });
document.addEventListener('click', (ev) => { if (detalhesDemo.open && !detalhesDemo.contains(ev.target)) detalhesDemo.open = false; });

// Quem já está logado vai direto para o painel.
api('/api/auth/sessao')
  .then((s) => { if (s.autenticado) irParaPainel(s.usuario); })
  .catch(() => { /* API fora do ar: fica no login */ });

document.getElementById('login-email').focus();
