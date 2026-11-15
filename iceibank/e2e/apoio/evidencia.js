// Monta os prints de evidencia do Sprint 3: captura(s) da tela do app ao lado de um
// "terminal" com a saida REAL de comandos PowerShell (Get-Date, Invoke-RestMethod...)
// executados naquele instante - a convencao do roteiro de ter a data/hora visivel.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const PASTA_EVIDENCIAS = path.resolve(__dirname, '..', '..', 'evidencias', 'sprint3');

/** Executa os comandos num PowerShell de verdade e devolve o texto, com "PS> comando" antes de cada um. */
function powershell(comandos, env = {}) {
  const partes = ['[Console]::OutputEncoding = [System.Text.Encoding]::UTF8', "$ProgressPreference = 'SilentlyContinue'"];
  for (const comando of comandos) {
    partes.push(`Write-Output ('PS> ' + '${comando.replace(/'/g, "''")}')`);
    partes.push(`try { (${comando}) | Out-String -Width 160 | ForEach-Object { $_.TrimEnd() } | Write-Output } catch { Write-Output ('ERRO: ' + $_.Exception.Message) }`);
  }
  const saida = execFileSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', partes.join('; ')], {
    encoding: 'utf8',
    timeout: 60_000,
    env: { ...process.env, ...env },
  });
  return saida.replace(/\r/g, '').replace(/\n{3,}/g, '\n\n').trim();
}

const escapar = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/**
 * Gera evidencias/sprint3/<arquivo>.png com os paineis do app lado a lado e o terminal.
 * paineis: [{ rotulo, png: Buffer }]
 */
async function compor(browser, { arquivo, titulo, paineis, terminal, nota }) {
  fs.mkdirSync(path.join(PASTA_EVIDENCIAS, 'saidas'), { recursive: true });
  if (terminal) {
    fs.writeFileSync(path.join(PASTA_EVIDENCIAS, 'saidas', arquivo.replace(/\.png$/, '.txt')), terminal + '\n');
  }
  const imagens = paineis
    .map((p) => `<figure><figcaption>${escapar(p.rotulo)}</figcaption>
      <img src="data:image/png;base64,${p.png.toString('base64')}"></figure>`)
    .join('');
  const linhas = (terminal || '')
    .split('\n')
    .map((l) => {
      const cls = l.startsWith('PS>') ? 'cmd' : l.startsWith('ERRO') ? 'erro' : '';
      return `<div class="${cls}">${escapar(l) || '&nbsp;'}</div>`;
    })
    .join('');
  const html = `<!doctype html><html><head><meta charset="utf-8"><style>
    body { margin: 0; background: #e9edf3; font-family: Segoe UI, Arial, sans-serif; }
    header { background: #1f4e8c; color: white; padding: 10px 16px; font-size: 16px; font-weight: 600; }
    header small { display: block; font-weight: 400; opacity: .85; font-size: 12px; }
    main { display: flex; gap: 16px; padding: 16px; align-items: flex-start; }
    figure { margin: 0; background: white; border-radius: 10px; padding: 8px; box-shadow: 0 1px 4px #0003; }
    figcaption { font-size: 13px; font-weight: 600; color: #1f4e8c; margin: 2px 4px 8px; }
    figure img { display: block; width: 330px; border: 1px solid #ccd; border-radius: 6px; }
    .terminal { background: #0d1117; color: #c9d1d9; border-radius: 10px; min-width: 420px; max-width: 640px;
      font: 12px/1.45 Consolas, monospace; box-shadow: 0 1px 4px #0005; overflow: hidden; }
    .barra { background: #1e242e; color: #7d8590; padding: 7px 12px; font: 600 12px Consolas, monospace; }
    .conteudo { padding: 10px 12px; white-space: pre-wrap; word-break: break-all; }
    .cmd { color: #56b6c2; } .erro { color: #f87171; }
    .nota { margin: 0 16px 16px; font-size: 12px; color: #444; }
  </style></head><body>
    <header>${escapar(titulo)}<small>ICEIBank - Sprint 3 - app Flutter (build web, Chromium com viewport de celular 412x915)</small></header>
    <main>${imagens}${terminal ? `<div class="terminal"><div class="barra">Windows PowerShell</div><div class="conteudo">${linhas}</div></div>` : ''}</main>
    ${nota ? `<p class="nota">${escapar(nota)}</p>` : ''}
  </body></html>`;
  const pagina = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await pagina.setContent(html);
  await pagina.screenshot({ path: path.join(PASTA_EVIDENCIAS, arquivo), fullPage: true });
  await pagina.close();
}

module.exports = { powershell, compor, PASTA_EVIDENCIAS };
