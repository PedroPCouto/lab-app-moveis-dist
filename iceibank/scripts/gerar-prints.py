#!/usr/bin/env python3
"""
Converte a saida de texto de um comando em um PNG com cara de terminal.

Serve para arquivar as evidencias da secao 4.2 sem depender de recortar a janela
do terminal na mao. A saida e a saida real do comando - inclusive a linha com a
data/hora da execucao, que o proprio demo.sh imprime no inicio.

Uso:
    python3 scripts/gerar-prints.py entrada.txt saida.png ["Titulo da janela"]
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
FONTE_NEGRITO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
TAMANHO = 15
MARGEM = 18
ALTURA_BARRA = 34

FUNDO = (13, 17, 23)
BARRA = (30, 36, 46)
TEXTO = (201, 209, 217)
CIANO = (86, 182, 194)
VERDE = (86, 211, 100)
VERMELHO = (248, 113, 113)
AMBAR = (251, 191, 36)
FRACO = (125, 133, 144)

def cor_da_linha(linha: str):
    despida = linha.strip()
    if despida.startswith("===") or despida.startswith("---"):
        return CIANO
    if despida.startswith(">>>"):
        return AMBAR
    if despida.startswith("HTTP "):
        codigo = despida.split()[1] if len(despida.split()) > 1 else ""
        return VERDE if codigo.startswith("2") else VERMELHO
    if '"erro"' in linha or "EMPATE" in linha:
        return VERMELHO if '"erro"' in linha else AMBAR
    if despida.startswith("Data/hora") or despida.startswith("Maquina:"):
        return FRACO
    return TEXTO

def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1

    entrada, saida = sys.argv[1], sys.argv[2]
    titulo = sys.argv[3] if len(sys.argv) > 3 else entrada.split("/")[-1]

    with open(entrada, encoding="utf-8") as arquivo:
        linhas = arquivo.read().rstrip("\n").split("\n")

    fonte = ImageFont.truetype(FONTE, TAMANHO)
    fonte_negrito = ImageFont.truetype(FONTE_NEGRITO, TAMANHO)

    regua = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    largura_caractere = regua.textlength("M", font=fonte)
    altura_linha = TAMANHO + 6

    colunas = max((len(linha) for linha in linhas), default=40)
    colunas = max(colunas, len(titulo) + 10)
    largura = int(colunas * largura_caractere) + 2 * MARGEM
    altura = ALTURA_BARRA + len(linhas) * altura_linha + 2 * MARGEM

    imagem = Image.new("RGB", (largura, altura), FUNDO)
    desenho = ImageDraw.Draw(imagem)

    desenho.rectangle([0, 0, largura, ALTURA_BARRA], fill=BARRA)
    for indice, cor in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        centro = 18 + indice * 20
        desenho.ellipse([centro - 6, 11, centro + 6, 23], fill=cor)
    desenho.text((84, 8), titulo, font=fonte_negrito, fill=FRACO)

    y = ALTURA_BARRA + MARGEM
    for linha in linhas:
        desenho.text((MARGEM, y), linha, font=fonte, fill=cor_da_linha(linha))
        y += altura_linha

    imagem.save(saida)
    print(f"{saida}  ({largura}x{altura})")
    return 0

if __name__ == "__main__":
    sys.exit(main())
