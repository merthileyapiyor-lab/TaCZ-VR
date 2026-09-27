Add-Type -AssemblyName System.Drawing
$res = Join-Path $PSScriptRoot '..\src\main\resources\assets\taczvr'
$enc = New-Object Text.UTF8Encoding($false)
$inv = [Globalization.CultureInfo]::InvariantCulture

function N($v) { return ([double]$v).ToString('0.###', $inv) }
function Arr($a) { return '[' + (($a | ForEach-Object { N $_ }) -join ', ') + ']' }

function Texture($name, $rows, $spec) {
    $palette = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    foreach ($pair in $spec.Split(';')) { $palette[$pair.Substring(0, 1)] = $pair.Substring(2) }
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $c = [string]$rows[$y][$x]
            if ($palette.ContainsKey($c)) { $bmp.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($palette[$c])) }
        }
    }
    $bmp.Save("$res\textures\item\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# a box: from, to, uv region for all faces, per face overrides
function Box($from, $to, $uv, $over = @{}) { return @{ from = $from; to = $to; uv = $uv; over = $over } }

$generated = @'
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}
'@
# like $generated, but held upright in the fist by others (a flat picture lies pointing forward there)
$upright = @'
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 1.5, 2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}
'@
$handheld = @'
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}
'@

# $tilt: -45 turns a model standing up into the diagonal a tool sprite has (handle bottom left, tip top right)
function Model($name, $boxes, $display, $tilt = 0) {
    $elements = @()
    foreach ($b in $boxes) {
        $faces = @()
        foreach ($f in 'north', 'south', 'east', 'west', 'up', 'down') {
            $uv = if ($b.over.ContainsKey($f)) { $b.over[$f] } else { $b.uv }
            $faces += "        `"$f`": {`"uv`": $(Arr $uv), `"texture`": `"#t`"}"
        }
        $rot = if ($tilt -ne 0) { ",`n      `"rotation`": {`"angle`": $tilt, `"axis`": `"z`", `"origin`": [8, 8, 8]}" } else { '' }
        $elements += "    {`n      `"from`": $(Arr $b.from),`n      `"to`": $(Arr $b.to)$rot,`n      `"faces`": {`n$($faces -join ",`n")`n      }`n    }"
    }
    $json = "{`n  `"ambientocclusion`": false,`n  `"textures`": {`n    `"particle`": `"taczvr:item/$name`",`n    `"t`": `"taczvr:item/${name}_model`"`n  },`n  `"display`": {`n$display`n  },`n  `"elements`": [`n$($elements -join ",`n")`n  ]`n}`n"
    [IO.File]::WriteAllText("$res\models\item\${name}_3d.json", $json, $enc)
    # 3D in the hands and in the world, the flat picture in the inventory
    $item = "{`n  `"loader`": `"forge:separate_transforms`",`n  `"gui_light`": `"front`",`n  `"base`": {`"parent`": `"taczvr:item/${name}_3d`"},`n  `"perspectives`": {`n    `"gui`": {`"parent`": `"minecraft:item/generated`", `"textures`": {`"layer0`": `"taczvr:item/$name`"}}`n  }`n}`n"
    [IO.File]::WriteAllText("$res\models\item\$name.json", $item, $enc)
}

# --- grenades: a rounded body, fuze on top, the spoon down the side, pin and ring on the other side ---
function GrenadeTop($bodyTop, $uvFuze, $uvSpoon, $uvRing, $side) {
    $t = $bodyTop
    return @(
        (Box @(7, $t, 7) @(9, ($t + 1.5), 9) $uvFuze),
        (Box @(8, ($t + 1.5), 7.5) @(($side + 0.5), ($t + 2), 8.5) $uvSpoon),
        (Box @($side, ($t - 6), 7.5) @(($side + 0.5), ($t + 2), 8.5) $uvSpoon),
        (Box @(4.5, ($t + 1.5), 6.5) @(5, ($t + 2), 9.5) $uvRing),
        (Box @(4.5, ($t - 0.5), 6.5) @(5, $t, 9.5) $uvRing),
        (Box @(4.5, $t, 6.5) @(5, ($t + 1.5), 7) $uvRing),
        (Box @(4.5, $t, 9) @(5, ($t + 1.5), 9.5) $uvRing),
        (Box @(5, ($t + 0.5), 7.75) @(7, ($t + 1), 8.25) $uvRing)
    )
}

Texture 'grenade_model' @(
'OOOoOOOoDDDDSSSS',
'OhOoOhOoDDDDSSSS',
'OOOoOOOoDDDDSSSS',
'ooooooooDDDDSSSS',
'OOOoOOOoRRRRBBBB',
'OhOoOhOoRRRRBBBB',
'OOOoOOOoRRRRBBBB',
'ooooooooRRRRBBBB',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO',
'OOOOOOOOOOOOOOOO'
) 'O=#4f5a2e;o=#343c1d;h=#6a7840;D=#2b2d30;S=#b9bec4;R=#c9ccd0;B=#353d1f'
$grid = @(0, 0, 8, 8); $plain = @(0, 8, 4, 12); $fuze = @(8, 0, 12, 4); $spoon = @(12, 0, 16, 4); $ring = @(8, 4, 12, 8); $bottom = @(12, 4, 16, 8)
$boxes = @(
    (Box @(5, 2, 6) @(11, 10, 10) $grid @{ up = $plain; down = $bottom }),
    (Box @(6, 2, 5) @(10, 10, 11) $grid @{ up = $plain; down = $bottom }),
    (Box @(6, 10, 6) @(10, 11, 10) $plain),
    (Box @(6.5, 1.5, 6.5) @(9.5, 2, 9.5) $bottom)
) + (GrenadeTop 11 $fuze $spoon $ring 11)
Model 'grenade' $boxes $upright

Texture 'flashbang_model' @(
'GGGGGGGGDDDDSSSS',
'GhGGGhGGDDDDSSSS',
'GGGGGGGGDDDDSSSS',
'GGGhGGGhDDDDSSSS',
'GGGGGGGGRRRRCCCC',
'GhGGGhGGRRRRCCCC',
'GGGGGGGGRRRRCCCC',
'GGGhGGGhRRRRCCCC',
'GGGGLLLLLLLLLLLL',
'GGGGLLLLLLLLLLLL',
'GGGGLLLLLLLLLLLL',
'GGGGLLLLLLLLLLLL',
'GGGGGGGGGGGGGGGG',
'GGGGGGGGGGGGGGGG',
'GGGGGGGGGGGGGGGG',
'GGGGGGGGGGGGGGGG'
) 'G=#8b9197;h=#1c1e20;L=#b4bac0;D=#2b2d30;S=#cfd3d6;R=#d9b44a;C=#5a6066'
$holes = @(0, 0, 8, 8); $cap = @(12, 4, 16, 8); $fuze = @(8, 0, 12, 4); $spoon = @(12, 0, 16, 4); $ring = @(8, 4, 12, 8)
$boxes = @(
    (Box @(5.5, 2, 6) @(10.5, 11, 10) $holes @{ up = $cap; down = $cap }),
    (Box @(6, 2, 5.5) @(10, 11, 10.5) $holes @{ up = $cap; down = $cap }),
    (Box @(5.3, 10, 5.8) @(10.7, 11, 10.2) $cap),
    (Box @(5.8, 10, 5.3) @(10.2, 11, 10.7) $cap),
    (Box @(5.3, 2, 5.8) @(10.7, 3, 10.2) $cap),
    (Box @(5.8, 2, 5.3) @(10.2, 3, 10.7) $cap)
) + (GrenadeTop 11 $fuze $spoon $ring 10.7)
Model 'flashbang' $boxes $upright

Texture 'smoke_grenade_model' @(
'lDDDDDDDKKKKSSSS',
'lDDDDDDDKKKKSSSS',
'lDDDDDDDKKKKSSSS',
'lDDDDDDDKKKKSSSS',
'lDDDDDDDRRRRTTTT',
'lDDDDDDDRRRRTTTT',
'lDDDDDDDRRRRTTTT',
'lDDDDDDDRRRRTTTT',
'WWWWWWWWDDDDDDDD',
'WkWkkWkWDDDDDDDD',
'WWWWWWWWDDDDDDDD',
'WWWWWWWWDDDDDDDD',
'DDDDDDDDDDDDDDDD',
'DDDDDDDDDDDDDDDD',
'DDDDDDDDDDDDDDDD',
'DDDDDDDDDDDDDDDD'
) 'D=#56653a;l=#728451;W=#e9e9e4;k=#3a3a3a;K=#2b2d30;S=#cfd3d6;R=#d9b44a;T=#3b4628'
$body = @(0, 0, 8, 8); $band = @(0, 8, 8, 12); $top = @(12, 4, 16, 8); $fuze = @(8, 0, 12, 4); $spoon = @(12, 0, 16, 4); $ring = @(8, 4, 12, 8)
$boxes = @(
    (Box @(5.5, 2, 6) @(10.5, 11, 10) $body @{ up = $top; down = $top }),
    (Box @(6, 2, 5.5) @(10, 11, 10.5) $body @{ up = $top; down = $top }),
    (Box @(5.4, 5.5, 5.9) @(10.6, 7.5, 10.1) $band @{ up = $top; down = $top }),
    (Box @(5.9, 5.5, 5.4) @(10.1, 7.5, 10.6) $band @{ up = $top; down = $top })
) + (GrenadeTop 11 $fuze $spoon $ring 10.6)
Model 'smoke_grenade' $boxes $upright

# --- the knife, standing up, then tilted like a sword sprite ---
Texture 'combat_knife_model' @(
'BBBBEEEEggggPPPP',
'BBBBEEEEggggPPPP',
'BBBBEEEEggggPPPP',
'BBBBEEEEggggPPPP',
'HHHHssssBBBBBBBB',
'hhhhssssBBBBBBBB',
'HHHHssssBBBBBBBB',
'hhhhssssBBBBBBBB',
'HHHHBBBBBBBBBBBB',
'hhhhBBBBBBBBBBBB',
'HHHHBBBBBBBBBBBB',
'hhhhBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB'
) 'B=#aeb6bd;E=#eef2f4;s=#7d858c;g=#4e5358;P=#3e4247;H=#2a2522;h=#463d36'
$blade = @(0, 0, 4, 4); $edge = @(4, 0, 8, 4); $guard = @(8, 0, 12, 4); $pommel = @(12, 0, 16, 4); $grip = @(0, 4, 4, 12); $spine = @(4, 4, 8, 8)
$boxes = @(
    (Box @(7, 0, 7) @(9, 1, 9) $pommel),
    (Box @(7.25, 1, 7.25) @(8.75, 6, 8.75) $grip @{ up = $pommel; down = $pommel }),
    (Box @(6, 6, 7) @(10, 6.75, 9) $guard),
    (Box @(7, 6.75, 7.75) @(9, 13.5, 8.25) $blade @{ east = $edge; west = $spine }),
    (Box @(7, 13.5, 7.75) @(8.5, 14.75, 8.25) $blade @{ east = $edge; west = $spine; up = $edge }),
    (Box @(7, 14.75, 7.75) @(7.75, 15.75, 8.25) $blade @{ east = $edge; west = $spine; up = $edge })
)
Model 'combat_knife' $boxes $handheld -45

# --- the syringe, needle up, tilted like the sprite ---
Texture 'medkit_model' @(
'WRRWFFFFPPPPNNNN',
'kRRWFFFFPPPPNNNN',
'WRRWFFFFPPPPNNNN',
'kRRWFFFFPPPPNNNN',
'WRRWGGGGbbbbRRRR',
'kRRWGGGGbbbbRRRR',
'WWWWGGGGbbbbRRRR',
'kWWWGGGGbbbbRRRR',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW',
'WWWWWWWWWWWWWWWW'
) 'W=#e3eaee;R=#d23a3a;k=#8f989e;F=#f7f7f7;P=#4a8fd6;N=#d7dde2;G=#8c9399;b=#202226'
$barrel = @(0, 0, 4, 8); $white = @(4, 0, 8, 4); $plunger = @(8, 0, 12, 4); $needle = @(12, 0, 16, 4); $hub = @(4, 4, 8, 8); $rubber = @(8, 4, 12, 8)
$boxes = @(
    (Box @(6.5, 0.5, 6.5) @(9.5, 1, 9.5) $plunger),
    (Box @(7.5, 1, 7.5) @(8.5, 3.5, 8.5) $plunger),
    (Box @(5.5, 3.5, 7) @(10.5, 4, 9) $white),
    (Box @(6.75, 4, 6.75) @(9.25, 12, 9.25) $barrel @{ up = $white; down = $rubber }),
    (Box @(7.4, 12, 7.4) @(8.6, 13, 8.6) $hub),
    (Box @(7.85, 13, 7.85) @(8.15, 16, 8.15) $needle)
)
Model 'medkit' $boxes $generated -45

# --- the grappling hook: ring and rope on top, four prongs curving up at the bottom, like the sprite ---
Texture 'grappling_hook_model' @(
'SSSSddddTTTTRRRR',
'SSSSddddTTTTrRRr',
'SSSSddddTTTTRRRR',
'SSSSddddTTTTrRRr',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS',
'SSSSSSSSSSSSSSSS'
) 'S=#bcc3ca;d=#6d747b;T=#e2e6ea;R=#a67c52;r=#7a5a3a'
$steel = @(0, 0, 4, 4); $dark = @(4, 0, 8, 4); $tip = @(8, 0, 12, 4); $rope = @(12, 0, 16, 4)
$boxes = @(
    (Box @(7.25, 4, 7.25) @(8.75, 13, 8.75) $steel),
    (Box @(6.75, 2.75, 6.75) @(9.25, 4.5, 9.25) $dark),
    (Box @(6.5, 13, 7.5) @(7, 15.75, 8.5) $steel),
    (Box @(9, 13, 7.5) @(9.5, 15.75, 8.5) $steel),
    (Box @(7, 15.25, 7.5) @(9, 15.75, 8.5) $steel),
    (Box @(4.5, 14, 7.6) @(6.5, 14.75, 8.4) $rope),
    (Box @(3.75, 12, 7.6) @(4.5, 14.75, 8.4) $rope)
)
foreach ($p in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
    $dx = $p[0]; $dz = $p[1]
    if ($dx -ne 0) {
        $a0 = if ($dx -gt 0) { 9.25 } else { 4 }; $a1 = if ($dx -gt 0) { 12 } else { 6.75 }
        $t0 = if ($dx -gt 0) { 11 } else { 4 }; $t1 = $t0 + 1
        $boxes += (Box @($a0, 2.75, 7.5) @($a1, 3.75, 8.5) $steel)
        $boxes += (Box @($t0, 3.75, 7.5) @($t1, 6.5, 8.5) $steel)
        $boxes += (Box @(($t0 + 0.25), 6.5, 7.75) @(($t1 - 0.25), 7.5, 8.25) $tip)
    } else {
        $a0 = if ($dz -gt 0) { 9.25 } else { 4 }; $a1 = if ($dz -gt 0) { 12 } else { 6.75 }
        $t0 = if ($dz -gt 0) { 11 } else { 4 }; $t1 = $t0 + 1
        $boxes += (Box @(7.5, 2.75, $a0) @(8.5, 3.75, $a1) $steel)
        $boxes += (Box @(7.5, 3.75, $t0) @(8.5, 6.5, $t1) $steel)
        $boxes += (Box @(7.75, 6.5, ($t0 + 0.25)) @(8.25, 7.5, ($t1 - 0.25)) $tip)
    }
}
Model 'grappling_hook' $boxes $handheld

# --- the radio: a box with the screen and keys on the front, antenna, knob, talk button on the side ---
Texture 'radio_model' @(
'BBBBBBBBBBBBLLLL',
'BGGGGBBBBBBBLLLL',
'BGgGGBBBBBBBLLLL',
'BGGGGBBBBBBBLLLL',
'BBBBBBBBAAAAOOOO',
'BkBkBBBBAAAAOOOO',
'BBBBBBBBAAAAOOOO',
'BkBkBBBBAAAAOOOO',
'BBBBBBBBKKKKBBBB',
'BkBkBBBBKKKKBBBB',
'BBBBBBBBKKKKBBBB',
'BBBBBBBBKKKKBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB',
'BBBBBBBBBBBBBBBB'
) 'B=#3f454c;L=#59616a;G=#63d163;g=#c9f5c9;k=#1f2226;A=#17191c;O=#e0782a;K=#26292d'
$front = @(0, 0, 6, 11); $side = @(8, 0, 12, 4); $edge = @(12, 0, 16, 4); $antenna = @(8, 4, 12, 8); $ptt = @(12, 4, 16, 8); $knob = @(8, 8, 12, 12)
$boxes = @(
    (Box @(5, 0, 6) @(11, 11, 10) $side @{ south = $front; up = $edge; down = $edge }),
    (Box @(9.25, 11, 7.5) @(10.25, 16.5, 8.5) $antenna),
    (Box @(9, 16.5, 7.25) @(10.5, 17.25, 8.75) $antenna),
    (Box @(6, 11, 7.25) @(7.5, 12, 8.75) $knob),
    (Box @(4.6, 5, 7) @(5, 8, 9) $ptt),
    (Box @(6.5, 3, 5.5) @(9.5, 9, 6) $knob)
)
Model 'radio' $boxes $upright
'done'
