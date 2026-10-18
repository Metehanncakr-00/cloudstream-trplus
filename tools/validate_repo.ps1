# validate_repo.ps1
# PowerShell static validator for local Windows environments

param(
    [switch]$VerifyArtifacts
)

$ErrorActionPreference = "Stop"
$repoRoot = (Get-Item -Path $PSScriptRoot).Parent.FullName
$errors = @()

# 1. Check repo.json
$repoJsonPath = Join-Path $repoRoot "repo.json"
if (-not (Test-Path $repoJsonPath)) {
    $errors += "Missing repo.json in root!"
} else {
    try {
        $repoData = Get-Content $repoJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([string]::IsNullOrWhiteSpace($repoData.name)) {
            $errors += "repo.json must contain a non-empty 'name'"
        }
        if ($repoData.manifestVersion -ne 1) {
            $errors += "repo.json manifestVersion must be 1"
        }
        if (-not $repoData.pluginLists -or $repoData.pluginLists.Count -eq 0) {
            $errors += "repo.json must contain a non-empty 'pluginLists' array"
        }
    } catch {
        $errors += "Invalid JSON in repo.json: $_"
    }
}

# 2. Discover modules
$ignored = @("tools", "docs", "config", "legacy", ".github", "gradle", "reports", "site", ".gradle", "build")
$modules = @()
Get-ChildItem -Path $repoRoot -Directory | ForEach-Object {
    if ($_.Name -notin $ignored -and -not $_.Name.StartsWith(".")) {
        $bg = Join-Path $_.FullName "build.gradle.kts"
        if (Test-Path $bg) {
            $modules += $_.Name
        }
    }
}

Write-Host "[Validator] Discovered $($modules.Count) provider modules: $($modules -join ', ')"

foreach ($mod in $modules) {
    $modDir = Join-Path $repoRoot $mod
    $bgFile = Join-Path $modDir "build.gradle.kts"
    $bgContent = Get-Content $bgFile -Raw -Encoding UTF8

    if ($bgContent -match 'version\s*=\s*(\d+)') {
        $ver = [int]$matches[1]
        if ($ver -le 0) {
            $errors += "[$mod] Version must be > 0 (found $ver)"
        }
    } else {
        $errors += "[$mod] Missing version in build.gradle.kts"
    }

    if ($bgContent -notmatch 'authors') {
        $errors += "[$mod] Missing authors in build.gradle.kts"
    }
    if ($bgContent -notmatch 'description') {
        $errors += "[$mod] Missing description in build.gradle.kts"
    }
    if ($bgContent -notmatch 'language') {
        $errors += "[$mod] Missing language in build.gradle.kts"
    }

    $srcDir = Join-Path $modDir "src\main\kotlin"
    if (-not (Test-Path $srcDir)) {
        $errors += "[$mod] Missing src/main/kotlin directory"
        continue
    }

    $hasPlugin = $false
    $hasMainApi = $false
    Get-ChildItem -Path $srcDir -Filter "*.kt" -Recurse | ForEach-Object {
        $kt = Get-Content $_.FullName -Raw -Encoding UTF8
        if ($kt -match '@CloudstreamPlugin') { $hasPlugin = $true }
        if ($kt -match ':\s*MainAPI\b') { $hasMainApi = $true }
    }

    if (-not $hasPlugin) { $errors += "[$mod] Missing @CloudstreamPlugin class" }
    if (-not $hasMainApi) { $errors += "[$mod] Missing MainAPI class" }
}

if ($VerifyArtifacts) {
    $pluginsJson = Join-Path $repoRoot "build\plugins.json"
    if (-not (Test-Path $pluginsJson)) {
        $errors += "Artifact verification failed: $pluginsJson does not exist!"
    } else {
        $plugins = Get-Content $pluginsJson -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($p in $plugins) {
            $pName = $p.name
            $pHash = $p.fileHash
            if (-not $pHash) {
                $errors += "Plugin $pName missing fileHash in plugins.json"
            }
            $cs3 = Get-ChildItem -Path $repoRoot -Filter "$pName.cs3" -Recurse | Select-Object -First 1
            if (-not $cs3) {
                $errors += "Missing artifact file $pName.cs3"
            }
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "`n=== Validation Failures ===" -ForegroundColor Red
    foreach ($e in $errors) {
        Write-Host " - [ERROR] $e" -ForegroundColor Red
    }
    exit 1
}

Write-Host "`n[SUCCESS] Repository validation passed with 0 errors!" -ForegroundColor Green
