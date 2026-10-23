# Sobe o NexoDesk inteiro (servico Python + API Java) com um comando.
#
#   .\iniciar-nexodesk.ps1                    banco H2 (nada para instalar)
#   .\iniciar-nexodesk.ps1 -Perfil mysql      MySQL (veja o README: variaveis NEXODESK_DB_*)
#   .\iniciar-nexodesk.ps1 -Perfil demo       com dados de demonstracao (exige NEXODESK_DEMO_SENHA)
#   .\iniciar-nexodesk.ps1 -Perfil mysql,demo
#   .\iniciar-nexodesk.ps1 -Reconstruir       refaz o .jar mesmo que ja exista
#
# Ctrl+C encerra a API e tambem o servico Python.
# Texto somente ASCII de proposito: o PowerShell 5.1 le .ps1 sem BOM como ANSI.
param(
    [string]$Perfil = "",
    [switch]$Reconstruir
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
$pastaApi = Join-Path $raiz "api-java"
$pastaPy = Join-Path $raiz "servico-prioridade"

function Achar-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return Join-Path $env:JAVA_HOME "bin\java.exe"
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { return $cmd.Source }
    $pastas = @("C:\Program Files\Microsoft", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java")
    foreach ($p in $pastas) {
        $jdk = Get-ChildItem $p -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk -and (Test-Path (Join-Path $jdk.FullName "bin\java.exe"))) {
            return Join-Path $jdk.FullName "bin\java.exe"
        }
    }
    throw "Java 21 nao encontrado. Instale com: winget install Microsoft.OpenJDK.21"
}

function Testar-Python($exe) {
    # O 'python' da Microsoft Store e so um atalho: precisa responder de verdade.
    try {
        $saida = & $exe --version 2>$null
        return ($LASTEXITCODE -eq 0 -and "$saida" -match "^Python 3\.")
    } catch { return $false }
}

function Achar-Python {
    $candidatos = @()
    $cmd = Get-Command python -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { $candidatos += $cmd.Source }
    $candidatos += Get-ChildItem (Join-Path $env:LOCALAPPDATA "Programs\Python") -Directory -Filter "Python3*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName "python.exe" }
    foreach ($c in $candidatos) { if ((Test-Path $c) -and (Testar-Python $c)) { return $c } }
    throw "Python 3 nao encontrado. Instale com: winget install Python.Python.3.12"
}

function Porta-Ocupada($porta) {
    return [bool](Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue)
}

foreach ($porta in 8080, 8001) {
    if (Porta-Ocupada $porta) { throw "A porta $porta ja esta em uso. Feche o programa que a usa e tente de novo." }
}

$java = Achar-Java
$env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
Write-Host "Java:   $java"

# --- servico Python ---
$venvPython = Join-Path $pastaPy ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    $python = Achar-Python
    Write-Host "Python: $python (criando ambiente virtual, so na primeira vez)"
    & $python -m venv (Join-Path $pastaPy ".venv")
    & $venvPython -m pip install --quiet -r (Join-Path $pastaPy "requirements.txt")
    if ($LASTEXITCODE -ne 0) { throw "Falha ao instalar as dependencias do Python." }
} else {
    Write-Host "Python: $venvPython"
}

# --- jar da API ---
$jar = Join-Path $pastaApi "target\api-0.0.1-SNAPSHOT.jar"
# Reconstroi se o jar nao existe, se pediram, ou se algum arquivo da API (codigo, telas, pom) for mais novo que ele.
$desatualizado = $false
if (Test-Path $jar) {
    $dataJar = (Get-Item $jar).LastWriteTime
    $maisNovo = @(Get-ChildItem (Join-Path $pastaApi "src") -Recurse -File) + (Get-Item (Join-Path $pastaApi "pom.xml")) |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $desatualizado = ($maisNovo.LastWriteTime -gt $dataJar)
}
if ($Reconstruir -or $desatualizado -or -not (Test-Path $jar)) {
    Write-Host "Gerando o .jar da API (leva ~1 minuto)..."
    Push-Location $pastaApi
    try {
        & (Join-Path $pastaApi "mvnw.cmd") -B -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Falha ao gerar o .jar." }
    } finally { Pop-Location }
}

$processoPython = $null
try {
    Write-Host "Iniciando o servico de prioridade na porta 8001..."
    # A saida vai para arquivos: sem isso o processo oculto pode morrer em hosts sem console.
    $logSaida = Join-Path $pastaPy "uvicorn.out.log"
    $logErro = Join-Path $pastaPy "uvicorn.err.log"
    $processoPython = Start-Process -FilePath $venvPython `
        -ArgumentList "-m", "uvicorn", "app.main:app", "--port", "8001", "--log-level", "warning" `
        -WorkingDirectory $pastaPy -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $logSaida -RedirectStandardError $logErro

    $pronto = $false
    for ($i = 0; $i -lt 30 -and -not $pronto; $i++) {
        Start-Sleep -Seconds 1
        if ($processoPython.HasExited) { break }
        # Conexao TCP direta em vez de Invoke-WebRequest: o IWR pode travar por causa de proxy/WPAD.
        $tcp = New-Object System.Net.Sockets.TcpClient
        try { $pronto = ($tcp.ConnectAsync("127.0.0.1", 8001).Wait(1000) -and $tcp.Connected) }
        catch { $pronto = $false }
        finally { $tcp.Close() }
    }
    if (-not $pronto) {
        if (Test-Path $logErro) { Get-Content $logErro -Tail 15 | Write-Host }
        throw "O servico Python nao respondeu (veja servico-prioridade\uvicorn.err.log)."
    }

    $argumentos = @("-jar", $jar)
    if ($Perfil) { $argumentos += "--spring.profiles.active=$Perfil" }

    Write-Host ""
    Write-Host "NexoDesk em http://localhost:8080  (Ctrl+C para encerrar)"
    Write-Host "Na 1a execucao o console mostra a senha inicial do sindico, UMA vez: anote."
    Write-Host ""
    if ($Perfil -match "demo") {
        Write-Host "Contas de demonstracao (todas @nexodesk.local; a senha e a de NEXODESK_DEMO_SENHA, o sindico usa NEXODESK_SINDICO_SENHA):"
        Write-Host "  sindico, ana.demo, bruno.demo (moradores), ze.demo (zelador), marta.demo (faxineira), eletricista.demo,"
        Write-Host "  encanador.demo, porteiro.demo, seguranca.demo, elevador.demo (tecnico externo), manutencao.demo"
        Write-Host "  A tela de login mostra botoes 'Entrar como...' que preenchem o e-mail."
        Write-Host ""
    }
    Push-Location $pastaApi
    try { & $java @argumentos } finally { Pop-Location }
}
finally {
    if ($processoPython -and -not $processoPython.HasExited) {
        Stop-Process -Id $processoPython.Id -Force
        Write-Host "Servico Python encerrado."
    }
}
