#!/usr/bin/env node
/**
 * Lighthouse against hash-routed SPA URLs while keeping JWT in localStorage.
 *
 * Auth (first match wins):
 * 1. PERF_AUTH_TOKEN — inject directly (no credentials; best for CI secrets).
 * 2. PERF_USE_API_LOGIN=1 — POST /api/auth/login with PERF_LOGIN_USER / PERF_LOGIN_PASSWORD
 *    (in GitHub Actions use repository secrets; no defaults in CI).
 * 3. Otherwise — UI login on /#/login (local dev; admin/admin for seeded DB).
 *
 * Usage (from repo root):
 *   CHROME_PATH=... PERF_BASE_URL=http://localhost:3000 \
 *     node scripts/performance/lighthouse-authenticated.cjs /#/dashboard /#/stats
 */

const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const chromeLauncher = require('chrome-launcher');
const puppeteer = require('puppeteer-core');
const lighthouse = require('lighthouse').default;
const { generateReport } = require('lighthouse');

const BASE = process.env.PERF_BASE_URL || 'http://localhost:3000';
const TOKEN_KEY = 'galaticos.auth.token';
const IS_CI = process.env.CI === 'true' || process.env.GITHUB_ACTIONS === 'true';

const DEFAULT_CHROME = path.join(
  __dirname,
  '..',
  '..',
  'chrome',
  'linux-147.0.7727.24',
  'chrome-linux64',
  'chrome'
);
const CHROME_PATH = process.env.CHROME_PATH || (fs.existsSync(DEFAULT_CHROME) ? DEFAULT_CHROME : undefined);

const urls = process.argv.slice(2).length
  ? process.argv.slice(2).map((u) => (u.startsWith('http') ? u : `${BASE}${u.startsWith('/') ? '' : '/'}${u}`))
  : [`${BASE}/#/dashboard`, `${BASE}/#/stats`];

function summarize(lhr) {
  const perf = lhr.categories.performance;
  const a = lhr.audits;
  return {
    score: Math.round((perf && perf.score) * 100),
    LCP: a['largest-contentful-paint'] && a['largest-contentful-paint'].displayValue,
    TBT: a['total-blocking-time'] && a['total-blocking-time'].displayValue,
    CLS: a['cumulative-layout-shift'] && a['cumulative-layout-shift'].displayValue,
  };
}

function postJson(urlStr, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const lib = u.protocol === 'https:' ? https : http;
    const data = JSON.stringify(body);
    const req = lib.request(
      {
        hostname: u.hostname,
        port: u.port || (u.protocol === 'https:' ? 443 : 80),
        path: u.pathname,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
        },
      },
      (res) => {
        let chunks = '';
        res.on('data', (c) => (chunks += c));
        res.on('end', () => {
          try {
            const parsed = JSON.parse(chunks);
            resolve({ status: res.statusCode, body: parsed });
          } catch (e) {
            reject(new Error(`Invalid JSON from login: ${chunks.slice(0, 200)}`));
          }
        });
      }
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function tokenFromApiLogin() {
  const user = process.env.PERF_LOGIN_USER;
  const pass = process.env.PERF_LOGIN_PASSWORD;
  if (!user || !pass) {
    throw new Error(
      'PERF_USE_API_LOGIN=1 requires PERF_LOGIN_USER and PERF_LOGIN_PASSWORD (use GitHub secrets in CI).'
    );
  }
  const loginUrl = `${BASE.replace(/\/$/, '')}/api/auth/login`;
  const { status, body } = await postJson(loginUrl, { username: user, password: pass });
  if (status !== 200 || !body.success) {
    throw new Error(`Login failed: HTTP ${status} ${JSON.stringify(body)}`);
  }
  const token = body.data && body.data.token;
  if (!token) throw new Error('Login response missing data.token');
  return token;
}

async function injectTokenAndWarmShell(page, token) {
  const origin = new URL(BASE).origin;
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await page.evaluate(
    (key, tok) => {
      window.localStorage.setItem(key, tok);
    },
    TOKEN_KEY,
    token
  );
  await page.goto(`${BASE}/#/dashboard`, { waitUntil: 'networkidle2', timeout: 90_000 });
  await page.waitForSelector('header', { timeout: 30_000 });
}

async function ensureLoggedIn(browser) {
  const pages = await browser.pages();
  const page = pages[0] || (await browser.newPage());
  const envToken = process.env.PERF_AUTH_TOKEN && process.env.PERF_AUTH_TOKEN.trim();
  if (envToken) {
    await injectTokenAndWarmShell(page, envToken);
    return;
  }
  if (process.env.PERF_USE_API_LOGIN === '1') {
    const token = await tokenFromApiLogin();
    await injectTokenAndWarmShell(page, token);
    return;
  }
  if (IS_CI) {
    console.warn(
      '[perf] CI detected: prefer PERF_AUTH_TOKEN or PERF_USE_API_LOGIN=1 with secrets (UI login may be brittle).'
    );
  }
  await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle2', timeout: 60_000 });
  const user = process.env.PERF_LOGIN_USER || 'admin';
  const pass = process.env.PERF_LOGIN_PASSWORD || 'admin';
  await page.waitForSelector('input[placeholder="Digite seu usuário"]', { timeout: 30_000 });
  await page.click('input[placeholder="Digite seu usuário"]', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await page.type('input[placeholder="Digite seu usuário"]', user, { delay: 15 });
  await page.click('input[placeholder="Digite sua senha"]', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await page.type('input[placeholder="Digite sua senha"]', pass, { delay: 15 });
  await page.click('button[type="submit"]');
  await page.waitForFunction(() => !!window.localStorage.getItem('galaticos.auth.token'), { timeout: 45_000 });
  await page.waitForSelector('header', { timeout: 15_000 });
}

async function main() {
  if (!CHROME_PATH) {
    console.error('Set CHROME_PATH to a Chrome/Chromium binary (see docs/informacao/performance/metodologia.md).');
    process.exit(1);
  }

  const chrome = await chromeLauncher.launch({
    chromePath: CHROME_PATH,
    chromeFlags: ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
  });

  try {
    const browser = await puppeteer.connect({
      browserURL: `http://127.0.0.1:${chrome.port}`,
      defaultViewport: null,
    });
    await ensureLoggedIn(browser);
    await browser.disconnect();

    const outDir = path.join(__dirname, '..', '..', 'docs', 'perf-output');
    await fs.promises.mkdir(outDir, { recursive: true });

    const minScore = process.env.PERF_MIN_SCORE ? Number(process.env.PERF_MIN_SCORE) : null;

    for (const url of urls) {
      const slug = url.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'page';
      const flags = {
        logLevel: 'error',
        onlyCategories: ['performance'],
        port: chrome.port,
        disableStorageReset: true,
      };
      const runnerResult = await lighthouse(url, flags);
      const lhr = runnerResult.lhr;
      const file = path.join(outDir, `lighthouse-auth-${slug}.json`);
      await fs.promises.writeFile(file, generateReport(lhr, 'json'));
      const s = summarize(lhr);
      console.log(url, s);
      if (minScore != null && !Number.isNaN(minScore) && s.score < minScore) {
        console.error(`Performance score ${s.score} < ${minScore} for ${url}`);
        process.exitCode = 1;
      }
    }
  } finally {
    await chrome.kill();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
