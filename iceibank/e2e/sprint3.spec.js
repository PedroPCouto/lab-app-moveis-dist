// Testes gravados do Sprint 3: o app Flutter (build web) contra as 3 agencias reais.
// Cada teste gera uma evidencia em evidencias/sprint3/ (app + terminal PowerShell real) e
// o video da sessao inteira vai para evidencias/sprint3/videos/.
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const agencias = require('./apoio/agencias');
const { powershell, compor, PASTA_EVIDENCIAS } = require('./apoio/evidencia');
const app = require('./apoio/app');

const VIEWPORT = { width: 412, height: 915 };
const PASTA_VIDEOS = path.join(PASTA_EVIDENCIAS, 'videos');
const liderEm = (id) => `Invoke-RestMethod http://localhost:${4000 + id}/bully/lider | Format-Table -AutoSize`;

test.describe.serial('Sprint 3 - app Flutter', () => {
  let contexto;
  let page;
  let tokenAna;

  test.beforeAll(async ({ browser }) => {
    agencias.verificarPreRequisitos({ exigirBroker: false });
    await agencias.garantirTodasFora();
    agencias.limparLogs();
    await Promise.all([0, 1, 2].map((id) => agencias.subir(id)));
    await expect.poll(app.lideres, { timeout: 30_000 }).toEqual([2, 2, 2]);

    tokenAna = await app.token('ana', 'ana123');
    for (const [id, saldo] of [[30, 200], [33, 50], [31, 10]]) {
      const r = await app.api('POST', id % 3, '/contas', { id, nomeAluno: 'Ana', saldoInicial: saldo }, tokenAna);
      expect(r.status).toBe(201);
    }

    contexto = await browser.newContext({
      viewport: VIEWPORT,
      recordVideo: { dir: path.join(__dirname, 'resultados-sprint3', 'video'), size: VIEWPORT },
    });
    page = await contexto.newPage();
  });

  test.afterAll(async () => {
    const video = page && page.video();
    if (contexto) await contexto.close();
    if (video) {
      fs.mkdirSync(PASTA_VIDEOS, { recursive: true });
      fs.copyFileSync(await video.path(), path.join(PASTA_VIDEOS, 'sprint3-app.webm'));
    }
    await agencias.derrubarTodas();
  });

  test('D1 - fluxo bancario: login, saldo, deposito, saque, transferencias e erros visiveis', async ({ browser }) => {
    await app.entrar(page, 'ana', 'ana123');
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 200,00/);

    await app.operar(page, 30, 'Depositar', { valor: '25,50' });
    await app.esperarNaTela(page, /Deposito de R\$ 25,50 na conta 30 confirmado/);
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 225,50/);

    await app.operar(page, 30, 'Sacar', { valor: '5' });
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 220,50/);

    await app.operar(page, 30, 'Transferir', { destino: 33, valor: '20' });
    await app.esperarNaTela(page, /concluida \(mesma agencia\)/);
    await app.esperarNaTela(page, /Saldo da conta 33: R\$ 70,00/);
    const posTransferencia = await page.screenshot();

    // erro de negocio devolvido pela API, mostrado na tela
    await app.operar(page, 33, 'Sacar', { valor: '1000' });
    await app.esperarNaTela(page, /Erro 400: Saldo insuficiente/);
    const erro = await page.screenshot();

    await compor(browser, {
      arquivo: 'app-fluxo-bancario.png',
      titulo: 'Parte D - fluxo bancario no app (mesma API REST do frontend web)',
      paineis: [
        { rotulo: 'Deposito, saque e transferencia local confirmados', png: posTransferencia },
        { rotulo: 'Erro da API (saldo insuficiente) visivel na tela', png: erro },
      ],
      terminal: powershell(['Get-Date', `(Invoke-RestMethod http://localhost:4000/status).contas -join ', '`]),
    });
  });

  test('D2 - painel mostra a lider atual', async ({ browser }) => {
    await app.esperarNaTela(page, /Lider atual: Agencia 2/);
    const tela = await page.screenshot();
    await compor(browser, {
      arquivo: 'app-lider-atual.png',
      titulo: 'Parte D - o app mostra a lider atual (GET /bully/lider a cada 3 s)',
      paineis: [{ rotulo: 'App: lider = agencia 2', png: tela }],
      terminal: powershell(['Get-Date', liderEm(0), liderEm(1), liderEm(2)]),
    });
  });

  test('D3 - failover aparece no app sem tocar na tela', async ({ browser }) => {
    await app.esperarNaTela(page, /Lider atual: Agencia 2/);
    const antes = await page.screenshot();
    const terminalAntes = powershell(['Get-Date', liderEm(0)]);

    await agencias.derrubar(2); // encerra o processo da lider, como fechar o terminal dela
    const terminalQueda = powershell(['Get-Date', `Test-NetConnection localhost -Port 4002 -InformationLevel Quiet -WarningAction SilentlyContinue`]);

    // ninguem toca no app: o polling de 3 s tem que mostrar a nova lider sozinho
    await app.esperarNaTela(page, /Lider atual: Agencia 1/, 30_000);
    const depois = await page.screenshot();
    const terminalDepois = powershell(['Get-Date', liderEm(0), liderEm(1)]);

    await compor(browser, {
      arquivo: 'app-failover.png',
      titulo: 'Parte D - failover visivel no app: a lider (agencia 2) cai e o painel muda sozinho',
      paineis: [
        { rotulo: 'Antes: lider = agencia 2', png: antes },
        { rotulo: 'Depois (sem tocar no app): lider = agencia 1', png: depois },
      ],
      terminal: [
        terminalAntes,
        '',
        '# agencia 2 derrubada (processo encerrado)',
        terminalQueda,
        '',
        terminalDepois,
      ].join('\n'),
    });

    // religa a 2 para os proximos testes: ela retoma a lideranca
    await agencias.subir(2);
    await app.esperarNaTela(page, /Lider atual: Agencia 2/, 30_000);
  });
});
