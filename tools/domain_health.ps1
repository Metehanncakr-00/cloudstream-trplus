# domain_health.ps1
# PowerShell domain health and redirect checker

param(
    [string]$ReportPath = "reports\domain-health.json"
)

$ErrorActionPreference = "Continue"
$repoRoot = (Get-Item -Path $PSScriptRoot).Parent.FullName
$domainsFile = Join-Path $repoRoot "config\domains.json"

if (-not (Test-Path $domainsFile)) {
    Write-Error "config\domains.json not found!"
    exit 1
}

$config = Get-Content $domainsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$providers = $config.providers

$results = @()
Write-Host "[Domain Health] Checking $($providers.psobject.properties.Name.Count) providers..."

foreach ($prop in $providers.psobject.properties) {
    $name = $prop.Name
    $info = $prop.Value
    $canonical = $info.canonical
    $allowedHosts = $info.allowedHosts
    $expectedMarkers = $info.expectedMarkers

    $item = [ordered]@{
        provider = $name
        canonical = $canonical
        finalUrl = $null
        httpsValid = $false
        hostAllowed = $false
        markerFound = $false
        status = "unknown"
        error = $null
    }

    if ([string]::IsNullOrEmpty($canonical)) {
        $item.status = "no_domain"
        $results += $item
        continue
    }

    try {
        $handler = [System.Net.Http.HttpClientHandler]::new()
        $handler.AllowAutoRedirect = $true
        $client = [System.Net.Http.HttpClient]::new($handler)
        $client.Timeout = [TimeSpan]::FromSeconds(10)
        $client.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

        $response = $client.GetAsync($canonical).GetAwaiter().GetResult()
        $finalUri = $response.RequestMessage.RequestUri
        $item.finalUrl = $finalUri.AbsoluteUri
        $item.httpsValid = ($finalUri.Scheme -eq "https")

        $host = $finalUri.Host.ToLower()
        $item.hostAllowed = ($allowedHosts -contains $host)

        if ($response.IsSuccessStatusCode) {
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $markerFound = $false
            if ($expectedMarkers) {
                foreach ($m in $expectedMarkers) {
                    if ($content -match [regex]::Escape($m)) {
                        $markerFound = $true
                        break
                    }
                }
            } else {
                $markerFound = $true
            }
            $item.markerFound = $markerFound

            if ($item.httpsValid -and $item.hostAllowed -and $item.markerFound) {
                $item.status = "healthy"
            } elseif (-not $item.hostAllowed) {
                $item.status = "untrusted_redirect"
            } elseif (-not $item.markerFound) {
                $item.status = "marker_missing"
            } else {
                $item.status = "degraded"
            }
        } else {
            $item.status = "http_$($response.StatusCode)"
        }
        $client.Dispose()
    } catch {
        $item.status = "network_error"
        $item.error = $_.Exception.Message
    }

    $results += $item
    Write-Host " - $name : $($item.status) ($($item.finalUrl))"
}

$fullReportPath = Join-Path $repoRoot $ReportPath
$reportDir = [System.IO.Path]::GetDirectoryName($fullReportPath)
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

$results | ConvertTo-Json -Depth 5 | Set-Content $fullReportPath -Encoding UTF8
Write-Host "`n[Domain Health] Saved report to $fullReportPath"
