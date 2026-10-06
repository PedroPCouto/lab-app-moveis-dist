// Testes ponta a ponta do Sprint 2, gravados em video. Rodam contra o RabbitMQ real
// (RABBITMQ_URL) e as 3 agencias de verdade, dirigindo o frontend como um usuario.
//
//   A - transferencia entre agencias, assincrona, via RabbitMQ
//   B - agencia de destino fora do ar: a mensagem fica retida na fila
//   C - fila de mortas (funcionalidade adicional): recriar a conta e reprocessar
//
// B e C sao a mesma historia em sequencia, por isso o arquivo roda em modo serial.
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const agencias = require('./apoio/agencias');

const PASTA_VIDEOS = path.resolve(__dirname, '..', 'evidencias', 'sprint2', 'videos');

test.describe.configure({ mode: 'serial' });

// ---------------------------------------------------------------- legenda no video

// Faixa fixa no rodape da pagina com o passo atual e a data/hora correndo. Sobrevive a
// recarregamentos (sessionStorage) e nao intercepta cliques (pointer-events: none).
function scriptDaLegenda(tituloDoTeste) {
  const montar = () => {
    if (document.getElementById('__legenda')) return;
    document.body.style.paddingBottom = '110px';
    const caixa = document.createElement('div');
    caixa.id = '__legenda';
    caixa.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;pointer-events:none;'
      + 'background:rgba(2,6,23,.94);border-top:2px solid #fbbf24;padding:10px 18px;'
      + 'font:600 16px/1.45 Consolas,"Cascadia Code",monospace;color:#fbbf24;white-space:pre-wrap';
    const relogio = document.createElement('div');
    relogio.style.cssText = 'color:#94a3b8;font-weight:400;font-size:12px;margin-bottom:2px';
    const texto = document.createElement('div');
    texto.id = '__legendaTexto';
    texto.textContent = sessionStorage.getItem('__legenda') || '';
    caixa.append(relogio, texto);
    document.body.appendChild(caixa);
    const tic = () => {
      relogio.textContent = tituloDoTeste + '   |   ' + new Date().toLocaleString('pt-BR');
    };
    tic();
    setInterval(tic, 500);
  };
  window.__legendar = (t) => {
    sessionStorage.setItem('__legenda', t);
    const e = document.getElementById('__legendaTexto');
    if (e) e.textContent = t;
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', montar);
  else montar();
}

// foco: elemento a centralizar na tela durante a legenda (para nao ficar sob a faixa).
async function legendar(page, texto, pausaMs = 2200, foco = null) {
  if (foco) await foco.evaluate((e) => e.scrollIntoView({ block: 'center', behavior: 'smooth' }));
  await page.evaluate((t) => window.__legendar(t), texto);
  await page.waitForTimeout(pausaMs);
}

const cartaoDeFilas = (page) => page.locator('#corpoFilas');

// ---------------------------------------------------------------- apoio de UI e de API

async function entrar(page, usuario, senha, idAgencia) {
  await page.selectOption('#agenciaLogin', String(idAgencia));
  await page.fill('#usuario', usuario);
  await page.fill('#senha', senha);
  await page.click('#formularioLogin button[type=submit]');
  await expect(page.locator('#telaApp')).toBeVisible();
}

async function sair(page) {
  await page.click('#botaoSair');
  await expect(page.locator('#telaLogin')).toBeVisible();
}

const pilula = (page, id) => page.locator('#pilulasAgencias .pilula').nth(id);

const linhaDaConta = (page, id) => page.locator('#corpoContas tr', {
  has: page.locator('td:first-child', { hasText: new RegExp(`^${id}$`) }),
});

const quantidadeNaFila = (page, nome) => page.locator('#corpoFilas tr', {
  has: page.locator('code', { hasText: new RegExp(`^${nome.replace(/\./g, '\\.')}$`) }),
}).locator('td').nth(1);

// O frontend so rele o estado ao clicar em Atualizar: clica ate a condicao valer
// (o credito e aplicado de forma assincrona, nao no instante da resposta).
async function atualizarAte(page, verificacao, limiteMs = 20_000) {
  await expect(async () => {
    await page.click('#botaoAtualizar');
    await page.waitForTimeout(400);
    await verificacao();
  }).toPass({ timeout: limiteMs, intervals: [500, 1000] });
}

async function transferir(page, origem, destino, valor) {
  await page.fill('#transferenciaOrigem', String(origem));
  await page.fill('#transferenciaDestino', String(destino));
  await page.fill('#transferenciaValor', String(valor));
}

async function api(idAgencia, metodo, caminho, token, corpo) {
  const cabecalhos = {};
  if (token) cabecalhos.Authorization = 'Bearer ' + token;
  if (corpo) cabecalhos['Content-Type'] = 'application/json';
  const resposta = await fetch(agencias.url(idAgencia) + caminho, {
    method: metodo,
    headers: cabecalhos,
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  const dados = await resposta.json().catch(() => null);
  if (!resposta.ok) throw new Error(`${metodo} ${caminho} -> HTTP ${resposta.status} ${JSON.stringify(dados)}`);
  return dados;
}

async function criarConta(usuario, senha, idAgencia, conta) {
  const { token } = await api(idAgencia, 'POST', '/auth/login', null, { usuario, senha });
  return api(idAgencia, 'POST', '/contas', token, conta);
}

// ---------------------------------------------------------------- ciclo de vida

test.beforeAll(async () => {
  test.setTimeout(240_000);
  agencias.verificarPreRequisitos();
  await agencias.garantirTodasFora();
  agencias.limparLogs();
  await Promise.all([0, 1, 2].map((id) => agencias.subir(id)));

  // Equivale ao "demo.sh preparar": uma conta por agencia (conta N mora na agencia N % 3).
  await criarConta('ana', 'ana123', 0, { id: 0, nomeAluno: 'Ana', saldoInicial: 200 });
  await criarConta('bruno', 'bruno123', 1, { id: 1, nomeAluno: 'Bruno', saldoInicial: 80 });
  await criarConta('carla', 'carla123', 2, { id: 2, nomeAluno: 'Carla', saldoInicial: 120 });
});

test.afterAll(async () => {
  await agencias.derrubarTodas();
});

test.beforeEach(async ({ page }, testInfo) => {
  await page.addInitScript(scriptDaLegenda, testInfo.title);
  await page.goto('/');
});

// Copia o video de cada teste para evidencias/sprint2/videos com um nome legivel.
test.afterEach(async ({ page }, testInfo) => {
  const video = page.video();
  await page.close();
  if (!video) return;
  fs.mkdirSync(PASTA_VIDEOS, { recursive: true });
  const nome = testInfo.title.split(' - ')[0].trim().toLowerCase().replace(/[^a-z0-9]+/g, '-');
  await video.saveAs(path.join(PASTA_VIDEOS, `${nome}.webm`));
});

// ---------------------------------------------------------------- testes

test('teste A - transferencia entre agencias via RabbitMQ', async ({ page }) => {
  await legendar(page, 'TESTE A - Transferencia entre agencias, assincrona, via RabbitMQ\n'
    + 'Ana entra pela agencia 0', 2500);
  await entrar(page, 'ana', 'ana123', 0);
  await expect(linhaDaConta(page, 0)).toContainText('200,00');
  await legendar(page, 'As pilulas mostram o RELOGIO VETORIAL de cada agencia (no Sprint 1 era um numero de Lamport).\n'
    + 'Agencias diferentes, sem mensagem entre elas: vetores independentes.', 3500);

  await transferir(page, 0, 2, 30);
  await expect(page.locator('#dicaTransferencia')).toContainText('ENTRE AGENCIAS');
  await legendar(page, 'Conta 0 (agencia 0) -> conta 2 (agencia 2), R$ 30,00.\n'
    + 'A agencia 0 debita e so PUBLICA uma mensagem na exchange iceibank.eventos (routing key agencia.2.creditar).');
  await page.click('#formularioTransferencia button[type=submit]');
  await expect(page.locator('#mensagem')).toContainText('publicada no RabbitMQ');
  const texto = await page.locator('#mensagem').textContent();
  const [, posicaoOrigem] = texto.match(/Vetor do evento: \[(\d+),0,0\]/);
  await legendar(page, `Resposta imediata: "publicada". 200 agora significa "o broker aceitou a mensagem",\n`
    + `nao "o credito ja foi aplicado". O envio levou o vetor [${posicaoOrigem},0,0].`, 4000);

  await atualizarAte(page, () => expect(pilula(page, 2)).toContainText(`vetor [${posicaoOrigem},0,`, { timeout: 1000 }));
  await legendar(page, `A agencia 2 consumiu a mensagem: o vetor dela agora comeca com ${posicaoOrigem}\n`
    + '(regra 3: max posicao a posicao com o vetor recebido, +1 na propria posicao).', 4000);

  await sair(page);
  await legendar(page, 'Carla entra pela agencia 2 para conferir o credito', 1500);
  await entrar(page, 'carla', 'carla123', 2);
  await expect(linhaDaConta(page, 2)).toContainText('150,00');
  await linhaDaConta(page, 2).locator('button').click();
  await expect(page.locator('#corpoHistorico')).toContainText('TRANSFERENCIA_CREDITO_REMOTO');
  await legendar(page, 'Conta 2: 120 + 30 = R$ 150,00.\n'
    + 'O historico mostra TRANSFERENCIA_CREDITO_REMOTO, com o vetor recebido da agencia 0.', 4500);
});

test('teste B - agencia de destino fora do ar', async ({ page }) => {
  await legendar(page, 'TESTE B - Resiliencia: agencia de destino fora do ar\nAna entra pela agencia 0', 2500);
  await entrar(page, 'ana', 'ana123', 0);
  await expect(pilula(page, 1)).toContainText('1 conta(s)');
  await legendar(page, 'A agencia 1 esta no ar e guarda a conta 1 (do Bruno).\n'
    + 'Vamos derruba-la - o equivalente a fechar o terminal dela.', 3000);

  await agencias.derrubar(1);
  await atualizarAte(page, () => expect(pilula(page, 1)).toContainText('fora do ar', { timeout: 1000 }));
  await legendar(page, 'Agencia 1 FORA DO AR. Mesmo assim, Ana transfere R$ 10,00 da conta 0 para a conta 1.', 3000);

  await transferir(page, 0, 1, 10);
  await page.click('#formularioTransferencia button[type=submit]');
  await expect(page.locator('#mensagem')).toContainText('publicada no RabbitMQ');
  await legendar(page, 'Sucesso! No Sprint 1 (chamada REST direta) isto dava erro.\n'
    + 'Agora a mensagem fica retida na fila duravel ate a agencia 1 voltar.', 3500);

  await atualizarAte(page, () => expect(quantidadeNaFila(page, 'fila-agencia-1')).toHaveText('1', { timeout: 1000 }));
  await legendar(page, 'Cartao "Filas no RabbitMQ": fila-agencia-1 = 1 mensagem esperando - ninguem consumiu ainda.', 3500, cartaoDeFilas(page));

  await legendar(page, 'Subindo a agencia 1 de novo. E um processo novo: as contas, que vivem so em memoria, se perderam...', 1500);
  await agencias.subir(1);
  await atualizarAte(page, () => expect(quantidadeNaFila(page, 'fila-agencia-1.mortas')).toHaveText('1', { timeout: 1000 }));
  await expect(quantidadeNaFila(page, 'fila-agencia-1')).toHaveText('0');
  await expect(pilula(page, 1)).toContainText('0 conta(s)');
  await legendar(page, 'A agencia 1 voltou e CONSUMIU a mensagem (fila-agencia-1 = 0) - a mensageria nao falhou.\n'
    + 'Mas a conta 1 nao existe mais (0 contas): o credito falhou e foi para fila-agencia-1.mortas = 1.', 5000, cartaoDeFilas(page));

  await expect(linhaDaConta(page, 0)).toContainText('160,00');
  await legendar(page, 'E o debito continua aplicado na origem: conta 0 = 200 - 30 - 10 = R$ 160,00.\n'
    + 'A mensagem nao se perdeu, mas o sistema ainda nao esta consistente.', 4500);
});

test('teste C - fila de mortas e reprocessamento', async ({ page }) => {
  await legendar(page, 'TESTE C - Fila de mensagens mortas (funcionalidade adicional)\nBruno entra pela agencia 1', 2500);
  await entrar(page, 'bruno', 'bruno123', 1);
  await expect(page.locator('#contasVazias')).toBeVisible();
  await expect(quantidadeNaFila(page, 'fila-agencia-1.mortas')).toHaveText('1');
  await legendar(page, 'Bruno nao tem mais a conta 1 (sumiu no reinicio).\n'
    + 'O credito de R$ 10,00 esta guardado em fila-agencia-1.mortas, nao foi descartado.', 3500);

  await page.fill('#novaContaId', '1');
  await page.fill('#novaContaSaldo', '0');
  await page.fill('#novaContaNome', 'Bruno');
  await page.click('#formularioConta button[type=submit]');
  await expect(linhaDaConta(page, 1)).toContainText('0,00');
  await legendar(page, 'Conta 1 recriada com saldo R$ 0,00. Agora: "Reprocessar mensagens mortas desta agencia".', 3000);

  await page.click('#botaoReprocessar');
  await expect(page.locator('#mensagem')).toContainText('1 mensagem(ns) morta(s) devolvida(s)');
  await legendar(page, 'A mensagem morta voltou para fila-agencia-1 e sera consumida de novo...', 2000);

  await atualizarAte(page, () => expect(linhaDaConta(page, 1)).toContainText('10,00', { timeout: 1000 }));
  await expect(quantidadeNaFila(page, 'fila-agencia-1.mortas')).toHaveText('0');
  await legendar(page, 'O credito feito com a agencia fora do ar finalmente chegou: conta 1 = R$ 10,00.\n'
    + 'fila-agencia-1.mortas = 0.', 4000);

  await linhaDaConta(page, 1).locator('button').click();
  const historico = page.locator('#corpoHistorico');
  await expect(historico).toContainText('CREDITO_REMOTO_FALHOU');
  await expect(historico).toContainText('TRANSFERENCIA_CREDITO_REMOTO');
  await legendar(page, 'Historico da conta 1: CREDITO_REMOTO_FALHOU -> CRIAR_CONTA -> TRANSFERENCIA_CREDITO_REMOTO,\n'
    + 'com o mesmo idTransferencia e o vetorRecebido original preservado.', 5000);
});
