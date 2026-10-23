const caixa = document.getElementById('mensagem');

document.getElementById('form-esqueci').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  const botao = e.target.querySelector('button');
  botao.disabled = true;
  try {
    const r = await api('/api/auth/esqueci-senha', { metodo: 'POST', json: { email: document.getElementById('email').value } });
    mostrarMensagem(caixa, r.mensagem, 'ok');   // a resposta é a mesma exista ou não a conta
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  } finally {
    botao.disabled = false;
  }
});
