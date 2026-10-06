const { defineConfig } = require('@playwright/test');

const LARGURA = 1280;
const ALTURA = 1200;

module.exports = defineConfig({
  testDir: '.',
  testMatch: 'sprint2.spec.js',
  // Os tres testes compartilham as mesmas agencias e o mesmo RabbitMQ: um de cada vez.
  workers: 1,
  timeout: 180_000,
  outputDir: 'resultados',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'relatorio' }]],
  use: {
    baseURL: 'http://localhost:5500',
    viewport: { width: LARGURA, height: ALTURA },
    video: { mode: 'on', size: { width: LARGURA, height: ALTURA } },
    trace: 'on',
    locale: 'pt-BR',
    // Desacelera cada acao para o video ficar acompanhavel.
    launchOptions: { slowMo: 250 },
  },
  webServer: {
    command: 'node apoio/servidor-frontend.js',
    url: 'http://localhost:5500',
    reuseExistingServer: true,
  },
});
