// O token vem no fragmento da URL (#token=...): o navegador não o envia ao servidor nem a outros sites.
const caixa = document.getElementById('mensagem');
const token = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token');
if (window.location.hash) history.replaceState(null, '', window.location.pathname);   // tira o token da barra de endereço

if (!token) {
  mostrarMensagem(caixa, 'O link está incompleto. Abra de novo o link do e-mail ou peça um novo.', 'erro');
  document.getElementById('form-redefinir').classList.add('hidden');
}

document.getElementById('form-redefinir').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  const nova = document.getElementById('nova').value;
  if (nova !== document.getElementById('confirmacao').value) {
    mostrarMensagem(caixa, 'As duas senhas não são iguais.', 'erro');
    return;
  }
  try {
    await api('/api/auth/redefinir-senha', { metodo: 'POST', json: { token, novaSenha: nova } });
    e.target.classList.add('hidden');
    mostrarMensagem(caixa, 'Senha alterada. Já pode entrar com a nova senha.', 'ok');
    caixa.append(' ', el('a', { href: '/index.html', text: 'Ir para o login' }));
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  }
});
