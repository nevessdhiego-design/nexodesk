// Tela do zelador: só os chamados atribuídos a ele. Todo texto vindo do servidor entra com textContent.
const caixa = document.getElementById('mensagem');
const lista = document.getElementById('lista-chamados');
const filtro = document.getElementById('filtro');
let recarregarAvisos = () => Promise.resolve();

const STATUS = [
  { valor: 'ABERTO', rotulo: 'Aberto' },
  { valor: 'EM_ANDAMENTO', rotulo: 'Em andamento' },
  { valor: 'RESOLVIDO', rotulo: 'Resolvido' },
];

function cartaoChamado(c, posicao) {
  const seletor = el('select', { id: 'status-' + c.id }, STATUS.map((s) => el('option', { value: s.valor, text: s.rotulo, selected: s.valor === c.status })));
  const observacao = el('input', { type: 'text', id: 'obs-' + c.id, maxlength: '500', placeholder: 'Ex.: peça pedida, volto amanhã' });
  const salvar = el('button', { type: 'button', text: 'Atualizar' });

  salvar.addEventListener('click', async () => {
    limparMensagem(caixa);
    salvar.disabled = true;
    try {
      await api('/api/zelador/chamados/' + c.id + '/status', {
        metodo: 'PATCH',
        // a versão que a tela mostrava: se o síndico já mexeu neste chamado, a API responde 409
        json: { status: seletor.value, observacao: observacao.value, versao: c.versao },
      });
      mostrarMensagem(caixa, 'Chamado #' + c.id + ' atualizado. O morador foi avisado.', 'ok');
      await carregar();
    } catch (erro) {
      mostrarMensagem(caixa, erro.message, 'erro');
      salvar.disabled = false;
      if (erro.status === 409 || erro.status === 404) await carregar().catch(() => {});
    }
  });

  return el('article', { class: 'chamado' + (c.emergencia ? ' emergencia' : '') },
    el('div', { class: 'linha' },
      el('span', { class: 'fila-num', text: posicao + 'º' }),
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      c.emergencia ? selo('EMERGÊNCIA', 'EMERGENCIA') : null,
      selo(c.statusRotulo, c.status),
      selo(c.dificuldadeRotulo, c.dificuldade),
      c.atrasado ? selo('Atrasado', 'ATRASADO') : null),
    el('div', { class: 'meta', text: c.categoriaRotulo
      + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '')
      + ' · ' + c.moradorNome + (c.moradorUnidade ? ' (' + c.moradorUnidade + ')' : '')
      + ' · ' + formatarData(c.criadoEm)
      + (c.status !== 'RESOLVIDO' && c.diasEmEspera > 0 ? ' · há ' + c.diasEmEspera + (c.diasEmEspera === 1 ? ' dia' : ' dias') : '') }),
    c.previsao ? el('div', { class: 'responsavel', text: 'Previsão combinada: ' + formatarDia(c.previsao) }) : null,
    el('p', { class: 'descricao', text: c.descricao }),
    c.temImagem ? el('img', { class: 'foto', src: '/api/chamados/' + c.id + '/imagem', alt: 'Foto enviada no chamado' }) : null,
    blocoConversa(c.id),
    el('div', { class: 'edicao' },
      el('div', {}, el('label', { for: 'status-' + c.id, text: 'Status' }), seletor),
      el('div', {}, el('label', { for: 'obs-' + c.id, text: 'Observação para o morador (opcional)' }), observacao),
      salvar));
}

async function carregar() {
  const chamados = await api('/api/zelador/chamados?status=' + encodeURIComponent(filtro.value));
  lista.replaceChildren(...(chamados.length
    ? chamados.map((c, i) => cartaoChamado(c, i + 1))
    : [el('p', { class: 'vazio', text: 'Nenhum chamado atribuído a você neste filtro.' })]));
}

filtro.addEventListener('change', () => carregar().catch((e) => mostrarMensagem(caixa, e.message, 'erro')));
document.getElementById('botao-sair').addEventListener('click', sair);

const banner = document.getElementById('novidades');
async function atualizarTudo() {
  banner.classList.add('hidden');
  await Promise.all([carregar(), recarregarAvisos()]);
}
document.getElementById('atualizar-agora').addEventListener('click', () => atualizarTudo().catch(() => {}));

/** Chegou um aviso de tempo real. Não redesenha a lista por cima de quem está escrevendo. */
function aoMudar(evento) {
  if (evento && evento.tipo === 'mensagem') {
    atualizarConversa(evento.chamadoId);
    return recarregarAvisos();
  }
  if (estaEditando(lista)) {
    banner.classList.remove('hidden');
    return recarregarAvisos();
  }
  return atualizarTudo();
}

(async function iniciar() {
  const eu = await exigirPerfil('ZELADOR');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  recarregarAvisos = iniciarAvisos();
  await Promise.all([carregar(), recarregarAvisos()]);
  iniciarTempoReal(aoMudar);
})();
