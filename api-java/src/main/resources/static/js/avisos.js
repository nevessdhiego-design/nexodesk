// Sino de avisos compartilhado entre o painel do morador e o do síndico.
// A página precisa ter: #botao-sino, #contador, #painel-avisos e #lista-avisos (veja morador.html e sindico.html).
// Devolve a função que recarrega os avisos (a página chama de novo quando algo muda).
function iniciarAvisos() {
  const painel = document.getElementById('painel-avisos');
  const lista = document.getElementById('lista-avisos');
  const contador = document.getElementById('contador');

  async function carregar() {
    const dados = await api('/api/notificacoes');
    contador.textContent = dados.naoLidas;
    contador.classList.toggle('hidden', dados.naoLidas === 0);
    const naoLidas = dados.itens.filter((n) => !n.lida);
    const barra = naoLidas.length > 1
      ? el('div', { class: 'avisos-barra' },
        el('span', { class: 'muted', text: naoLidas.length + ' avisos não lidos' }),
        marcarTodos(naoLidas))
      : null;
    lista.replaceChildren(
      ...(dados.itens.length
        ? [barra, ...dados.itens.map((n) => {
          const emergencia = n.mensagem.startsWith('EMERGÊNCIA');
          const bloco = el('div', { class: 'notificacao' + (n.lida ? '' : ' nova') + (emergencia ? ' emergencia' : '') },
            el('div', { class: 'aviso-corpo' },
              el('div', { class: 'aviso-texto', text: n.mensagem }),
              el('div', { class: 'muted aviso-hora', text: formatarData(n.criadoEm) })));
          if (!n.lida) {
            const marcar = el('button', { type: 'button', class: 'secundario-escuro pequeno', text: 'Marcar como lido' });
            marcar.addEventListener('click', async () => {
              marcar.disabled = true;
              await api('/api/notificacoes/' + n.id + '/lida', { metodo: 'POST' });
              await carregar();
            });
            bloco.append(marcar);
          }
          return bloco;
        })].filter(Boolean)
        : [el('p', { class: 'vazio', text: 'Nenhum aviso por enquanto.' })]));
  }

  /** Marca todos de uma vez, um depois do outro (a API marca por aviso). */
  function marcarTodos(naoLidas) {
    const botao = el('button', { type: 'button', class: 'secundario-escuro pequeno', text: 'Marcar todos como lidos' });
    botao.addEventListener('click', async () => {
      botao.disabled = true;
      for (const n of naoLidas) await api('/api/notificacoes/' + n.id + '/lida', { metodo: 'POST' });
      await carregar();
    });
    return botao;
  }

  document.getElementById('botao-sino').addEventListener('click', () => painel.classList.toggle('hidden'));
  return carregar;
}
