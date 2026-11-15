# ICEIBank - Sprint 3, Parte C: Observando a eleição

## 7.1 `agencia/historico-lideranca.js`

Este script filtra os eventos de eleição dos logs das 3 agências e reconstrói a linha do tempo de lideranças:

```javascript
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const pastaDados = path.join(__dirname, 'data');
const arquivos = fs.readdirSync(pastaDados).filter((f) => f.endsWith('.jsonl'));

let eventos = [];
for (const arquivo of arquivos) {
  const linhas = fs
    .readFileSync(path.join(pastaDados, arquivo), 'utf-8')
    .trim()
    .split('\n')
    .filter(Boolean);
  eventos.push(...linhas.map((l) => JSON.parse(l)));
}

const tiposBully = ['ELEICAO_INICIADA', 'ELEICAO_RECEBIDA', 'TORNOU_LIDER', 'LIDER_ANUNCIADO', 'LIDER_NAO_RESPONDEU'];
const eventosBully = eventos.filter((e) => tiposBully.includes(e.tipo));
eventosBully.sort((a, b) => new Date(a.horaParede) - new Date(b.horaParede));

console.log('=== Histórico de eventos de eleição ===');
for (const evento of eventosBully) {
  console.log(`(${evento.horaParede}) [${evento.agencia}] ${evento.tipo}`, JSON.stringify(evento.detalhes));
}

const tornouLider = eventosBully.filter((e) => e.tipo === 'TORNOU_LIDER');
console.log('\n=== Linha do tempo de líderes ===');
for (const evento of tornouLider) {
  console.log(`A partir de ${evento.horaParede}: Agência ${evento.detalhes.idAgencia} é a líder`);
}
```

Como executar (depois de já ter gerado eventos nas seções anteriores):

```powershell
node historico-lideranca.js
```

## 7.2 Tarefa

1. Rodar o script depois de reproduzir a eleição inicial e o failover da Parte B.
2. Confirmar que a linha do tempo de líderes reflete corretamente a sequência: líder inicial -> queda -> nova líder.
3. Capturar um print para `evidencias/sprint3/historico-lideranca.png`.
4. Fazer o commit:

   ```powershell
   git add agencia/historico-lideranca.js evidencias/sprint3
   git commit -m "feat(observabilidade): adiciona historico de liderancas"
   ```

## 7.3 Perguntas - Parte C

Responder em `RESPOSTAS.md`:

1. Observando a saída do script, quantas vezes a liderança mudou de mãos durante a "tempestade" de eleições da subida inicial (Parte A), antes de estabilizar? Isso tem algum custo real para o sistema (rede, tempo), mesmo não afetando o resultado final?
