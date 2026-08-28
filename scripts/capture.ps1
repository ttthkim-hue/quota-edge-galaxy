# Capture mockup screenshots using Edge headless
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$html = Join-Path $root "mockups\index.html"
$assets = Join-Path $root "assets"
New-Item -ItemType Directory -Force -Path $assets | Out-Null

$fileUrl = "file:///" + ($html -replace '\\', '/')
$heroOut = Join-Path $assets "mockup-hero.png"
$singleOut = Join-Path $assets "mockup-single.png"

# Full hero (all 5 screens)
$heroScript = @"
const puppeteer = require('puppeteer');
(async () => {
  const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 900, deviceScaleFactor: 2 });
  await page.goto('${fileUrl}', { waitUntil: 'networkidle0' });
  await page.screenshot({ path: '${heroOut -replace '\\', '/'}', fullPage: false });
  await browser.close();
})();
"@

# Single phone for X card
$singleScript = @"
const puppeteer = require('puppeteer');
(async () => {
  const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 400, height: 800, deviceScaleFactor: 3 });
  await page.goto('${fileUrl}', { waitUntil: 'networkidle0' });
  const el = await page.$('.screens .screen-wrap:nth-child(2)');
  await el.screenshot({ path: '${singleOut -replace '\\', '/'}' });
  await browser.close();
})();
"@

Push-Location $root
try {
  npm init -y 2>$null | Out-Null
  npm install puppeteer --no-save 2>&1 | Out-Null
  $heroScript | node
  $singleScript | node
  Write-Host "Saved: $heroOut"
  Write-Host "Saved: $singleOut"
} finally {
  Pop-Location
}
