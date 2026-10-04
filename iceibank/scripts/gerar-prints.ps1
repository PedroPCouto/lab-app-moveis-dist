<#
  Converte a saida de texto de um comando em um PNG com cara de terminal.

  Versao Windows/PowerShell do gerar-prints.py (que depende de Pillow e das fontes
  DejaVu do Linux). A saida e a saida real do comando - inclusive a linha com a
  data/hora da execucao, que o proprio demo.sh imprime no inicio.

  Uso:
    powershell -ExecutionPolicy Bypass -File scripts\gerar-prints.ps1 entrada.txt saida.png ["Titulo da janela"]
#>
param(
  [Parameter(Mandatory = $true)][string]$Entrada,
  [Parameter(Mandatory = $true)][string]$Saida,
  [string]$Titulo
)

Add-Type -AssemblyName System.Drawing

if (-not $Titulo) { $Titulo = Split-Path $Entrada -Leaf }

$TAMANHO = 11
$MARGEM = 18
$ALTURA_BARRA = 34

function Cor($r, $g, $b) { [System.Drawing.Color]::FromArgb($r, $g, $b) }
$FUNDO = Cor 13 17 23
$BARRA = Cor 30 36 46
$TEXTO = Cor 201 209 217
$CIANO = Cor 86 182 194
$VERDE = Cor 86 211 100
$VERMELHO = Cor 248 113 113
$AMBAR = Cor 251 191 36
$FRACO = Cor 125 133 144

function CorDaLinha([string]$linha) {
  $despida = $linha.Trim()
  if ($despida.StartsWith('===') -or $despida.StartsWith('---')) { return $CIANO }
  if ($despida.StartsWith('>>>')) { return $AMBAR }
  if ($despida.StartsWith('HTTP ')) {
    if ($despida -match '^HTTP 2') { return $VERDE } else { return $VERMELHO }
  }
  if ($linha.Contains('"erro"') -or $linha.Contains('FALHOU') -or $linha.Contains('inesperado')) { return $VERMELHO }
  if ($linha.Contains(' x  [')) { return $AMBAR }
  if ($linha.Contains('NAO concorrente')) { return $VERDE }
  if ($despida.StartsWith('Data/hora') -or $despida.StartsWith('Maquina:')) { return $FRACO }
  return $TEXTO
}

$linhas = [System.IO.File]::ReadAllText((Resolve-Path $Entrada), [System.Text.Encoding]::UTF8).TrimEnd("`r", "`n") -split "`r?`n"

$fonte = New-Object System.Drawing.Font('Consolas', $TAMANHO, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Point)
$fonteNegrito = New-Object System.Drawing.Font('Consolas', $TAMANHO, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Point)

$regua = [System.Drawing.Graphics]::FromImage((New-Object System.Drawing.Bitmap 1, 1))
$formato = [System.Drawing.StringFormat]::GenericTypographic
$larguraCaractere = $regua.MeasureString('MMMMMMMMMM', $fonte, 10000, $formato).Width / 10
$alturaLinha = [int][Math]::Ceiling($fonte.GetHeight($regua)) + 3

$colunas = ($linhas | Measure-Object -Property Length -Maximum).Maximum
$colunas = [Math]::Max($colunas, $Titulo.Length + 10)
$largura = [int]($colunas * $larguraCaractere) + 2 * $MARGEM
$altura = $ALTURA_BARRA + $linhas.Count * $alturaLinha + 2 * $MARGEM

$imagem = New-Object System.Drawing.Bitmap $largura, $altura
$desenho = [System.Drawing.Graphics]::FromImage($imagem)
$desenho.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$desenho.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$desenho.Clear($FUNDO)

$desenho.FillRectangle((New-Object System.Drawing.SolidBrush $BARRA), 0, 0, $largura, $ALTURA_BARRA)
$bolinhas = @((Cor 255 95 86), (Cor 255 189 46), (Cor 39 201 63))
for ($i = 0; $i -lt 3; $i++) {
  $centro = 18 + $i * 20
  $desenho.FillEllipse((New-Object System.Drawing.SolidBrush $bolinhas[$i]), $centro - 6, 11, 12, 12)
}
$desenho.DrawString($Titulo, $fonteNegrito, (New-Object System.Drawing.SolidBrush $FRACO), 84, 9, $formato)

$y = $ALTURA_BARRA + $MARGEM
foreach ($linha in $linhas) {
  $desenho.DrawString($linha, $fonte, (New-Object System.Drawing.SolidBrush (CorDaLinha $linha)), $MARGEM, $y, $formato)
  $y += $alturaLinha
}

$destino = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Saida))
$imagem.Save($destino, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output "$Saida  (${largura}x${altura})"
