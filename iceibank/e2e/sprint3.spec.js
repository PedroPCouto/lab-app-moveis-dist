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

  // ---- Parte F: offline-first --------------------------------------------------------

  // O token vai por variavel de ambiente para o PowerShell, nunca escrito no comando.
  const saldoNoServidor = `Invoke-RestMethod http://localhost:4000/contas/30 -Headers @{Authorization="Bearer $env:TOKEN_ANA"} | Select-Object id, saldo, @{n='vetorAtual';e={$_.vetorAtual -join ','}} | Format-Table -AutoSize`;
  let telaOffline;
  let terminalOffline;

  test('F1 - em modo aviao, o deposito fica pendente na tela, sem erro', async ({ browser }) => {
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 200,50/);
    await contexto.setOffline(true); // "modo aviao" do navegador: navigator.onLine = false
    await app.esperarNaTela(page, /Conexao: offline/);

    await app.operar(page, 30, 'Depositar', { valor: '25' });
    await app.esperarNaTela(page, /Saldo provisorio da conta 30: R\$ 225,50/);
    await app.esperarNaTela(page, /Operacoes pendentes de sincronizacao/);
    // sem rede, o painel da lider tambem mostra que nenhuma agencia respondeu
    await app.esperarNaTela(page, /Nenhuma agencia respondeu/);
    telaOffline = await page.screenshot();
    terminalOffline = powershell(['Get-Date', saldoNoServidor], { TOKEN_ANA: tokenAna });

    await compor(browser, {
      arquivo: 'app-offline.png',
      titulo: 'Parte F - app offline: deposito de R$ 25,00 guardado como PENDENTE (UI otimista)',
      paineis: [{ rotulo: 'App em modo aviao: saldo provisorio + fila', png: telaOffline }],
      terminal: terminalOffline + '\n\n# a agencia ainda nao recebeu nada: saldo continua 200,50',
    });
  });

  test('F2 - ao reconectar, o app sincroniza sozinho', async ({ browser }) => {
    await contexto.setOffline(false); // so reconecta: nenhum toque na tela
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 225,50/, 30_000);
    await expect.poll(async () => app.textoDaTela(page)).not.toMatch(/Operacoes pendentes de sincronizacao/);
    const depois = await page.screenshot();

    await compor(browser, {
      arquivo: 'app-sincronizacao.png',
      titulo: 'Parte F - conexao de volta: a fila e sincronizada sem acao manual',
      paineis: [
        { rotulo: 'Antes (offline): R$ 225,50 provisorio', png: telaOffline },
        { rotulo: 'Depois (so reconectou): R$ 225,50 confirmado', png: depois },
      ],
      terminal: [terminalOffline, '', '# rede de volta - o app chama POST /contas/30/sincronizar sozinho', powershell(['Get-Date', saldoNoServidor], { TOKEN_ANA: tokenAna })].join('\n'),
    });
  });

  test('F3 - conflito: o saldo muda por outro caminho e o 409 aparece na tela', async ({ browser }) => {
    await contexto.setOffline(true);
    await app.esperarNaTela(page, /Conexao: offline/);
    await app.operar(page, 30, 'Sacar', { valor: '10' });
    await app.esperarNaTela(page, /Saldo provisorio da conta 30: R\$ 215,50/);
    const pendente = await page.screenshot();

    // "por outro caminho": deposito direto na API pelo PowerShell, com o app ainda offline
    const outroCaminho = powershell([
      'Get-Date',
      `Invoke-RestMethod -Method Post http://localhost:4000/contas/30/depositar -ContentType 'application/json' -Body '{"valor":50}' -Headers @{Authorization="Bearer $env:TOKEN_ANA"} | Select-Object id, saldo, @{n='vetorAtual';e={$_.vetorAtual -join ','}} | Format-Table -AutoSize`,
    ], { TOKEN_ANA: tokenAna });

    await contexto.setOffline(false);
    await app.esperarNaTela(page, /A agencia recusou a sincronizacao/, 30_000);
    await app.esperarNaTela(page, /saldo real agora: R\$ 275,50/);
    const conflito = await page.screenshot();
    const terminalConflito = powershell(['Get-Date', saldoNoServidor], { TOKEN_ANA: tokenAna });

    // a decisao e da pessoa usuaria: aplicar o saque sobre o saldo real
    await page.getByRole('button', { name: 'Aplicar sobre o saldo atual' }).first().click();
    await app.esperarNaTela(page, /Saldo da conta 30: R\$ 265,50/, 30_000);
    const resolvido = await page.screenshot();

    await compor(browser, {
      arquivo: 'app-conflito.png',
      titulo: 'Parte F - conflito de sincronizacao (HTTP 409) tratado de forma visivel no app',
      paineis: [
        { rotulo: '1. Offline: saque de R$ 10 pendente', png: pendente },
        { rotulo: '2. Reconectou: agencia recusou (409)', png: conflito },
        { rotulo: '3. Usuaria escolheu aplicar sobre o saldo real', png: resolvido },
      ],
      terminal: [outroCaminho, '', '# app reconecta -> 409 (vetor mudou) -> nada aplicado', terminalConflito].join('\n'),
    });
  });

  // ---- Funcionalidade adicional: painel do cluster ------------------------------------

  test('X1 - painel do cluster: agencias ativas e historico de trocas de lider', async ({ browser }) => {
    await agencias.derrubar(1); // uma agencia que NAO e a lider cai
    await app.esperarNaTela(page, /Agencia 1: fora do ar/, 20_000);
    await page.mouse.move(200, 600);
    await page.mouse.wheel(0, 3000); // rola ate o fim da lista, onde fica o painel
    await app.esperarNaTela(page, /Troca de lider: agencia 1 para agencia 2/);
    await page.waitForTimeout(800);
    const tela = await page.screenshot();

    await compor(browser, {
      arquivo: 'funcionalidade-adicional.png',
      titulo: 'Funcionalidade adicional - painel do cluster: quem esta no ar + trocas de lideranca vistas pelo app',
      paineis: [{ rotulo: 'Agencia 1 derrubada; historico 2 -> 1 -> 2 do failover', png: tela }],
      terminal: powershell([
        'Get-Date',
        'Test-NetConnection localhost -Port 4001 -InformationLevel Quiet -WarningAction SilentlyContinue',
        liderEm(0),
        `(Invoke-RestMethod http://localhost:4002/status) | Select-Object idAgencia, situacao, @{n='vetor';e={$_.relogioVetorial -join ','}} | Format-Table -AutoSize`,
      ]),
    });

    await agencias.subir(1);
    await app.esperarNaTela(page, /Agencia 1: no ar/, 20_000);
  });

  // ---- Regressao: o frontend web do Sprint 1 continua funcionando ---------------------

  test('R1 - regressao: frontend web e JWT continuam funcionando com a API do Sprint 3', async ({ browser }) => {
    const { spawn } = require('child_process');
    const servidor = spawn(process.execPath, [path.join(__dirname, 'apoio', 'servidor-estatico.js'),
      path.join(__dirname, '..', 'frontend'), '5500'], { stdio: 'ignore' });
    try {
      await expect.poll(() => fetch('http://localhost:5500').then((r) => r.ok).catch(() => false)).toBe(true);
      const web = await browser.newPage({ viewport: { width: 1280, height: 900 } });
      await web.goto('http://localhost:5500');
      await web.selectOption('#agenciaLogin', '0');
      await web.fill('#usuario', 'ana');
      await web.fill('#senha', 'ana123');
      await web.click('#formularioLogin button[type=submit]');
      await expect(web.locator('#telaApp')).toBeVisible();
      await expect(web.locator('#corpoContas')).toContainText('30');

      await web.fill('#transferenciaOrigem', '30');
      await web.fill('#transferenciaDestino', '33');
      await web.fill('#transferenciaValor', '5');
      await web.click('#formularioTransferencia button[type=submit]');
      await expect(web.locator('#mensagem')).toContainText(/mesma agencia|LOCAL/i);
      const tela = await web.screenshot({ fullPage: true });
      await web.close();

      // JWT: sem token -> 401; token valido -> 200 (agora com vetorAtual)
      const semToken = await app.api('GET', 0, '/contas/30');
      const comToken = await app.api('GET', 0, '/contas/30', null, tokenAna);
      expect(semToken.status).toBe(401);
      expect(comToken.status).toBe(200);
      expect(comToken.corpo.vetorAtual).toHaveLength(3);

      await compor(browser, {
        arquivo: 'regressao-frontend-jwt.png',
        titulo: 'Continuidade - frontend web (Sprint 1) e JWT funcionando sobre a API do Sprint 3',
        subtitulo: 'ICEIBank - Sprint 3 - frontend web sem alteracoes, servido em localhost:5500',
        paineis: [],
        terminal: powershell([
          'Get-Date',
          `try { Invoke-RestMethod http://localhost:4000/contas/30 } catch { 'HTTP ' + [int]$_.Exception.Response.StatusCode + ' sem token' }`,
          `Invoke-RestMethod http://localhost:4000/contas/30 -Headers @{Authorization="Bearer $env:TOKEN_ANA"} | Select-Object id, saldo, @{n='vetorAtual';e={$_.vetorAtual -join ','}} | Format-Table -AutoSize`,
        ], { TOKEN_ANA: tokenAna }),
        imagemLarga: { rotulo: 'Frontend web: login da Ana + transferencia local 30 -> 33', png: tela },
      });
    } finally {
      servidor.kill();
    }
  });
});
