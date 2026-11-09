const caixa = document.getElementById('mensagem');
const lista = document.getElementById('lista-chamados');
const filtro = document.getElementById('filtro');
let recarregarAvisos = () => Promise.resolve();
let equipe = [];
let chamadosNaTela = [];   // a fila inteira do filtro de status; a busca por texto e categoria age só na tela
const TAMANHO_PAGINA = 20;   // quantos chamados aparecem por vez; "Mostrar mais" libera os próximos
let limiteVisivel = TAMANHO_PAGINA;
const busca = document.getElementById('busca');
const filtroCategoria = document.getElementById('filtro-categoria');   // síndicos e zeladores ativos: quem pode cuidar de um chamado

const STATUS = [
  { valor: 'ABERTO', rotulo: 'Aberto' },
  { valor: 'EM_ANDAMENTO', rotulo: 'Em andamento' },
  { valor: 'RESOLVIDO', rotulo: 'Resolvido' },
];

/** Hoje no formato AAAA-MM-DD, no fuso do navegador (limite mínimo do campo de previsão). */
function hojeIso() {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

/** Quem cuida do chamado e para quando. Só aparece enquanto o chamado não foi resolvido. */
function blocoResponsavel(c) {
  if (c.status === 'RESOLVIDO') {
    return c.responsavelNome ? el('p', { class: 'responsavel', text: textoResponsavel(c) }) : null;
  }
  // quem cobre a categoria vem primeiro ("Indicado"); entre eles, o de menor carga; síndicos por último. É só sugestão.
  const indicado = (p) => p.disponivel && p.categorias.includes(c.categoria);   // quem está de férias ou de folga não é indicado
  const ordenada = [...equipe].sort((a, b) => (indicado(b) - indicado(a)) || (b.disponivel - a.disponivel)
    || ((a.perfil === 'SINDICO') - (b.perfil === 'SINDICO')) || (a.cargaAberta - b.cargaAberta) || a.nome.localeCompare(b.nome, 'pt-BR'));
  const quem = el('select', { id: 'resp-' + c.id },
    el('option', { value: '', text: 'Sem responsável' }),
    ordenada.map((p) => el('option', {
      value: String(p.id),
      text: (indicado(p) ? 'Indicado · ' : '') + p.nome + ' · ' + (p.funcaoRotulo || 'Síndico') + ' · ' + p.cargaAberta + ' em aberto'
        + (p.disponivel ? '' : ' · AUSENTE: ' + p.indisponibilidade),
      selected: p.id === c.responsavelId,
    })));
  const indicados = ordenada.filter(indicado).map((p) => p.nome);
  const dica = el('p', { class: 'muted', text: indicados.length
    ? 'Para ' + c.categoriaRotulo + ' o sistema indica: ' + indicados.join(', ') + '. Quem atribui é você.'
    : 'Nenhuma função da equipe cobre ' + c.categoriaRotulo + '. Atribua a quem preferir.' });
  const quando = el('input', { type: 'date', id: 'prev-' + c.id, min: hojeIso(), value: c.previsao || null });
  const atribuir = el('button', { type: 'button', text: 'Salvar responsável' });
  atribuir.addEventListener('click', async () => {
    limparMensagem(caixa);
    const escolhido = equipe.find((p) => String(p.id) === quem.value);
    if (escolhido && !escolhido.disponivel
        && !window.confirm(escolhido.nome + ' está ausente (' + escolhido.indisponibilidade + '). Atribuir mesmo assim?')) return;
    atribuir.disabled = true;
    try {
      await api('/api/sindico/chamados/' + c.id + '/responsavel', {
        metodo: 'PATCH',
        json: { responsavelId: quem.value ? Number(quem.value) : null, previsao: quando.value || null, versao: c.versao },
      });
      mostrarMensagem(caixa, 'Chamado #' + c.id + ': responsável atualizado. As pessoas envolvidas foram avisadas.', 'ok');
      await carregar();
    } catch (erro) {
      mostrarMensagem(caixa, erro.message, 'erro');
      atribuir.disabled = false;
      if (erro.status === 409) await carregar().catch(() => {});
    }
  });
  return el('div', { class: 'bloco-responsavel' },
    dica,
    el('div', { class: 'edicao responsavel-edicao' },
      el('div', {}, el('label', { for: 'resp-' + c.id, text: 'Responsável' }), quem),
      el('div', {}, el('label', { for: 'prev-' + c.id, text: 'Previsão (opcional)' }), quando),
      atribuir));
}

function cartaoChamado(c, posicao) {
  const seletor = el('select', { id: 'status-' + c.id }, STATUS.map((s) => el('option', { value: s.valor, text: s.rotulo, selected: s.valor === c.status })));
  const observacao = el('input', { type: 'text', id: 'obs-' + c.id, maxlength: '500', placeholder: 'Ex.: técnico agendado para amanhã' });
  const salvar = el('button', { type: 'button', text: 'Atualizar' });

  salvar.addEventListener('click', async () => {
    limparMensagem(caixa);
    if (seletor.value === 'RESOLVIDO' && c.status !== 'RESOLVIDO'
        && !window.confirm('Marcar o chamado #' + c.id + ' como resolvido? O morador será avisado agora e poderá avaliar o atendimento.')) return;
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

  return el('article', { class: 'chamado st-' + c.status.toLowerCase() + (c.emergencia ? ' emergencia' : '') },
    el('div', { class: 'linha' },
      el('span', { class: 'fila-num', text: posicao + 'º' }),
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      c.emergencia ? selo('EMERGÊNCIA', 'EMERGENCIA') : null,
      selo(c.statusRotulo, c.status),
      selo(c.dificuldadeRotulo + ' (' + c.pontuacao + '/10)', c.dificuldade),
      c.atrasado ? selo('Atrasado', 'ATRASADO') : null,
      c.reaberturas > 0 ? selo('Reaberto ' + c.reaberturas + 'x', 'ATRASADO') : null,
      c.recorrencias >= 3 ? selo('Se repete: ' + c.recorrencias + 'x em 90 dias', 'ATRASADO') : null,
      seloPrazo(c)),
    el('div', { class: 'meta', text: c.categoriaRotulo
      + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '')
      + ' · ' + c.moradorNome + (c.moradorUnidade ? ' (' + c.moradorUnidade + ')' : '')
      + ' · ' + formatarData(c.criadoEm)
      + (c.status !== 'RESOLVIDO' && c.diasEmEspera > 0 ? ' · há ' + c.diasEmEspera + (c.diasEmEspera === 1 ? ' dia' : ' dias')
        + (c.pontuacaoEfetiva < c.pontuacao ? ' (subiu na fila: peso ' + c.pontuacaoEfetiva + ' em vez de ' + c.pontuacao + ')' : '') : '') }),
    c.emergencia ? telefonesEmergencia() : null,
    el('p', { class: 'descricao', text: c.descricao }),
    c.notaAvaliacao !== null && c.notaAvaliacao !== undefined
      ? el('div', { class: 'avaliacao' },
        el('strong', { text: 'Avaliação do morador: ' }),
        el('span', { class: 'estrelas', text: '★'.repeat(c.notaAvaliacao) + '☆'.repeat(5 - c.notaAvaliacao), 'aria-label': c.notaAvaliacao + ' de 5 estrelas' }),
        c.comentarioAvaliacao ? el('p', { text: c.comentarioAvaliacao }) : null)
      : null,
    blocoAntesDepois(c, '/api/sindico', carregar, (m) => mostrarMensagem(caixa, m, 'erro')),
    blocoTarefas(c, '/api/sindico', (m) => mostrarMensagem(caixa, m, 'erro')),
    el('details', {},
      el('summary', { text: 'Por que essa dificuldade?' }),
      el('ul', {}, c.justificativa.map((j) => el('li', { text: j }))),
      c.classificacaoFallback ? el('p', { class: 'muted', text: 'Serviço de prioridade indisponível na hora: classificação aproximada, só pela categoria.' }) : null),
    blocoConversa(c.id),
    blocoResponsavel(c),
    el('div', { class: 'edicao' },
      el('div', {}, el('label', { for: 'status-' + c.id, text: 'Status' }), seletor),
      el('div', {}, el('label', { for: 'obs-' + c.id, text: 'Observação para o morador (opcional)' }), observacao),
      salvar));
}

/** Rosca (gráfico de anel) com o total no centro e legenda. É SVG desenhado por atributos (a CSP não aceita estilo inline); a legenda diz os números por extenso. */
function rosca(titulo, partes) {
  const NS = 'http://www.w3.org/2000/svg';
  const total = partes.reduce((soma, p) => soma + p[1], 0);
  const resumo = titulo + ': ' + partes.map((p) => p[1] + ' ' + p[0].toLowerCase()).join(', ');
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 120 120');
  svg.setAttribute('class', 'rosca');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', resumo);
  const anel = (classe) => {
    const c = document.createElementNS(NS, 'circle');
    c.setAttribute('cx', '60'); c.setAttribute('cy', '60'); c.setAttribute('r', '48'); c.setAttribute('fill', 'none');
    c.setAttribute('class', classe);
    return c;
  };
  svg.append(anel('rosca-fundo'));
  const volta = 2 * Math.PI * 48;
  let acumulado = 0;
  partes.filter((p) => p[1] > 0).forEach((p) => {
    const trecho = anel('rosca-trecho ' + p[2]);
    const tamanho = volta * p[1] / total;
    const visivel = Math.max(tamanho - (partes.filter((q) => q[1] > 0).length > 1 ? 2 : 0), 0.5);
    trecho.setAttribute('stroke-dasharray', visivel + ' ' + (volta - visivel));
    trecho.setAttribute('stroke-dashoffset', String(-acumulado));
    trecho.setAttribute('transform', 'rotate(-90 60 60)');
    svg.append(trecho);
    acumulado += tamanho;
  });
  const numero = document.createElementNS(NS, 'text');
  numero.setAttribute('x', '60'); numero.setAttribute('y', '62'); numero.setAttribute('text-anchor', 'middle'); numero.setAttribute('class', 'rosca-total');
  numero.textContent = String(total);
  const rotulo = document.createElementNS(NS, 'text');
  rotulo.setAttribute('x', '60'); rotulo.setAttribute('y', '78'); rotulo.setAttribute('text-anchor', 'middle'); rotulo.setAttribute('class', 'rosca-rotulo');
  rotulo.textContent = total === 1 ? 'chamado' : 'chamados';
  svg.append(numero, rotulo);
  return el('div', { class: 'grafico' }, el('h3', { text: titulo }),
    el('div', { class: 'grafico-rosca' }, svg,
      el('ul', { class: 'legenda legenda-coluna' }, partes.map((p) => el('li', {}, el('span', { class: 'ponto ' + p[2] }), el('span', { text: p[0] + ': ' + p[1] }))))));
}

let estatisticasJaApareceram = false;   // o número só "sobe" na primeira vez; as atualizações em tempo real não repetem a animação

async function carregarEstatisticas() {
  const e = await api('/api/sindico/estatisticas');
  const itens = [
    ['Emergências pendentes', e.emergenciasAbertas, 'alerta', e.emergenciasAbertas > 0 ? 'urgente' : ''],
    ['Abertos', e.abertos, 'chamados', 'tom-azul'], ['Em andamento', e.emAndamento, 'ferramenta', 'tom-ambar'], ['Resolvidos', e.resolvidos, 'check', 'ok'],
    ['Pendentes fáceis', e.pendentesFaceis, 'relogio', 'ok'], ['Pendentes médios', e.pendentesMedios, 'relogio', 'tom-ambar'], ['Pendentes difíceis', e.pendentesDificeis, 'relogio', 'tom-vermelho'],
    ['Avaliação média', e.mediaAvaliacoes === null || e.mediaAvaliacoes === undefined
      ? '—' : String(e.mediaAvaliacoes).replace('.', ',') + ' / 5 (' + e.totalAvaliacoes + ')', 'estrela', 'tom-ambar'],
  ];
  document.getElementById('estatisticas').replaceChildren(...itens.map(([rotulo, valor, nomeIcone, classe]) =>
    el('div', { class: 'estatistica ' + classe },
      icone(nomeIcone, 'icone-estatistica'),
      el('div', { class: 'valor', text: String(valor) }),
      el('div', { class: 'rotulo', text: rotulo }))));
  if (!estatisticasJaApareceram) {
    estatisticasJaApareceram = true;
    document.querySelectorAll('#estatisticas .valor').forEach((no) => contar(no, no.textContent));
  }
  document.getElementById('graficos').replaceChildren(
    rosca('Chamados por situação', [['Abertos', e.abertos, 'c-aberto'], ['Em andamento', e.emAndamento, 'c-andamento'], ['Resolvidos', e.resolvidos, 'c-resolvido']]),
    rosca('Pendentes por dificuldade', [['Fáceis', e.pendentesFaceis, 'c-facil'], ['Médios', e.pendentesMedios, 'c-medio'], ['Difíceis', e.pendentesDificeis, 'c-dificil']]));
}

async function carregar() {
  // o link de exportação segue o filtro escolhido na tela
  document.getElementById('exportar').href = '/api/sindico/chamados/exportar.csv?status=' + encodeURIComponent(filtro.value);
  const [chamados, pessoas] = await Promise.all([
    api('/api/sindico/chamados?status=' + encodeURIComponent(filtro.value)),
    api('/api/sindico/equipe'),
    carregarEstatisticas(),
  ]);
  equipe = pessoas;
  chamadosNaTela = chamados;
  atualizarCategorias();
  desenharLista();
}

/** As categorias do select vêm dos chamados que estão na lista (mantém a escolhida, se ainda existir). */
function atualizarCategorias() {
  const escolhida = filtroCategoria.value;
  const rotulos = [...new Set(chamadosNaTela.map((c) => c.categoriaRotulo))].sort((a, b) => a.localeCompare(b, 'pt-BR'));
  filtroCategoria.replaceChildren(el('option', { value: '', text: 'Todas as categorias' }),
    ...rotulos.map((r) => el('option', { value: r, text: r, selected: r === escolhida })));
}

function semAcento(texto) {
  return (texto || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

/** Desenha a lista aplicando busca e categoria. O número ao lado de cada chamado continua sendo a posição REAL na fila. */
function desenharLista() {
  const termo = semAcento(busca.value.trim());
  const categoria = filtroCategoria.value;
  const visiveis = chamadosNaTela
    .map((c, i) => ({ c, posicao: i + 1 }))
    .filter(({ c }) => (!categoria || c.categoriaRotulo === categoria)
      && (!termo || semAcento([c.titulo, c.descricao, c.moradorNome, c.localOcorrencia].join(' ')).includes(termo)));
  const naPagina = visiveis.slice(0, limiteVisivel);
  const mostrarMais = visiveis.length > naPagina.length
    ? el('button', { type: 'button', class: 'secundario-escuro', text: 'Mostrar mais ' + Math.min(TAMANHO_PAGINA, visiveis.length - naPagina.length) + ' chamados' })
    : null;
  if (mostrarMais) mostrarMais.addEventListener('click', () => { limiteVisivel += TAMANHO_PAGINA; desenharLista(); });
  lista.replaceChildren(...(visiveis.length
    ? naPagina.map(({ c, posicao }) => cartaoChamado(c, posicao))
    : [vazio(chamadosNaTela.length ? 'Nenhum chamado combina com a busca. Tente outras palavras ou limpe o filtro.' : 'Nenhum chamado neste filtro. Quando um morador abrir um chamado, ele aparece aqui sozinho.')]),
    ...(mostrarMais ? [el('div', { class: 'acoes' }, mostrarMais)] : []));
  document.getElementById('contagem').textContent = naPagina.length + ' de ' + visiveis.length + ' chamados exibidos'
    + (visiveis.length !== chamadosNaTela.length ? ' (' + chamadosNaTela.length + ' no filtro de status)' : '');
}

// mudar a busca, a categoria ou o filtro de status volta para a primeira página
busca.addEventListener('input', () => { limiteVisivel = TAMANHO_PAGINA; desenharLista(); });
filtroCategoria.addEventListener('change', () => { limiteVisivel = TAMANHO_PAGINA; desenharLista(); });

filtro.addEventListener('change', () => { limiteVisivel = TAMANHO_PAGINA; carregar().catch((e) => mostrarMensagem(caixa, e.message, 'erro')); });
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
