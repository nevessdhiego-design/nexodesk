// Configurações do síndico: matriz função x categoria e checklist por categoria. Todo texto do servidor entra com textContent.
const caixa = document.getElementById('mensagem');
let config = null;

function desenharMatriz() {
  document.getElementById('matriz-cabecalho').replaceChildren(el('tr', {},
    el('th', { scope: 'col', text: 'Categoria' }), ...config.funcoes.map((f) => el('th', { scope: 'col', text: f.rotulo }))));
  document.getElementById('matriz-corpo').replaceChildren(...config.categorias.map((c) =>
    el('tr', {}, el('th', { scope: 'row', text: c.rotulo }), ...config.funcoes.map((f) => {
      const id = 'm-' + f.codigo + '-' + c.codigo;
      return el('td', {}, el('input', { type: 'checkbox', id, 'data-funcao': f.codigo, 'data-categoria': c.codigo,
        checked: f.categorias.includes(c.codigo), 'aria-label': f.rotulo + ' cobre ' + c.rotulo }));
    }))));
}

function desenharChecklist() {
  const seletor = document.getElementById('categoria-checklist');
  const atual = seletor.value;
  seletor.replaceChildren(...config.categorias.map((c) => el('option', { value: c.codigo, text: c.rotulo, selected: c.codigo === atual })));
  mostrarChecklist();
}

function mostrarChecklist() {
  const c = config.categorias.find((x) => x.codigo === document.getElementById('categoria-checklist').value);
  document.getElementById('texto-checklist').value = c ? c.checklist.join('\n') : '';
  document.getElementById('prazo-categoria').textContent = c ? 'Prazo padrão desta categoria: ' + c.prazoHoras + ' h (emergência: 1 h).' : '';
}

function aplicar(resposta) {
  config = resposta;
  desenharMatriz();
  desenharChecklist();
}

document.getElementById('categoria-checklist').addEventListener('change', mostrarChecklist);
document.getElementById('salvar-matriz').addEventListener('click', async () => {
  limparMensagem(caixa);
  const matriz = {};
  config.funcoes.forEach((f) => { matriz[f.codigo] = []; });
  document.querySelectorAll('#matriz-corpo input:checked').forEach((c) => matriz[c.dataset.funcao].push(c.dataset.categoria));
  try {
    aplicar(await api('/api/sindico/configuracao/funcoes', { metodo: 'PUT', json: { matriz } }));
    mostrarMensagem(caixa, 'Salvo. O painel passa a indicar de acordo com esta tabela.', 'ok');
  } catch (erro) { mostrarMensagem(caixa, erro.message, 'erro'); }
});
document.getElementById('salvar-checklist').addEventListener('click', async () => {
  limparMensagem(caixa);
  const categoria = document.getElementById('categoria-checklist').value;
  const itens = document.getElementById('texto-checklist').value.split('\n');
  try {
    aplicar(await api('/api/sindico/configuracao/checklists/' + encodeURIComponent(categoria), { metodo: 'PUT', json: { itens } }));
    mostrarMensagem(caixa, 'Etapas salvas. Valem para os próximos chamados desta categoria.', 'ok');
  } catch (erro) { mostrarMensagem(caixa, erro.message, 'erro'); }
});
document.getElementById('restaurar').addEventListener('click', async () => {
  limparMensagem(caixa);
  if (!window.confirm('Voltar a matriz e os checklists ao padrão do sistema?')) return;
  try {
    aplicar(await api('/api/sindico/configuracao/restaurar', { metodo: 'POST' }));
    mostrarMensagem(caixa, 'Padrão restaurado.', 'ok');
  } catch (erro) { mostrarMensagem(caixa, erro.message, 'erro'); }
});
document.getElementById('botao-sair').addEventListener('click', sair);

(async function iniciar() {
  const eu = await exigirPerfil('SINDICO');
  if (!eu) return;
  document.getElementById('usuario').textContent = eu.nome;
  aplicar(await api('/api/sindico/configuracao'));
})();
