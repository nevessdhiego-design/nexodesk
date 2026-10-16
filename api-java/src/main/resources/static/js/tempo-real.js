// Atualização em tempo real (Server-Sent Events) com plano B.
//
// O servidor empurra um aviso curto ("atualizacao": tipo + id do chamado) por GET /api/eventos. O aviso não traz
// conteúdo; quem decide o que recarregar é a página (aoMudar), usando a API normal.
//
// Se o navegador não suporta SSE ou a conexão cair, a página consulta a cada 15 s (plano B) até a conexão voltar.
// Quando a conexão volta, chamamos aoMudar uma vez para recuperar o que pode ter se perdido no intervalo.
function iniciarTempoReal(aoMudar) {
  const INTERVALO_PLANO_B_MS = 15000;
  const ESPERA_AGRUPAR_MS = 300; // vários avisos seguidos viram uma recarga só
  let conectado = false;
  let jaConectouUmaVez = false;
  let pendente = null;
  let timer = null;

  function agendar(evento) {
    // junta avisos próximos; se algum for "emergencia", a recarga leva essa informação
    if (!pendente || evento.tipo === 'emergencia') pendente = evento;
    clearTimeout(timer);
    timer = setTimeout(() => {
      const e = pendente;
      pendente = null;
      Promise.resolve(aoMudar(e)).catch((erro) => {
        if (erro && erro.status === 401) window.location.href = '/index.html';
      });
    }, ESPERA_AGRUPAR_MS);
  }

  if ('EventSource' in window) {
    const fonte = new EventSource('/api/eventos');
    fonte.addEventListener('open', () => {
      conectado = true;
      if (jaConectouUmaVez) agendar({ tipo: 'reconectou' }); // recupera o que passou enquanto estava desconectado
      jaConectouUmaVez = true;
    });
    fonte.addEventListener('atualizacao', (mensagem) => {
      let evento = { tipo: 'desconhecido' };
      try { evento = JSON.parse(mensagem.data); } catch (e) { /* aviso mal formado: recarrega por segurança */ }
      agendar(evento);
    });
    fonte.addEventListener('error', () => { conectado = false; }); // o navegador reconecta sozinho
  }

  // plano B: enquanto não houver conexão ao vivo, consulta de tempos em tempos
  setInterval(() => {
    if (!conectado) agendar({ tipo: 'consulta' });
  }, INTERVALO_PLANO_B_MS);

  return { conectado: () => conectado };
}

/** A pessoa está digitando dentro de {@code recipiente}? Então não recarregamos por cima do que ela escreve. */
function estaEditando(recipiente) {
  const a = document.activeElement;
  if (!a || !recipiente.contains(a)) return false;
  return a.tagName === 'TEXTAREA' || (a.tagName === 'INPUT' && a.type !== 'file' && a.value.trim() !== '');
}
