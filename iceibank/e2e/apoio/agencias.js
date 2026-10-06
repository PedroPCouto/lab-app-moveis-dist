// Sobe e derruba as agencias direto pelo Node (java -jar), o equivalente aos
// scripts/subir-agencias.sh e parar-agencias.sh, sem depender de jq/curl/Git Bash.
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const RAIZ = path.resolve(__dirname, '..', '..');
const AGENCIA_DIR = path.join(RAIZ, 'agencia');
const JAR = path.join(AGENCIA_DIR, 'target', 'iceibank-0.0.1-SNAPSHOT.jar');
const DADOS = path.join(AGENCIA_DIR, 'data');
const NUMERO_AGENCIAS = 3;

const processos = {};

function url(id) {
  return `http://localhost:${4000 + id}`;
}

function java() {
  return process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
}

async function noAr(id) {
  try {
    return (await fetch(url(id) + '/status')).ok;
  } catch {
    return false;
  }
}

async function esperar(condicao, descricao, limiteMs = 90_000) {
  const fim = Date.now() + limiteMs;
  while (Date.now() < fim) {
    if (await condicao()) return;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Tempo esgotado esperando: ${descricao}`);
}

function verificarPreRequisitos() {
  if (!process.env.RABBITMQ_URL) {
    throw new Error('RABBITMQ_URL nao definida. Rode pelo e2e/rodar.ps1, que le a variavel de usuario.');
  }
  if (!fs.existsSync(JAR)) {
    throw new Error(`Jar nao encontrado em ${JAR}. Rode "./mvnw package -DskipTests" em agencia/.`);
  }
}

async function garantirTodasFora() {
  for (let id = 0; id < NUMERO_AGENCIAS; id++) {
    if (await noAr(id)) {
      throw new Error(`A agencia ${id} ja esta no ar (porta ${4000 + id}). Derrube-a antes de rodar os testes.`);
    }
  }
}

// Equivale ao --limpar-logs, mas guarda uma copia dos logs anteriores em data/anteriores-<data>.
function limparLogs() {
  fs.mkdirSync(DADOS, { recursive: true });
  const antigos = fs.readdirSync(DADOS).filter((f) => f.endsWith('.jsonl') || f.endsWith('.out'));
  if (antigos.length === 0) return;
  const copia = path.join(DADOS, 'anteriores-' + new Date().toISOString().replace(/[:.]/g, '-'));
  fs.mkdirSync(copia);
  for (const arquivo of antigos) fs.renameSync(path.join(DADOS, arquivo), path.join(copia, arquivo));
}

async function subir(id) {
  const saida = fs.openSync(path.join(DADOS, `agencia-${id}.out`), 'a');
  const filho = spawn(java(), ['-jar', JAR], {
    cwd: AGENCIA_DIR,
    env: { ...process.env, AGENCIA_ID: String(id) },
    stdio: ['ignore', saida, saida],
    windowsHide: true,
  });
  fs.closeSync(saida);
  processos[id] = filho;
  filho.on('exit', () => {
    if (processos[id] === filho) delete processos[id];
  });
  await esperar(() => noAr(id), `agencia ${id} responder em ${url(id)} (veja agencia/data/agencia-${id}.out)`);
}

// Encerramento abrupto do processo: o mesmo que fechar o terminal da agencia.
async function derrubar(id) {
  const filho = processos[id];
  if (!filho) throw new Error(`A agencia ${id} nao foi iniciada por estes testes.`);
  filho.kill();
  await esperar(async () => !(await noAr(id)), `agencia ${id} sair do ar`, 20_000);
}

async function derrubarTodas() {
  for (const id of Object.keys(processos)) {
    await derrubar(Number(id)).catch(() => {});
  }
}

module.exports = {
  NUMERO_AGENCIAS,
  url,
  verificarPreRequisitos,
  garantirTodasFora,
  limparLogs,
  subir,
  derrubar,
  derrubarTodas,
};
