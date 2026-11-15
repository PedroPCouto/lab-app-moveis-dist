# ICEIBank - Sprint 3: Preparação do ambiente

Mesma convenção dos sprints anteriores: **Windows com PowerShell**.

## Requisitos de ambiente

- **Flutter SDK** instalado (`flutter doctor` sem erros críticos). O link de instalação está na seção de Referências do roteiro.
- Um **emulador Android** configurado, ou um **dispositivo físico** com depuração USB habilitada. O app pode ser testado em qualquer um dos dois.
- O **backend dos Sprints 1 e 2** (agências + RabbitMQ/CloudAMQP) continua sendo necessário. O app Flutter é um cliente a mais e não substitui nada.

## 4.1 Estrutura do repositório

Mesmo repositório dos sprints anteriores. A estrutura ganha a pasta do app Flutter:

```
iceibank/
├── agencia/
│   ├── src/
│   │   ├── app.js
│   │   ├── config.js
│   │   ├── routes.js
│   │   ├── controllers/
│   │   │   └── contasController.js    (atualizado - ganha /sincronizar)
│   │   └── services/
│   │       ├── vectorClock.js         (atualizado - ganha obterVetorAtual)
│   │       ├── eventLog.js
│   │       ├── mensageria.js
│   │       ├── compararVetores.js     (novo)
│   │       └── bullyService.js        (novo)
│   ├── data/
│   ├── mesclar-logs.js
│   └── historico-lideranca.js         (novo)
├── frontend/
├── app_mobile/                        (novo - projeto Flutter)
├── evidencias/
│   ├── sprint1/
│   ├── sprint2/
│   └── sprint3/
├── RESPOSTAS.md
├── .gitignore
└── README.md
```

Criar a pasta de evidências do sprint:

```powershell
New-Item -ItemType Directory -Force -Path evidencias/sprint3
```

## 4.2 Evidências de teste

Mesma convenção: prints reais, com `Get-Date` visível em algum terminal.

- `evidencias/sprint3/eleicao-inicial.png`: as 3 agências subindo e convergindo para a mesma líder (consultar `/bully/lider` nas 3).
- `evidencias/sprint3/failover.png`: a líder sendo derrubada e uma nova eleição acontecendo automaticamente (seção 6.2).
- `evidencias/sprint3/historico-lideranca.png`: a saída do script `historico-lideranca.js`.
- `evidencias/sprint3/app-lider-atual.png`: o app Flutter mostrando a líder atual.
- `evidencias/sprint3/app-failover.png`: o app Flutter atualizando o painel da líder após uma queda, sem o usuário precisar atualizar manualmente.
- `evidencias/sprint3/conflito-sincronizacao.png`: o backend recusando uma sincronização desatualizada (resposta 409), conforme a seção 9.3.
- `evidencias/sprint3/app-offline.png`: o app em modo avião, com uma operação feita offline aparecendo como pendente na tela.
- `evidencias/sprint3/app-sincronizacao.png`: o app reconectando e sincronizando sozinho a operação pendente, sem ação manual.

## 4.3 Portas exclusivas

Mesmo `OFFSET` pessoal dos sprints anteriores, já embutido no `config.js`. Sem mudanças nesta parte.

## 4.4 Disciplina de commits

Mesma orientação: trabalho individual, commits pequenos e incrementais ao longo das 3 semanas.
