// Acoes no app Flutter (build web) pela arvore de acessibilidade: o app liga a semantica
// no navegador (main.dart), entao campos e botoes aparecem com os mesmos rotulos da tela.
const { expect } = require('@playwright/test');
const agencias = require('./agencias');

async function digitar(page, nome, texto) {
  const campo = page.getByRole('textbox', { name: nome });
  await campo.click();
  // o Flutter cria o <input> de edicao ao focar: sem a pausa, os primeiros caracteres se perdem
  await page.waitForTimeout(400);
  await page.keyboard.press('Control+A');
  await page.keyboard.type(String(texto), { delay: 50 });
}

async function textoDaTela(page) {
  return page.locator('body').ariaSnapshot();
}

async function esperarNaTela(page, padrao, timeout = 20_000) {
  await expect
    .poll(async () => textoDaTela(page), { timeout, intervals: [250, 500, 1000] })
    .toMatch(padrao);
}

async function entrar(page, usuario, senha) {
  await page.goto('/');
  await page.getByRole('textbox', { name: 'Usuario' }).waitFor({ timeout: 60_000 });
  await digitar(page, 'Usuario', usuario);
  await digitar(page, 'Senha', senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await esperarNaTela(page, /LIDER ATUAL DO CLUSTER/);
}

function cartaoDaConta(page, id) {
  return page.getByRole('group', { name: new RegExp(`^Conta ${id} - Agencia`) });
}

async function operar(page, id, botao, { valor, destino }) {
  await cartaoDaConta(page, id).getByRole('button', { name: botao }).click();
  if (destino !== undefined) await digitar(page, 'Conta de destino', destino);
  await digitar(page, 'Valor (R$)', valor);
  await page.getByRole('button', { name: 'Confirmar' }).click();
}

// Chamadas diretas a API (fora do navegador): preparar contas e simular "outro caminho".
async function api(metodo, idAgencia, caminho, corpo, token) {
  const resposta = await fetch(agencias.url(idAgencia) + caminho, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  const texto = await resposta.text();
  return { status: resposta.status, corpo: texto ? JSON.parse(texto) : null };
}

async function token(usuario, senha) {
  const r = await api('POST', 0, '/auth/login', { usuario, senha });
  return r.corpo.token;
}

async function lideres() {
  const r = await Promise.all(
    [0, 1, 2].map((id) => api('GET', id, '/bully/lider').then((x) => x.corpo.liderAtual).catch(() => undefined)),
  );
  return r;
}

module.exports = { digitar, textoDaTela, esperarNaTela, entrar, cartaoDaConta, operar, api, token, lideres };
