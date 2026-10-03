// Run against the isolated preview fixture; never use a production URL.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');

const base = 'http://127.0.0.1:18080';
const output = path.resolve(__dirname, '../target/ui-preview');
const findings = { pages: [], interactions: [], errors: [] };

(async () => {
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ headless: true,
    ...(process.env.PLAYWRIGHT_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 1366, height: 900 }, reducedMotion: 'reduce' });
    page.on('pageerror', error => findings.errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') findings.errors.push(message.text()); });
    async function visit(route) {
      const response = await page.goto(base + route, { waitUntil: 'networkidle' });
      assert.equal(response.status(), 200, `${route} must render`);
      assert.equal(await page.locator('main h1').count(), 1, `${route} has one main heading`);
    }
    await visit('/');
    await page.locator('#home-search').fill('Nguyễn');
    await page.getByRole('button', { name: 'Tìm sách' }).click();
    await page.waitForURL(/\/product\?q=/);
    assert.equal(new URL(page.url()).searchParams.get('q'), 'Nguyễn');
    findings.interactions.push('Home search submits the query');

    await visit('/product?sort=price_asc');
    const prices = async () => page.locator('.book-price').allTextContents().then(values =>
      values.map(text => Number(text.replace(/\D/g, ''))));
    let values = await prices();
    assert.ok(values.length > 1 && values.every((value, index) => index === 0 || values[index - 1] <= value));
    await page.getByRole('link', { name: 'Trang 2', exact: true }).click();
    await page.waitForURL(/page=2/);
    assert.equal(new URL(page.url()).searchParams.get('sort'), 'price_asc');
    await page.locator('#catalog-min-price').fill('30000');
    await page.locator('#catalog-max-price').fill('60000');
    await page.locator('#catalog-condition').selectOption('GOOD');
    await page.locator('#catalog-sort').selectOption('price_desc');
    await page.getByRole('button', { name: 'Xem kết quả' }).click();
    await page.waitForURL(/sort=price_desc/);
    values = await prices();
    assert.ok(values.length > 1 && values.every((value, index) => value >= 30000 && value <= 60000 && (index === 0 || values[index - 1] >= value)));
    await page.getByRole('link', { name: 'Bỏ lọc khoảng giá', exact: true }).click();
    await page.waitForLoadState('networkidle');
    assert.equal(new URL(page.url()).searchParams.get('condition'), 'GOOD');
    assert.equal(new URL(page.url()).searchParams.get('sort'), 'price_desc');
    assert.equal(new URL(page.url()).searchParams.has('maxPrice'), false);
    findings.interactions.push('Sorting, combined filters, pagination and individual filter removal');

    await visit('/guide');
    const faq = page.locator('.faq-list details').first();
    await faq.locator('summary').click();
    assert.equal(await faq.getAttribute('open'), '');
    findings.interactions.push('Reading guide FAQ expands');

    await visit('/auth/login');
    await page.locator('#login').fill('buyer');
    await page.locator('#password').fill('demo-password-123');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL(base + '/');
    await page.locator('.account-menu summary').click();
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('.account-menu').getAttribute('open'), null);
    assert.equal(await page.locator('.account-menu summary').evaluate(el => el === document.activeElement), true);
    findings.interactions.push('Account menu supports Escape and restores focus');

    await visit('/account/reviews/23');
    const original = 'Cuốn sách được đóng gói cẩn thận, trang giấy còn đẹp và nội dung rất thú vị. Mình muốn giới thiệu cho những bạn yêu thích đọc sách mỗi ngày.';
    await page.locator('input[name=rating][value="4"]').check();
    await page.locator('#review-body').fill(original);
    await page.getByRole('button', { name: 'Đăng đánh giá', exact: true }).click();
    await page.waitForURL(/\/product\/detail\?id=23#reviews/);
    assert.equal(await page.locator('.reader-review .description').filter({ hasText: original }).count(), 1);
    await page.getByRole('link', { name: 'Xem đánh giá của tôi', exact: true }).click();
    const updated = original + ' Sau khi đọc lại, mình càng yêu thích cách kể chuyện nhẹ nhàng của tác giả.';
    await page.locator('input[name=rating][value="5"]').check();
    await page.locator('#review-body').fill(updated);
    await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
    await page.waitForURL(/\/product\/detail\?id=23#reviews/);
    assert.equal(await page.locator('.reader-review .description').filter({ hasText: updated }).count(), 1);
    findings.interactions.push('Verified buyer creates and updates a review; product summary updates');

    const routes = ['/', '/product', '/product?q=khongtimthay', '/product?sort=favorites', '/product/detail?id=24', '/guide',
      '/favorites', '/recently-viewed', '/account/profile', '/account/addresses', '/cart', '/checkout', '/orders', '/orders/1', '/account/reviews/23'];
    for (const width of [320, 390, 768, 1366]) {
      await page.setViewportSize({ width, height: 900 });
      for (const route of routes) {
        await visit(route);
        const layout = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
          mainWidth: document.querySelector('main').scrollWidth,
          overflowing: [...document.querySelectorAll('main *')].filter(el => {
            const r = el.getBoundingClientRect();
            return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1) && getComputedStyle(el).position !== 'absolute';
          }).slice(0, 8).map(el => `${el.tagName}.${el.className}`) }));
        findings.pages.push({ route, width, ...layout });
        assert.ok(layout.scroll <= width + 1, `Horizontal overflow at ${width}: ${route}: ${JSON.stringify(layout)}`);
        if ((width === 390 || width === 1366) && ['/', '/product', '/product/detail?id=24', '/guide', '/account/reviews/23'].includes(route)) {
          const name = route === '/' ? 'home' : route.split('?')[0].replaceAll('/', '-').slice(1);
          await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: true });
        }
      }
    }
    assert.deepEqual(findings.errors, [], 'No browser JS/console errors');
    console.log(JSON.stringify({ pages: findings.pages.length, interactions: findings.interactions, errors: findings.errors }, null, 2));
  } finally {
    await fs.writeFile(path.join(output, 'results.json'), JSON.stringify(findings, null, 2));
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
