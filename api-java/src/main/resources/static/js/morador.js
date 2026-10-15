const caixa = document.getElementById('mensagem');
const listaChamados = document.getElementById('lista-chamados');
const listaAvisos = document.getElementById('lista-avisos');
const painelAvisos = document.getElementById('painel-avisos');
const contador = document.getElementById('contador');

function cartaoChamado(c) {
  const detalhes = el('div', { class: 'hidden' });
  let carregado = false;

  const alternar = el('button', { type: 'button', class: 'link', text: 'Ver detalhes' });
  alternar.addEventListener('click', async () => {
    const aberto = !detalhes.classList.contains('hidden');
    if (aberto) {
      detalhes.classList.add('hidden');
      alternar.textContent = 'Ver detalhes';
      return;
    }
    if (!carregado) {
      try {
        const completo = await api('/api/chamados/' + c.id);
        detalhes.append(
          el('p', { class: 'descricao', text: completo.descricao }),
          completo.temImagem ? el('img', { class: 'foto', src: '/api/chamados/' + c.id + '/imagem', alt: 'Foto enviada no chamado' }) : null,
          el('h3', { text: 'Histórico' }),
          el('ul', {}, completo.historico.map((h) => el('li', {
            text: formatarData(h.criadoEm) + ' — ' + (h.statusAnterior ? h.statusAnterior + ' → ' : '') + h.statusNovo
              + (h.observacao ? ' (' + h.observacao + ')' : ''),
          }))),
        );
        carregado = true;
      } catch (erro) {
        detalhes.append(el('p', { class: 'mensagem erro', text: erro.message }));
      }
    }
    detalhes.classList.remove('hidden');
    alternar.textContent = 'Ocultar detalhes';
  });

  return el('article', { class: 'chamado' },
    el('div', { class: 'linha' },
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      selo(c.statusRotulo, c.status),
      selo('Dificuldade: ' + c.dificuldadeRotulo, c.dificuldade),
      c.anonimo ? selo('Anônimo', 'neutro') : null),
    el('div', { class: 'meta', text: c.categoriaRotulo + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '') + ' · aberto em ' + formatarData(c.criadoEm) }),
    alternar,
    detalhes);
}

async function carregarChamados() {
  const chamados = await api('/api/chamados/meus');
  listaChamados.replaceChildren(
    ...(chamados.length
      ? chamados.map(cartaoChamado)
      : [el('p', { class: 'vazio', text: 'Você ainda não abriu nenhum chamado.' })]));
}

async function carregarAvisos() {
  const dados = await api('/api/notificacoes');
  contador.textContent = dados.naoLidas;
  contador.classList.toggle('hidden', dados.naoLidas === 0);
  listaAvisos.replaceChildren(
    ...(dados.itens.length
      ? dados.itens.map((n) => {
        const bloco = el('div', { class: 'notificacao' + (n.lida ? '' : ' nova') },
          el('div', { text: n.mensagem }),
          el('div', { class: 'muted', text: formatarData(n.criadoEm) }));
        if (!n.lida) {
          const marcar = el('button', { type: 'button', class: 'link', text: 'Marcar como lido' });
          marcar.addEventListener('click', async () => {
            await api('/api/notificacoes/' + n.id + '/lida', { metodo: 'POST' });
            await carregarAvisos();
          });
          bloco.append(marcar);
        }
        return bloco;
      })
      : [el('p', { class: 'vazio', text: 'Nenhum aviso por enquanto.' })]));
}

document.getElementById('botao-sino').addEventListener('click', () => painelAvisos.classList.toggle('hidden'));
document.getElementById('botao-sair').addEventListener('click', sair);

document.getElementById('form-chamado').addEventListener('submit', async (e) => {
  e.preventDefault();
  limparMensagem(caixa);
  const botao = document.getElementById('botao-enviar');
  botao.disabled = true;
  const form = new FormData();
  form.append('titulo', document.getElementById('titulo').value);
  form.append('categoria', document.getElementById('categoria').value);
  form.append('localOcorrencia', document.getElementById('local').value);
  form.append('descricao', document.getElementById('descricao').value);
  form.append('anonimo', document.getElementById('anonimo').checked);
  const arquivo = document.getElementById('imagem').files[0];
  if (arquivo) form.append('imagem', arquivo);
  try {
    const criado = await api('/api/chamados', { metodo: 'POST', form });
    e.target.reset();
    mostrarMensagem(caixa, 'Chamado #' + criado.id + ' enviado. Dificuldade estimada: ' + criado.dificuldadeRotulo + '.', 'ok');
    await carregarChamados();
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  } finally {
    botao.disabled = false;
  }
});

(async function iniciar() {
  const eu = await exigirPerfil('MORADOR');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome + (eu.unidade ? ' · ' + eu.unidade : '');
  const categorias = await api('/api/categorias');
  const seletor = document.getElementById('categoria');
  seletor.append(el('option', { value: '', text: 'Selecione…' }),
    ...categorias.map((c) => el('option', { value: c.codigo, text: c.rotulo })));
  await Promise.all([carregarChamados(), carregarAvisos()]);
})();
