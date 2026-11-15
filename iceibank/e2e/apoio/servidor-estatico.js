// Servidor estatico minimo, parametrizavel: serve o frontend web (Sprint 1/2, porta 5500)
// ou o build web do app Flutter (Sprint 3, porta 5600), sem depender de Python.
//   node apoio/servidor-estatico.js <pasta> <porta>
const http = require('http');
const fs = require('fs');
const path = require('path');

const PASTA = path.resolve(process.argv[2] || path.join(__dirname, '..', '..', 'app_mobile', 'build', 'web'));
const PORTA = Number(process.argv[3] || 5600);
const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.wasm': 'application/wasm',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.map': 'application/json',
  '.bin': 'application/octet-stream',
};

http.createServer((req, res) => {
  const caminho = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const arquivo = path.join(PASTA, caminho === '/' ? 'index.html' : caminho);
  if (!arquivo.startsWith(PASTA)) {
    res.writeHead(403).end();
    return;
  }
  fs.readFile(arquivo, (erro, conteudo) => {
    if (erro) {
      res.writeHead(404).end('nao encontrado');
      return;
    }
    res.writeHead(200, {
      'Content-Type': TIPOS[path.extname(arquivo)] || 'application/octet-stream',
      'Cache-Control': 'no-cache',
    });
    res.end(conteudo);
  });
}).listen(PORTA, () => console.log(`Servindo ${PASTA} em http://localhost:${PORTA}`));
