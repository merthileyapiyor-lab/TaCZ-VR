Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\taczvr\textures'

function Draw($name, $rows, $spec, $w = 16, $h = 16, $dir = 'item') {
    $palette = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal); foreach ($pair in $spec.Split(';')) { $palette[$pair.Substring(0, 1)] = $pair.Substring(2) }
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $rows.Count; $y++) {
        $row = $rows[$y]
        for ($x = 0; $x -lt $row.Length; $x++) {
            $c = [string]$row[$x]
            if ($c -ne '.' -and $palette.ContainsKey($c)) {
                $bmp.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($palette[$c]))
            }
        }
    }
    $path = Join-Path (Join-Path $root $dir) "$name.png"
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    # an 8x preview to look at
    $src = [System.Drawing.Image]::FromFile($path)
    $big = New-Object System.Drawing.Bitmap ($w * 8), ($h * 8)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.Clear([System.Drawing.Color]::FromArgb(255, 120, 130, 140))
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($src, 0, 0, $w * 8, $h * 8)
    $g.Dispose(); $src.Dispose()
    $big.Save((Join-Path $PSScriptRoot "..\build\ref\preview_$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $big.Dispose()
}

# the creative tab logo: a VR headset with TaCZ orange lenses
Draw 'logo' @(
'................',
'................',
'................',
'..############..',
'.#HHHHHHHHHHHH#.',
'#HhhhhhhhhhhhhH#',
'#HOOOOHHHHOOOOH#',
'#HOYOOHHHHOYOOH#',
'#HOOOOHHHHOOOOH#',
'#HOOOOH##HOOOOH#',
'#HHHHH#..#HHHHH#',
'.#HHH#....#HHH#.',
'..###......###..',
'................',
'................',
'................'
) '#=#141517;H=#3a3e45;h=#565c66;O=#ff7a1a;Y=#ffe0a8'

Draw 'flashbang' @(
'................',
'.......ss.......',
'......s##s......',
'......#rr#ss....',
'.....########s..',
'.....#LGGGGG#.s.',
'.....#LkGGkG#...',
'.....#LGGGGG#...',
'.....#LkGGkG#...',
'.....#LGGGGG#...',
'.....#LkGGkG#...',
'.....#LGGGGG#...',
'.....#LkGGkG#...',
'.....#LGGGGG#...',
'.....########...',
'................'
) '#=#33373b;G=#8b9197;L=#b4bac0;k=#1c1e20;s=#cfd3d6;r=#d9b44a'

Draw 'smoke_grenade' @(
'................',
'.......ss.......',
'......s##s......',
'......#rr#ss....',
'.....########s..',
'.....#lDDDDD#.s.',
'.....#lDDDDD#...',
'.....#WWWWWW#...',
'.....#WWWWWW#...',
'.....#lDDDDD#...',
'.....#lDDDDD#...',
'.....#lDDDDD#...',
'.....#lDDDDD#...',
'.....#lDDDDD#...',
'.....########...',
'................'
) '#=#262b1c;D=#56653a;l=#728451;W=#e9e9e4;s=#cfd3d6;r=#d9b44a'

Draw 'combat_knife' @(
'................',
'.............##.',
'............#EB#',
'...........#EB#.',
'..........#EB#..',
'.........#EB#...',
'........#EB#....',
'.......#EB#.....',
'......#EB#......',
'....gg#B#.......',
'....g#g#........',
'....#HHgg.......',
'...#HH#.g.......',
'..#HH#..........',
'.#HH#...........',
'.##.............'
) '#=#2b2e31;B=#aeb6bd;E=#eef2f4;g=#5e6368;H=#3f3128'

Draw 'medkit' @(
'................',
'.............n..',
'............n...',
'...........##...',
'..........#RW#..',
'.........#RRW#..',
'........#RRW#...',
'.......#RRW#....',
'......#RRW#.....',
'.....#RRW#......',
'....cccc#.......',
'.....cc.........',
'....#p#.........',
'...#p#..........',
'..#pp#..........',
'..###...........'
) '#=#3a3a3f;R=#d23a3a;W=#f2d6d6;n=#d7dde2;c=#e8e8e8;p=#4a8fd6'

Draw 'night_vision_goggles' @(
'................',
'................',
'................',
'................',
'.ssssssssssssss.',
'sSSSSSSSSSSSSSSs',
'sS####SSSS####Ss',
'sS#gG#SSSS#gG#Ss',
'sS#GG#S##S#GG#Ss',
'sS####S..S####Ss',
'.sSSSs....sSSSs.',
'..sss......sss..',
'................',
'................',
'................',
'................'
) 's=#1b1f15;S=#4a5537;#=#111310;G=#4dff63;g=#c8ffd0'

Draw 'grappling_hook' @(
'................',
'...RRR..........',
'..R...R.........',
'..R..#I#........',
'...R..I.........',
'.....#I#........',
'......I.........',
'......I.........',
'......I.........',
'......I.........',
'#.....I.....#...',
'I#....I....#I...',
'.I#...I...#I....',
'..II..I..II.....',
'...IIIIIII......',
'.....III........',
'................'
) '#=#4a4f55;I=#bcc3ca;R=#a67c52'

Draw 'radio' @(
'..........#.....',
'..........#.....',
'..........#.....',
'..........#.....',
'.....######.....',
'....#BBBBBB#....',
'....#GgGGGG#....',
'....#GGGGGG#....',
'....#BBBBBB#....',
'....#BkBkBB#....',
'....#BBBBBB#....',
'....#BkBkBB#....',
'....#BBBBBB#....',
'....#BkBkBB#....',
'....#BBBBBB#....',
'.....######.....'
) '#=#17191c;B=#3f454c;G=#63d163;g=#c9f5c9;k=#1f2226'

# helmet layer: a strap round the head and two green lenses at the front
$rows = @()
for ($y = 0; $y -lt 32; $y++) { $rows += ('.' * 64) }
function Put($x, $y, $c) {
    $r = $script:rows[$y].ToCharArray(); $r[$x] = $c; $script:rows[$y] = -join $r
}
for ($x = 0; $x -lt 32; $x++) { Put $x 11 's'; Put $x 12 's' }
for ($y = 0; $y -lt 8; $y++) { Put 11 $y 's'; Put 12 $y 's' }
foreach ($lx in 9, 13) {
    for ($y = 10; $y -le 13; $y++) { Put $lx $y '#'; Put ($lx + 1) $y '#' }
    Put $lx 11 'g'; Put ($lx + 1) 11 'G'; Put $lx 12 'G'; Put ($lx + 1) 12 'G'
}
Put 11 11 '#'; Put 12 11 '#'
Draw 'night_vision_layer_1' $rows 's=#2c3322;#=#141612;G=#4dff63;g=#c8ffd0' 64 32 'models\armor'
'done'
