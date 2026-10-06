// Servidor estatico minimo para o frontend (substitui o "python -m http.server" do
// scripts/servir-frontend.sh, para nao depender de Python).
const http = require('http');
const fs = require('fs');
const path = require('path');

const PASTA = path.resolve(__dirname, '..', '..', 'frontend');
const PORTA = Number(process.env.PORTA_FRONTEND || 5500);
const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
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
    res.writeHead(200, { 'Content-Type': TIPOS[path.extname(arquivo)] || 'application/octet-stream' });
    res.end(conteudo);
  });
}).listen(PORTA, () => console.log(`Frontend em http://localhost:${PORTA}`));
