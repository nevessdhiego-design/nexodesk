const caixaPerfil = document.getElementById('mensagem-perfil');
const caixaSenha = document.getElementById('mensagem-senha');

document.getElementById('botao-sair').addEventListener('click', sair);

document.getElementById('form-perfil').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixaPerfil);
  try {
    const eu = await api('/api/auth/me', {
      metodo: 'PATCH',
      json: {
        nome: document.getElementById('nome').value,
        unidade: document.getElementById('unidade').value,
        telefone: document.getElementById('telefone').value,
      },
    });
    document.getElementById('nome').value = eu.nome;
    mostrarMensagem(caixaPerfil, 'Dados salvos.', 'ok');
  } catch (erro) {
    mostrarMensagem(caixaPerfil, erro.message, 'erro');
  }
});

document.getElementById('form-senha').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixaSenha);
  try {
    await api('/api/auth/senha', {
      metodo: 'POST',
      json: {
        senhaAtual: document.getElementById('senha-atual').value,
        novaSenha: document.getElementById('senha-nova').value,
      },
    });
    e.target.reset();
    mostrarMensagem(caixaSenha, 'Senha alterada. Use a nova senha no próximo login.', 'ok');
  } catch (erro) {
    mostrarMensagem(caixaSenha, erro.message, 'erro');
  }
});

document.getElementById('form-exclusao').addEventListener('submit', async (e) => {
  e.preventDefault();
  const caixaExclusao = document.getElementById('mensagem-exclusao');
  limparMensagem(caixaExclusao);
  if (!window.confirm('Excluir a sua conta? Seus dados pessoais serão apagados e isso não pode ser desfeito.')) return;
  try {
    await api('/api/auth/excluir-conta', { metodo: 'POST', json: { senha: document.getElementById('senha-exclusao').value } });
    window.location.href = '/index.html';
  } catch (erro) {
    mostrarMensagem(caixaExclusao, erro.message, 'erro');
  }
});

(async function iniciar() {
  let eu;
  try {
    eu = await api('/api/auth/me');
  } catch (erro) {
    window.location.href = '/index.html';
    return;
  }
  montarMenu(eu.perfil);
  document.getElementById('email').value = eu.email;
  document.getElementById('nome').value = eu.nome || '';
  document.getElementById('unidade').value = eu.unidade || '';
  document.getElementById('telefone').value = eu.telefone || '';
})();
