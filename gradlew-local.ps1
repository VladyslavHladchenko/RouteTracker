param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @(":wear:assembleDebug", "--console=plain")
)

$repoRoot = $PSScriptRoot
$gradleWrapper = Join-Path $repoRoot "gradlew"
$androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
$projectGradleHome = Join-Path $repoRoot ".gradle-local"

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

if (-not (Test-Path $androidStudioJbr)) {
    throw "Android Studio JBR not found at $androidStudioJbr"
}

$env:JAVA_HOME = $androidStudioJbr
$env:GRADLE_USER_HOME = $projectGradleHome

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
Write-Host "Using GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "Running gradlew $($GradleArgs -join ' ')"

& $gradleWrapper @GradleArgs
exit $LASTEXITCODE
