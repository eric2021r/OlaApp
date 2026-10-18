Add-Type -AssemblyName System.Drawing

function Col([string]$hex, [int]$a = 255) {
  $r = [Convert]::ToInt32($hex.Substring(1,2),16)
  $g = [Convert]::ToInt32($hex.Substring(3,2),16)
  $b = [Convert]::ToInt32($hex.Substring(5,2),16)
  return [System.Drawing.Color]::FromArgb($a,$r,$g,$b)
}
function ToPts([double[]]$c) {
  $n = [int]($c.Count/2)
  $pts = New-Object 'System.Drawing.PointF[]' $n
  for ($i=0; $i -lt $n; $i++) { $pts[$i] = New-Object System.Drawing.PointF([single]$c[$i*2], [single]$c[$i*2+1]) }
  return ,$pts
}

$script:FORMAS = New-Object System.Collections.ArrayList
function AddPoly([double[]]$pts, [string]$fill) { [void]$script:FORMAS.Add(@{ tipo='poly'; pts=$pts; fill=$fill }) }
function AddRect([double]$x,[double]$y,[double]$w,[double]$h,[string]$fill) { [void]$script:FORMAS.Add(@{ tipo='rect'; x=$x; y=$y; w=$w; h=$h; fill=$fill }) }
function AddLimb([double]$x0,[double]$y0,[double]$x1,[double]$y1,[double]$w,[string]$fill) {
  $dx=$x1-$x0; $dy=$y1-$y0; $len=[Math]::Sqrt($dx*$dx+$dy*$dy)
  $px=-$dy/$len*$w/2; $py=$dx/$len*$w/2
  AddPoly @(($x0+$px),($y0+$py), ($x1+$px),($y1+$py), ($x1-$px),($y1-$py), ($x0-$px),($y0-$py)) $fill
}

$P = @{
  pele      = '#6BAA3A'
  peleClara = '#7EBF4A'
  peleEsc   = '#4F8A2E'
  colete    = '#6B4423'
  couro     = '#4A3020'
  pano      = '#8B2E2E'
  olho      = '#FFE066'
  pupila    = '#1B1220'
  boca      = '#2B1A14'
  presa     = '#F2EBD3'
  madeira   = '#7A5230'
  madeiraE  = '#5A3A20'
  ferro     = '#BFC4C9'
  contorno  = '#191106'
}

# ---------------- ORELHAS ----------------
AddPoly @(33,28, 33,47, 17,23) $P.pele
AddPoly @(67,28, 67,47, 83,23) $P.pele
AddPoly @(33,31, 33,43, 23,27) $P.peleEsc
AddPoly @(67,31, 67,43, 77,27) $P.peleEsc

# ---------------- PERNAS E BOTAS ----------------
AddLimb 44 68 43 80 10 $P.peleEsc
AddLimb 56 68 57 80 10 $P.peleEsc
AddRect 38 79 11 7 $P.couro
AddRect 51 79 11 7 $P.couro

# ---------------- TRONCO ----------------
AddRect 38 52 24 20 $P.pele
AddRect 35 56 30 12 $P.colete
AddRect 42 68 16 7 $P.pano

# ---------------- BRACOS ----------------
AddLimb 41 56 31 70 9 $P.pele
AddLimb 62 58 72 46 9 $P.pele

# ---------------- PORRETE ----------------
AddLimb 72 46 76 32 6 $P.madeira
AddRect 70 19 14 14 $P.madeiraE
AddPoly @(73,21, 81,21, 77,12) $P.ferro
AddPoly @(82,23, 82,29, 89,26) $P.ferro
AddPoly @(73,14, 77,18, 69,18) $P.ferro

# ---------------- CABECA ----------------
AddRect 32 18 36 32 $P.pele
AddRect 32 18 36 5 $P.peleClara
AddRect 32 46 36 4 $P.peleEsc
AddRect 36 25 8 7 $P.olho
AddRect 56 25 8 7 $P.olho
AddRect 38.5 27 3 4 $P.pupila
AddRect 58.5 27 3 4 $P.pupila
AddPoly @(46,32, 54,32, 50,45) $P.peleEsc
AddRect 40 45 20 4 $P.boca
AddPoly @(42,49, 46,49, 44,41) $P.presa
AddPoly @(54,49, 58,49, 56,41) $P.presa

function DrawGoblin([System.Drawing.Graphics]$g) {
  $cOut = Col $P.contorno
  $penOut = New-Object System.Drawing.Pen($cOut, 3.0)
  $penOut.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
  $brOut = New-Object System.Drawing.SolidBrush($cOut)
  foreach ($f in $FORMAS) {
    if ($f.tipo -eq 'rect') {
      $g.FillRectangle($brOut, [single]$f.x, [single]$f.y, [single]$f.w, [single]$f.h)
      $g.DrawRectangle($penOut, [single]$f.x, [single]$f.y, [single]$f.w, [single]$f.h)
    } else {
      $pts = ToPts $f.pts
      $g.FillPolygon($brOut, $pts)
      $g.DrawPolygon($penOut, $pts)
    }
  }
  foreach ($f in $FORMAS) {
    $br = New-Object System.Drawing.SolidBrush((Col $f.fill))
    if ($f.tipo -eq 'rect') {
      $g.FillRectangle($br, [single]$f.x, [single]$f.y, [single]$f.w, [single]$f.h)
    } else {
      $pts = ToPts $f.pts
      $g.FillPolygon($br, $pts)
    }
    $br.Dispose()
  }
  $penOut.Dispose(); $brOut.Dispose()
}

function DrawFundo([System.Drawing.Graphics]$g, [int]$size, [bool]$redondo) {
  $rect = New-Object System.Drawing.Rectangle(0,0,$size,$size)
  $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, (Col '#33250F'), (Col '#120C05'), 90)
  if ($redondo) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddEllipse(0,0,$size,$size)
    $g.FillPath($lg, $p)
  } else {
    $g.FillRectangle($lg, $rect)
  }
  $aneis = @(@(0.480,0.470,'#3A2A12',150), @(0.470,0.330,'#5C4319',150), @(0.460,0.190,'#7E5E24',150))
  foreach ($a in $aneis) {
    $cx = $size*0.50; $cy = $size*0.44; $r = $size*[double]$a[1]
    $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
    $gp.AddEllipse([single]($cx-$r), [single]($cy-$r), [single]($r*2), [single]($r*2))
    $br = New-Object System.Drawing.SolidBrush((Col $a[2] ([int]$a[3])))
    $g.FillPath($br, $gp)
    $br.Dispose(); $gp.Dispose()
  }
  $lg.Dispose()
}

function AplicarTransform([System.Drawing.Graphics]$g, [int]$size, [string]$modo) {
  if ($modo -eq 'fg') {
    $s = $size * 66.0 / 75.0 / 108.0
  } else {
    $s = $size * 0.88 / 75.0
  }
  $ox = $size/2.0 - 53.0*$s
  $oy = $size/2.0 - 48.5*$s
  $g.Transform = New-Object System.Drawing.Drawing2D.Matrix([single]$s,0,0,[single]$s,[single]$ox,[single]$oy)
}

function NovoBitmap([int]$size, [string]$modo) {
  $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  return @($bmp, $g)
}

# ============================================================ saida
# Desenha os PNGs do icone dentro de app/src/main/res.
# Uso (na raiz do repositorio):
#   powershell -ExecutionPolicy Bypass -File tools\gerar-icone.ps1
#
# Gera, em cada densidade: ic_launcher.png (quadrado, para Android < 8),
# ic_launcher_round.png (redondo) e ic_launcher_foreground.png (usado pelo
# icone adaptativo do Android 8+, junto com drawable/ic_launcher_background.xml).

$dest = Join-Path $PSScriptRoot '..\app\src\main\res'
$dens = @(
  @{ d='mdpi';    legacy=48;  fg=108 },
  @{ d='hdpi';    legacy=72;  fg=162 },
  @{ d='xhdpi';   legacy=96;  fg=216 },
  @{ d='xxhdpi';  legacy=144; fg=324 },
  @{ d='xxxhdpi'; legacy=192; fg=432 }
)

foreach ($x in $dens) {
  $dir = Join-Path $dest ("mipmap-" + $x.d)
  New-Item -ItemType Directory -Force -Path $dir | Out-Null

  $r = NovoBitmap $x.legacy 'quadrado'; $b=$r[0]; $g=$r[1]
  DrawFundo $g $x.legacy $false; AplicarTransform $g $x.legacy 'quadrado'; DrawGoblin $g
  $g.Dispose(); $b.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()

  $r = NovoBitmap $x.legacy 'quadrado'; $b=$r[0]; $g=$r[1]
  DrawFundo $g $x.legacy $true; AplicarTransform $g $x.legacy 'quadrado'; DrawGoblin $g
  $g.Dispose(); $b.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()

  $r = NovoBitmap $x.fg 'fg'; $b=$r[0]; $g=$r[1]
  AplicarTransform $g $x.fg 'fg'; DrawGoblin $g
  $g.Dispose(); $b.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
}

"icones gerados em $dest"
