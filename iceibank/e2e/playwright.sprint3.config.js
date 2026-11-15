const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: '.',
  testMatch: 'sprint3.spec.js',
  workers: 1,
  timeout: 240_000,
  outputDir: 'resultados-sprint3',
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5600',
    locale: 'pt-BR',
    launchOptions: { slowMo: 120 },
  },
  webServer: {
    command: 'node apoio/servidor-estatico.js ../app_mobile/build/web 5600',
    url: 'http://localhost:5600',
    reuseExistingServer: true,
  },
});
