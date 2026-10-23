// Relatório do síndico. Todo texto vindo do servidor entra na tela com textContent.
const caixa = document.getElementById('mensagem');
const campoDe = document.getElementById('de');
const campoAte = document.getElementById('ate');

function iso(d) {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function definirPeriodo(de, ate) {
  campoDe.value = iso(de);
  campoAte.value = iso(ate);
}

function horas(valor) {
  if (valor === null || valor === undefined) return '—';
  return valor < 48 ? String(valor).replace('.', ',') + ' h' : String(Math.round(valor / 24 * 10) / 10).replace('.', ',') + ' dias';
}

function desenhar(r) {
  document.getElementById('periodo').textContent = 'Período: ' + formatarDia(r.de) + ' a ' + formatarDia(r.ate) + ' · ' + r.total + ' chamados abertos.';
  const itens = [
    ['Chamados abertos no período', r.total], ['Resolvidos', r.resolvidos], ['Em andamento', r.emAndamento], ['Ainda abertos', r.abertos],
    ['Emergências', r.emergencias], ['Tempo médio de resolução', horas(r.tempoMedioResolucaoHoras)],
    ['Resolvidos dentro do prazo', r.percentualNoPrazo === null ? '—' : String(r.percentualNoPrazo).replace('.', ',') + '%'],
    ['Fora do prazo agora', r.vencidosAgora],
  ];
  document.getElementById('resumo').replaceChildren(...itens.map(([rotulo, valor]) =>
    el('div', { class: 'estatistica' }, el('div', { class: 'valor', text: String(valor) }), el('div', { class: 'rotulo', text: rotulo }))));

  const maximo = Math.max(1, ...r.porCategoria.map((c) => c.total));
  document.getElementById('por-categoria').replaceChildren(...(r.porCategoria.length
    ? r.porCategoria.map((c, i) => el('div', { class: 'barra-linha' },
      el('label', { for: 'cat-' + i, text: c.rotulo + ': ' + c.total }),
      el('meter', { id: 'cat-' + i, min: '0', max: String(maximo), value: String(c.total), 'aria-label': c.rotulo + ': ' + c.total + ' chamados' }, String(c.total))))
    : [el('p', { class: 'vazio', text: 'Nenhum chamado neste período.' })]));

  document.getElementById('por-funcionario').replaceChildren(...(r.porFuncionario.length
    ? r.porFuncionario.map((f) => el('tr', {},
      el('td', { class: 'nome', text: f.nome }), el('td', { text: f.funcao }),
      el('td', { text: String(f.atribuidos) }), el('td', { text: String(f.resolvidos) }),
      el('td', { text: f.mediaAvaliacao === null || f.mediaAvaliacao === undefined ? '—' : String(f.mediaAvaliacao).replace('.', ',') + ' / 5' })))
    : [el('tr', {}, el('td', { colspan: '5', class: 'vazio', text: 'Nenhum chamado atribuído neste período.' }))]));
  document.getElementById('baixar-csv').href = '/api/sindico/relatorio.csv?de=' + r.de + '&ate=' + r.ate;
}

async function gerar() {
  limparMensagem(caixa);
  try {
    desenhar(await api('/api/sindico/relatorio?de=' + encodeURIComponent(campoDe.value) + '&ate=' + encodeURIComponent(campoAte.value)));
  } catch (erro) {
    mostrarMensagem(caixa, erro.message, 'erro');
  }
}

document.getElementById('form-periodo').addEventListener('submit', (e) => { e.preventDefault(); gerar(); });
document.getElementById('ultimos30').addEventListener('click', () => {
  const hoje = new Date(); const inicio = new Date(); inicio.setDate(hoje.getDate() - 29);
  definirPeriodo(inicio, hoje); gerar();
});
document.getElementById('este-mes').addEventListener('click', () => {
  const hoje = new Date(); definirPeriodo(new Date(hoje.getFullYear(), hoje.getMonth(), 1), hoje); gerar();
});
document.getElementById('imprimir').addEventListener('click', () => window.print());
document.getElementById('botao-sair').addEventListener('click', sair);

(async function iniciar() {
  const eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  const hoje = new Date(); const inicio = new Date(); inicio.setDate(hoje.getDate() - 29);
  definirPeriodo(inicio, hoje);
  await gerar();
})();
