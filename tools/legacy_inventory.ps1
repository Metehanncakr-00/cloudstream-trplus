# legacy_inventory.ps1
# Processes legacy plugins from legacy_plugins_decoded.json or legacy-plugins.json,
# builds legacy/migration-matrix.json and docs/LEGACY_INVENTORY.md

$ErrorActionPreference = "Stop"
$repoRoot = (Get-Item -Path $PSScriptRoot).Parent.FullName

$legacyFile = Join-Path $repoRoot "legacy_plugins_decoded.json"
if (-not (Test-Path $legacyFile)) {
    $legacyFile = Join-Path (Join-Path $repoRoot "legacy") "legacy-plugins.json"
}

if (-not (Test-Path $legacyFile)) {
    Write-Error "Legacy plugins file not found!"
}

$plugins = Get-Content $legacyFile -Raw -Encoding UTF8 | ConvertFrom-Json

# Copy to legacy/legacy-plugins.json
$outLegacyJson = Join-Path (Join-Path $repoRoot "legacy") "legacy-plugins.json"
$plugins | ConvertTo-Json -Depth 10 | Set-Content $outLegacyJson -Encoding UTF8
Write-Host "Saved $($plugins.Count) plugins to $outLegacyJson"

$knownDomains = @{
    "AnimeciX" = "https://animecix.tv"
    "Animeler" = "https://animeler.pw"
    "Animely" = "https://animecim.tv"
    "AnimPow" = "https://animpow.com"
    "Anizium" = "https://anizium.co"
    "AsyaAnimeleri" = "https://asyaanimeleri.top"
    "AsyaFanatiklerim" = "https://asyafanatiklerim.com"
    "AsyaMinik" = "https://asyaminik.com"
    "AsyaWatch" = "https://asyawatch.com"
    "BelgeselX" = "https://belgeselx.com"
    "CizgiMax" = "https://cizgimax.online"
    "CizgiveDizi" = "https://cizgivedizi.com"
    "Ddizi" = "https://www.ddizi.im"
    "DiziAsia" = "https://diziasia.com"
    "DiziAsya" = "https://api.diziasya.com"
    "DiziBox" = "https://www.dizibox.live"
    "DiziFilmORG" = "https://dizifilmizle.to"
    "Dizigecesi" = "https://dizigecesi.com"
    "DiziKorea" = "https://dizikorea3.com"
    "DiziLife" = "https://dizi74.life"
    "Dizilla" = "https://dizilla.now"
    "DiziMom" = "https://www.dizimom.diy"
    "DiziPal" = "https://dizipal.bid"
    "DiziPalOrijinal" = "https://dizipal1580.com"
    "Dizipod" = "https://dizipod.com"
    "DiziYo" = "https://www.diziyo.so"
    "DiziYou" = "https://www.diziyou.one"
    "DramaDizilerim" = "https://dramadizilerim.com"
    "FilmEkseni" = "https://filmekseni.vip"
    "FilmHane" = "https://www.filmhane.shop"
    "FilmMakinesi" = "https://filmmakinesi.to"
    "FilmModu" = "https://www.filmmodu.one"
    "Filmzal" = "https://filmzal.me"
    "FullHDFilmizlesene" = "https://www.fullhdfilmizlesene.now"
    "GinikoCanli" = "https://www.giniko.com"
    "HDFilmCehennemi" = "https://www.hdfilmcehennemi.nl"
    "HDFilmDelisi" = "https://hdfilmdelisi.one"
    "HDFilmizle" = "https://www.hdfilmizle.vip"
    "JetFilmizle" = "https://jetfilmizle.now"
    "KickTR" = "https://kick.com"
    "KultFilmler" = "https://kultfilmler.net"
    "MirrorVerse" = "https://mirrorverse.online"
    "OnePaceTr" = "https://onepacetr.net"
    "OpenAnime" = "https://openani.me"
    "RareFilmm" = "https://rarefilmm.com"
    "SeiCode" = "https://seiwatch.net"
    "SelcukFlix" = "https://selcukflix.com"
    "SetFilmIzle" = "https://www.setfilmizle.ltd"
    "SezonlukDizi" = "https://sezonlukdizi.cc"
    "SinemaCX" = "https://www.sinema.gg"
    "Sinezy" = "https://sinezy.to"
    "TrAnimeIzle" = "https://www.tranimeizle.io"
    "TurkAnime" = "https://turkanime.co"
    "Turkdizileri" = "https://turkdizileri.tv"
    "TvDiziler" = "https://tvdiziler.tv"
    "WebDramaTurkey" = "https://webdramaturkey2.com"
    "WebteIzle" = "https://webteizle.vip"
    "WFilmizle" = "https://www.wfilmizle.pw"
    "YabanciDizi" = "https://yabancidizi.news"
    "YeniKaynak" = "https://www.yenikaynak.com"
    "YesilCamTv" = "https://yesilcamtv.com.tr"
    "Youtube" = "https://www.youtube.com"
    "YTS" = "https://web.yts.gg"
}

$blockedPlugins = @{
    "InatBox" = "IPTV private backend / subscription bypass / reverse engineered token"
    "RecTV" = "IPTV private backend / encrypted session tokens"
    "SineWix" = "App-only encrypted API without public endpoints"
    "KraptorPlus" = "Meta-aggregator with mixed unknown upstream endpoints"
}

$matrix = @()
foreach ($p in $plugins) {
    $name = $p.name
    $status = $p.status
    $domain = $knownDomains[$name]

    $implStatus = "eligible"
    $notes = "Public domain identified, eligible for migration"

    if ($blockedPlugins.ContainsKey($name)) {
        $implStatus = "blocked"
        $notes = $blockedPlugins[$name]
    } elseif ($status -eq 0) {
        $implStatus = "deprecated"
        $notes = "Marked status=0 (Down) in legacy builds repository"
    } elseif ([string]::IsNullOrEmpty($domain)) {
        $implStatus = "unreviewed"
        $notes = "Pending domain and source investigation"
    }

    $entry = [ordered]@{
        name = $name
        internalName = $p.internalName
        legacyVersion = $p.version
        legacyStatus = $p.status
        language = $p.language
        tvTypes = $p.tvTypes
        legacyArtifactUrl = $p.url
        legacyHash = $p.fileHash
        currentDomain = $domain
        sourceCodeFound = $false
        sourceRepository = "Kraptor123/cs-kraptor"
        licenseVerified = $false
        implementationStatus = $implStatus
        healthStatus = "unknown"
        replacementModule = $null
        notes = $notes
    }
    $matrix += $entry
}

$outMatrixJson = Join-Path (Join-Path $repoRoot "legacy") "migration-matrix.json"
$matrix | ConvertTo-Json -Depth 10 | Set-Content $outMatrixJson -Encoding UTF8
Write-Host "Saved migration matrix to $outMatrixJson"

# Generate docs/LEGACY_INVENTORY.md
$outDoc = Join-Path (Join-Path $repoRoot "docs") "LEGACY_INVENTORY.md"
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("# Legacy Plugin Inventory & Migration Matrix")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("Bu dokuman, eski ``Kraptor123/cs-kraptor`` repository'sinden cikarilan **$($matrix.Count) adet** eklentinin dokumunu ve guncel durum analizini icerir.")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## Ozet Istatistikler")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- **Toplam Legacy Eklenti:** $($matrix.Count)")

$grouped = $matrix | Group-Object implementationStatus
foreach ($g in $grouped) {
    [void]$sb.AppendLine("- **$($g.Name):** $($g.Count)")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("---")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## Eklenti Listesi")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| # | Eklenti Adi | Internal Name | Legacy Surum | Legacy Status | Dil | Turler | Bilinen Domain | Migration Durumu | Notlar |")
[void]$sb.AppendLine("|---|-------------|---------------|--------------|---------------|-----|--------|----------------|------------------|--------|")

$idx = 1
foreach ($r in $matrix) {
    $tv = ($r.tvTypes -join ", ")
    $dom = if ($r.currentDomain) { $r.currentDomain } else { "-" }
    [void]$sb.AppendLine("| $idx | **$($r.name)** | ``$($r.internalName)`` | $($r.legacyVersion) | $($r.legacyStatus) | $($r.language) | $tv | $dom | ``$($r.implementationStatus)`` | $($r.notes) |")
    $idx++
}

$sb.ToString() | Set-Content $outDoc -Encoding UTF8
Write-Host "Generated inventory documentation: $outDoc"
