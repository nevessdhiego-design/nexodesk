// Conversa dentro do chamado (morador e síndico). Todo texto entra na tela com textContent (nunca como HTML).
//
// A lista de chamados é redesenhada quando algo muda; por isso guardamos FORA do desenho: quais conversas estavam abertas
// e o que a pessoa já tinha digitado. Assim uma atualização em tempo real não fecha nem apaga nada.
const rascunhosDeConversa = new Map();   // id do chamado -> texto digitado e ainda não enviado
const conversasAbertas = new Set();      // ids com a conversa aberta
const conversasNaTela = new Map();       // id do chamado -> { detalhes, carregar }

function blocoConversa(chamadoId) {
  const lista = el('div', { class: 'conversa-lista', 'aria-live': 'polite' });
  const campo = el('textarea', {
    id: 'msg-' + chamadoId, maxlength: '1000', rows: '2', placeholder: 'Escreva uma mensagem…',
  });
  campo.value = rascunhosDeConversa.get(chamadoId) || '';
  campo.addEventListener('input', () => rascunhosDeConversa.set(chamadoId, campo.value));
  const enviar = el('button', { type: 'button', text: 'Enviar' });
  const aviso = el('div', { class: 'mensagem hidden', role: 'alert' });
  const resumo = el('summary', { text: 'Conversa' });

  function bolha(m) {
    return el('div', { class: 'bolha ' + (m.minha ? 'minha' : 'outra') + (m.doSindico ? ' sindico' : '') },
      el('div', { class: 'autor', text: m.minha ? 'Você' : m.autor }),
      el('div', { class: 'texto', text: m.texto }),
      el('div', { class: 'hora muted', text: formatarData(m.criadoEm) }));
  }

  async function carregar() {
    const mensagens = await api('/api/chamados/' + chamadoId + '/mensagens');
    resumo.textContent = mensagens.length ? 'Conversa (' + mensagens.length + ')' : 'Conversa';
    lista.replaceChildren(...(mensagens.length
      ? mensagens.map(bolha)
      : [el('p', { class: 'vazio', text: 'Nenhuma mensagem ainda.' })]));
    lista.scrollTop = lista.scrollHeight;
  }

  enviar.addEventListener('click', async () => {
    limparMensagem(aviso);
    const texto = campo.value.trim();
    if (!texto) { mostrarMensagem(aviso, 'Escreva a mensagem.', 'erro'); return; }
    enviar.disabled = true;
    try {
      await api('/api/chamados/' + chamadoId + '/mensagens', { metodo: 'POST', json: { texto } });
      campo.value = '';
      rascunhosDeConversa.delete(chamadoId);
      await carregar();
    } catch (erro) {
      mostrarMensagem(aviso, erro.message, 'erro');
    } finally {
      enviar.disabled = false;
    }
  });

  const detalhes = el('details', { class: 'conversa' }, resumo, lista,
    el('label', { for: 'msg-' + chamadoId, text: 'Nova mensagem' }), campo,
    el('div', { class: 'acoes' }, enviar), aviso);
  detalhes.open = conversasAbertas.has(chamadoId);
  detalhes.addEventListener('toggle', () => {
    if (detalhes.open) {
      conversasAbertas.add(chamadoId);
      carregar().catch((erro) => mostrarMensagem(aviso, erro.message, 'erro'));
    } else {
      conversasAbertas.delete(chamadoId);
    }
  });
  if (detalhes.open) carregar().catch(() => {});
  conversasNaTela.set(chamadoId, { detalhes, carregar });
  return detalhes;
}

/** Chamado quando chega um aviso de mensagem nova: atualiza só aquela conversa, se estiver aberta. */
function atualizarConversa(chamadoId) {
  const c = conversasNaTela.get(chamadoId);
  if (c && c.detalhes.isConnected && c.detalhes.open) {
    c.carregar().catch(() => {});
    return true;
  }
  return false;
}
