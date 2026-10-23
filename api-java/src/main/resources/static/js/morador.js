const caixa = document.getElementById('mensagem');
const listaChamados = document.getElementById('lista-chamados');
let recarregarAvisos = () => Promise.resolve();

const ROTULOS_NOTA = { 5: 'Ótimo', 4: 'Bom', 3: 'Regular', 2: 'Ruim', 1: 'Péssimo' };

function estrelas(nota) {
  return '★'.repeat(nota) + '☆'.repeat(5 - nota);
}

/** Só aparece em chamado RESOLVIDO: a nota já dada, ou o formulário para dar uma. */
function blocoAvaliacao(c) {
  if (c.status !== 'RESOLVIDO') return null;
  if (c.notaAvaliacao !== null && c.notaAvaliacao !== undefined) {
    return el('div', { class: 'avaliacao' },
      el('strong', { text: 'Sua avaliação: ' }),
      el('span', { class: 'estrelas', text: estrelas(c.notaAvaliacao), 'aria-label': c.notaAvaliacao + ' de 5 estrelas' }),
      c.comentarioAvaliacao ? el('p', { text: c.comentarioAvaliacao }) : null);
  }
  const nota = el('select', { id: 'nota-' + c.id },
    el('option', { value: '', text: 'Escolha uma nota…' }),
    [5, 4, 3, 2, 1].map((n) => el('option', { value: String(n), text: n + ' - ' + ROTULOS_NOTA[n] })));
  const comentario = el('input', { type: 'text', id: 'comentario-' + c.id, maxlength: '500', placeholder: 'Comentário (opcional)' });
  const enviar = el('button', { type: 'button', text: 'Enviar avaliação' });
  const aviso = el('div', { class: 'mensagem hidden', role: 'alert' });
  enviar.addEventListener('click', async () => {
    limparMensagem(aviso);
    if (!nota.value) { mostrarMensagem(aviso, 'Escolha uma nota de 1 a 5.', 'erro'); return; }
    enviar.disabled = true;
    try {
      await api('/api/chamados/' + c.id + '/avaliacao', {
        metodo: 'POST',
        json: { nota: Number(nota.value), comentario: comentario.value },
      });
      await carregarChamados();
    } catch (erro) {
      mostrarMensagem(aviso, erro.message, 'erro');
      enviar.disabled = false;
    }
  });
  return el('div', { class: 'avaliacao' },
    el('h3', { text: 'Como foi o atendimento?' }),
    el('label', { for: 'nota-' + c.id, text: 'Nota' }), nota,
    el('label', { for: 'comentario-' + c.id, text: 'Comentário' }), comentario,
    el('div', { class: 'acoes' }, enviar), aviso);
}

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
          blocoAntesDepois(completo),
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

  return el('article', { class: 'chamado' + (c.emergencia ? ' emergencia' : '') },
    el('div', { class: 'linha' },
      el('span', { class: 'titulo', text: '#' + c.id + ' ' + c.titulo }),
      selo(c.statusRotulo, c.status),
      selo('Dificuldade: ' + c.dificuldadeRotulo, c.dificuldade),
      c.emergencia ? selo('EMERGÊNCIA', 'EMERGENCIA') : null,
      c.anonimo ? selo('Anônimo', 'neutro') : null,
      seloPrazo(c)),
    el('div', { class: 'meta', text: c.categoriaRotulo + (c.localOcorrencia ? ' · ' + c.localOcorrencia : '') + ' · aberto em ' + formatarData(c.criadoEm) }),
    c.responsavelNome ? el('div', { class: 'responsavel', text: textoResponsavel(c) }) : null,
    blocoTarefas(c),
    blocoAvaliacao(c),
    blocoConversa(c.id),
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

document.getElementById('botao-sair').addEventListener('click', sair);

// ao marcar "é uma emergência", os telefones de emergência aparecem na hora (o sistema só mostra, não liga)
const caixaTelefones = document.getElementById('telefones-emergencia');
caixaTelefones.append(telefonesEmergencia());
document.getElementById('emergencia').addEventListener('change', (e) => caixaTelefones.classList.toggle('hidden', !e.target.checked));

const banner = document.getElementById('novidades');
async function atualizarTudo() {
  banner.classList.add('hidden');
  await Promise.all([carregarChamados(), recarregarAvisos()]);
}
document.getElementById('atualizar-agora').addEventListener('click', () => atualizarTudo().catch(() => {}));

/** Chegou um aviso de tempo real. Não redesenha a lista por cima de quem está digitando. */
function aoMudar(evento) {
  if (evento && evento.tipo === 'mensagem') {
    atualizarConversa(evento.chamadoId);   // só a conversa aberta, sem redesenhar nada
    return recarregarAvisos();
  }
  if (estaEditando(listaChamados)) {
    banner.classList.remove('hidden');
    return recarregarAvisos();
  }
  return atualizarTudo();
}

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
  form.append('emergencia', document.getElementById('emergencia').checked);
  const arquivo = document.getElementById('imagem').files[0];
  if (arquivo) form.append('imagem', arquivo);
  try {
    const criado = await api('/api/chamados', { metodo: 'POST', form });
    e.target.reset();
    mostrarMensagem(caixa, 'Chamado #' + criado.id + ' enviado. Dificuldade estimada: ' + criado.dificuldadeRotulo + '.'
      + (criado.emergencia ? ' Marcado como EMERGÊNCIA: o síndico foi avisado e ele passa na frente da fila. Se há risco imediato, ligue também 192, 193 ou 190.' : ''), 'ok');
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
  recarregarAvisos = iniciarAvisos();
  await Promise.all([carregarChamados(), recarregarAvisos()]);
  iniciarTempoReal(aoMudar);
})();
