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
    lista.replaceChildren(
      ...(dados.itens.length
        ? dados.itens.map((n) => {
          const emergencia = n.mensagem.startsWith('EMERGÊNCIA');
          const bloco = el('div', { class: 'notificacao' + (n.lida ? '' : ' nova') + (emergencia ? ' emergencia' : '') },
            el('div', { text: n.mensagem }),
            el('div', { class: 'muted', text: formatarData(n.criadoEm) }));
          if (!n.lida) {
            const marcar = el('button', { type: 'button', class: 'link', text: 'Marcar como lido' });
            marcar.addEventListener('click', async () => {
              await api('/api/notificacoes/' + n.id + '/lida', { metodo: 'POST' });
              await carregar();
            });
            bloco.append(marcar);
          }
          return bloco;
        })
        : [el('p', { class: 'vazio', text: 'Nenhum aviso por enquanto.' })]));
  }

  document.getElementById('botao-sino').addEventListener('click', () => painel.classList.toggle('hidden'));
  return carregar;
}
