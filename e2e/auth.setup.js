/**
 * Auth setup: log in once and save storageState for dependent projects.
 * @see https://playwright.dev/docs/auth
 */
const playwright = require('@playwright/test');
const path = require('path');
const { loginAsAdmin } = require('./_helpers');

const setup = playwright.test;
const authFile = path.join(__dirname, '.auth', 'user.json');

setup('authenticate', async ({ page }) => {
  await loginAsAdmin(page);
  await page.context().storageState({ path: authFile });
});
