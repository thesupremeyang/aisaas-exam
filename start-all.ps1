$env:JAVA_HOME = "C:\JAVA\Java Tool\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$root = "C:\JAVA\apboa_saas"

$runners = @(
    @{name="runner-console"; port=3060},
    @{name="runner-runtime"; port=3061},
    @{name="runner-proxy"; port=3062},
    @{name="runner-file"; port=3063},
    @{name="runner-websocket"; port=3064}
)

foreach ($r in $runners) {
    $log = "$root\logs\$($r.name).log"
    New-Item -ItemType Directory -Force -Path "$root\logs" | Out-Null
    Start-Process powershell -WindowStyle Normal -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='$env:JAVA_HOME'; `$env:PATH='$env:PATH'; Set-Location '$root'; mvn spring-boot:run -pl $($r.name) *>> '$log'"
    Write-Host "Started $($r.name)..."
}

Start-Process powershell -WindowStyle Normal -ArgumentList "-NoExit", "-Command", "Set-Location '$root\ui'; pnpm dev"
Write-Host "Started frontend..."

Write-Host "`nAll services starting. Check logs in $root\logs\ for details."
