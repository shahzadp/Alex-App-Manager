# Uploads each app's `ic_launcher_icon.png` to the latest release of its repo.
# Safe to re-run: --clobber replaces an existing asset of the same name.
# Requires gh CLI signed in as the repo owner.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repos = @(
    'Recorder-by-Alex',
    'PhotoEditorbyAlex',
    '4GamesbyAlex',
    'Project-Alice-Android-by-Alex',
    'DoL-by-Alex',
    'alex-store-app'
)
$name = 'ic_launcher_icon.png'
foreach ($repo in $repos) {
    $tag = (gh release view --repo shahzadp/$repo --json tagName --jq '.tagName')
    $file = Join-Path $root (Join-Path $repo $name)
    if (-not (Test-Path $file)) { Write-Warning "Missing $file"; continue }
    Write-Output "Uploading $file to shahzadp/$repo@$tag ..."
    gh release upload $tag $file --repo shahzadp/$repo --clobber
}
Write-Output 'Done.'
