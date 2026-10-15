const caixa = document.getElementById('mensagem');
const lista = document.getElementById('lista-chamados');
const filtro = document.getElementById('filtro');

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
        json: { status: seletor.value, observacao: observacao.value },
      });
      mostrarMensagem(caixa, 'Chamado #' + c.id + ' atualizado. O morador foi avisado.', 'ok');
      await carregar();
    } catch (erro) {
      mostrarMensagem(caixa, erro.message, 'erro');
      salvar.disabled = false;
    }
  });

  return el('article', { class: 'chamado' },
    el('div', { class: 'linha' },
      el('span', { class: 'fila-num', text: posicao + 'º' }),
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      selo(c.statusRotulo, c.status),
      selo(c.dificuldadeRotulo + ' (' + c.pontuacao + '/10)', c.dificuldade)),
    el('div', { class: 'meta', text: c.categoriaRotulo
      + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '')
      + ' · ' + c.moradorNome + (c.moradorUnidade ? ' (' + c.moradorUnidade + ')' : '')
      + ' · ' + formatarData(c.criadoEm) }),
    el('p', { class: 'descricao', text: c.descricao }),
    c.temImagem ? el('img', { class: 'foto', src: '/api/chamados/' + c.id + '/imagem', alt: 'Foto enviada no chamado' }) : null,
    el('details', {},
      el('summary', { text: 'Por que essa dificuldade?' }),
      el('ul', {}, c.justificativa.map((j) => el('li', { text: j }))),
      c.classificacaoFallback ? el('p', { class: 'muted', text: 'Serviço de prioridade indisponível na hora: classificação aproximada, só pela categoria.' }) : null),
    el('div', { class: 'edicao' },
      el('div', {}, el('label', { for: 'status-' + c.id, text: 'Status' }), seletor),
      el('div', {}, el('label', { for: 'obs-' + c.id, text: 'Observação para o morador (opcional)' }), observacao),
      salvar));
}

async function carregarEstatisticas() {
  const e = await api('/api/sindico/estatisticas');
  const itens = [
    ['Abertos', e.abertos], ['Em andamento', e.emAndamento], ['Resolvidos', e.resolvidos],
    ['Pendentes fáceis', e.pendentesFaceis], ['Pendentes médios', e.pendentesMedios], ['Pendentes difíceis', e.pendentesDificeis],
  ];
  document.getElementById('estatisticas').replaceChildren(...itens.map(([rotulo, valor]) =>
    el('div', { class: 'estatistica' },
      el('div', { class: 'valor', text: String(valor) }),
      el('div', { class: 'rotulo', text: rotulo }))));
}

async function carregar() {
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

(async function iniciar() {
  const eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  await carregar();
})();
