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
  const observacao = el('input', { type: 'text', id: 'obs-' + c.id, maxlength: '500', placeholder: 'Ex.: técnico agendado para amanhã' });
  const salvar = el('button', { type: 'button', text: 'Atualizar' });

  salvar.addEventListener('click', async () => {
    limparMensagem(caixa);
    salvar.disabled = true;
    try {
      await api('/api/sindico/chamados/' + c.id + '/status', {
        metodo: 'PATCH',
        // envia a versão que a tela mostrava: se outra pessoa já mexeu neste chamado, a API responde 409
        json: { status: seletor.value, observacao: observacao.value, versao: c.versao },
      });
      mostrarMensagem(caixa, 'Chamado #' + c.id + ' atualizado. O morador foi avisado.', 'ok');
      await carregar();
    } catch (erro) {
      mostrarMensagem(caixa, erro.message, 'erro');
      salvar.disabled = false;
      if (erro.status === 409) await carregar().catch(() => {}); // mostra o estado atual do chamado
    }
  });

  return el('article', { class: 'chamado' + (c.emergencia ? ' emergencia' : '') },
    el('div', { class: 'linha' },
      el('span', { class: 'fila-num', text: posicao + 'º' }),
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      c.emergencia ? selo('EMERGÊNCIA', 'EMERGENCIA') : null,
      selo(c.statusRotulo, c.status),
      selo(c.dificuldadeRotulo + ' (' + c.pontuacao + '/10)', c.dificuldade),
      c.atrasado ? selo('Atrasado', 'ATRASADO') : null),
    el('div', { class: 'meta', text: c.categoriaRotulo
      + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '')
      + ' · ' + c.moradorNome + (c.moradorUnidade ? ' (' + c.moradorUnidade + ')' : '')
      + ' · ' + formatarData(c.criadoEm)
      + (c.status !== 'RESOLVIDO' && c.diasEmEspera > 0 ? ' · há ' + c.diasEmEspera + (c.diasEmEspera === 1 ? ' dia' : ' dias')
        + (c.pontuacaoEfetiva < c.pontuacao ? ' (subiu na fila: peso ' + c.pontuacaoEfetiva + ' em vez de ' + c.pontuacao + ')' : '') : '') }),
    el('p', { class: 'descricao', text: c.descricao }),
    c.notaAvaliacao !== null && c.notaAvaliacao !== undefined
      ? el('div', { class: 'avaliacao' },
        el('strong', { text: 'Avaliação do morador: ' }),
        el('span', { class: 'estrelas', text: '★'.repeat(c.notaAvaliacao) + '☆'.repeat(5 - c.notaAvaliacao), 'aria-label': c.notaAvaliacao + ' de 5 estrelas' }),
        c.comentarioAvaliacao ? el('p', { text: c.comentarioAvaliacao }) : null)
      : null,
    c.temImagem ? el('img', { class: 'foto', src: '/api/chamados/' + c.id + '/imagem', alt: 'Foto enviada no chamado' }) : null,
    el('details', {},
      el('summary', { text: 'Por que essa dificuldade?' }),
      el('ul', {}, c.justificativa.map((j) => el('li', { text: j }))),
      c.classificacaoFallback ? el('p', { class: 'muted', text: 'Serviço de prioridade indisponível na hora: classificação aproximada, só pela categoria.' }) : null),
    blocoConversa(c.id),
    el('div', { class: 'edicao' },
      el('div', {}, el('label', { for: 'status-' + c.id, text: 'Status' }), seletor),
      el('div', {}, el('label', { for: 'obs-' + c.id, text: 'Observação para o morador (opcional)' }), observacao),
      salvar));
}

async function carregarEstatisticas() {
  const e = await api('/api/sindico/estatisticas');
  const itens = [
    ['Emergências pendentes', e.emergenciasAbertas],
    ['Abertos', e.abertos], ['Em andamento', e.emAndamento], ['Resolvidos', e.resolvidos],
    ['Pendentes fáceis', e.pendentesFaceis], ['Pendentes médios', e.pendentesMedios], ['Pendentes difíceis', e.pendentesDificeis],
    ['Avaliação média', e.mediaAvaliacoes === null || e.mediaAvaliacoes === undefined
      ? '—' : String(e.mediaAvaliacoes).replace('.', ',') + ' / 5 (' + e.totalAvaliacoes + ')'],
  ];
  document.getElementById('estatisticas').replaceChildren(...itens.map(([rotulo, valor]) =>
    el('div', { class: 'estatistica' },
      el('div', { class: 'valor', text: String(valor) }),
      el('div', { class: 'rotulo', text: rotulo }))));
}

async function carregar() {
  // o link de exportação segue o filtro escolhido na tela
  document.getElementById('exportar').href = '/api/sindico/chamados/exportar.csv?status=' + encodeURIComponent(filtro.value);
  const [chamados] = await Promise.all([
    api('/api/sindico/chamados?status=' + encodeURIComponent(filtro.value)),
    carregarEstatisticas(),
  ]);
  lista.replaceChildren(...(chamados.length
    ? chamados.map((c, i) => cartaoChamado(c, i + 1))
    : [el('p', { class: 'vazio', text: 'Nenhum chamado neste filtro.' })]));
}

filtro.addEventListener('change', () => carregar().catch((e) => mostrarMensagem(caixa, e.message, 'erro')));
document.getElementById('botao-sair').addEventListener('click', sair);

const banner = document.getElementById('novidades');
const tituloOriginal = document.title;
window.addEventListener('focus', () => { document.title = tituloOriginal; });

async function atualizarTudo() {
  banner.classList.add('hidden');
  await Promise.all([carregar(), recarregarAvisos()]);
}
document.getElementById('atualizar-agora').addEventListener('click', () => atualizarTudo().catch(() => {}));

/** Chegou um aviso de tempo real. Não redesenha a fila por cima de quem está escrevendo uma observação ou mensagem. */
function aoMudar(evento) {
  if (evento && evento.tipo === 'emergencia' && document.hidden) {
    document.title = '🚨 EMERGÊNCIA - ' + tituloOriginal;   // chama a atenção com a aba em segundo plano
  }
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
  const eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  recarregarAvisos = iniciarAvisos();
  await Promise.all([carregar(), recarregarAvisos()]);
  iniciarTempoReal(aoMudar);
})();
