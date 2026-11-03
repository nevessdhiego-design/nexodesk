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
        bloco: document.getElementById('bloco').value,
        apartamento: document.getElementById('apartamento').value,
        unidade: document.getElementById('unidade-atual').dataset.valor || null,
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
    if (window.__eu && window.__eu.trocarSenha) {
      mostrarMensagem(caixaSenha, 'Senha alterada. Levando você ao painel...', 'ok');
      setTimeout(() => { window.location.href = paginaDoPerfil(window.__eu.perfil); }, 1200);
      return;
    }
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
  document.getElementById('bloco').value = eu.bloco || '';
  document.getElementById('apartamento').value = eu.apartamento || '';
  const atual = document.getElementById('unidade-atual');
  atual.dataset.valor = eu.unidade || '';   // conta antiga sem bloco: mantém o texto livre de antes
  atual.textContent = eu.unidade && !eu.bloco && !eu.apartamento ? 'Unidade cadastrada: ' + eu.unidade + '. Informe bloco e apartamento para organizar melhor.' : '';
  if (eu.trocarSenha || new URLSearchParams(window.location.search).get('trocar')) {
    mostrarMensagem(caixaSenha, 'Você entrou com uma senha temporária. Escolha uma nova senha para continuar usando o sistema.', 'erro');
  }
  window.__eu = eu;
  document.getElementById('telefone').value = eu.telefone || '';
})();
