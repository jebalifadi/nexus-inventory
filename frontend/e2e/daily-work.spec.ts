import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const OWNER = { username: 'owner', password: process.env.NEXUS_OWNER_PASSWORD ?? '' }
const today = () => new Date().toLocaleDateString('sv')

test.describe.configure({ mode: 'serial' })

// Products, suppliers and locations are created through the API: these tests are about daily work.
let token = ''
let supplierId = 0
let locationId = 0
let soldProductId = 0
let lostProductId = 0

async function post<T>(request: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await request.post(path, { data, headers: { Authorization: `Bearer ${token}` } })
  expect(response.ok(), `POST ${path}: ${await response.text()}`).toBeTruthy()
  return (await response.json()) as T
}

type Created = { id: number }

async function signIn(page: Page) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(OWNER.username)
  await page.getByLabel('Password').fill(OWNER.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

const card = (page: Page, title: string) =>
  page.locator('.summary-grid .card.section').filter({ has: page.locator('.eyebrow', { hasText: new RegExp(`^${title}$`) }) })
const bigNumber = (page: Page, title: string) => card(page, title).locator('.big-number')

test.beforeAll(async ({ request }) => {
  const login = await request.post('/api/v1/auth/login', { data: OWNER })
  expect(login.ok()).toBeTruthy()
  token = ((await login.json()) as { token: string }).token
  const category = await post<Created>(request, '/api/v1/categories', { name: 'Cables', trackingMode: 'LOT', fields: [] })
  soldProductId = (await post<Created>(request, '/api/v1/products', { categoryId: category.id, sku: 'SOLD-1', attributes: {} })).id
  lostProductId = (await post<Created>(request, '/api/v1/products', { categoryId: category.id, sku: 'LOST-1', attributes: {} })).id
  locationId = (await post<Created>(request, '/api/v1/locations', { name: 'Tunis shop', country: 'Tunisia' })).id
  supplierId = (await post<Created>(request, '/api/v1/suppliers', { name: 'Cable supplier', country: 'Germany' })).id
})

test('a wrong password is refused, the right one opens the dashboard', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username').fill(OWNER.username)
  await page.getByLabel('Password').fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page).toHaveURL(/\/login/)

  await signIn(page)
})

test('a purchase puts the goods in stock', async ({ page }) => {
  await signIn(page)
  await page.goto('/purchases/new')
  await page.getByRole('combobox', { name: /^Supplier/ }).selectOption(String(supplierId))
  await page.getByRole('combobox', { name: /^Goods go to/ }).selectOption(String(locationId))
  await page.getByRole('combobox', { name: /^Product/ }).selectOption(String(soldProductId))
  await page.getByLabel('Quantity').fill('10')
  await page.getByLabel('Price per piece').fill('4')
  await page.getByLabel('Not paid yet').check()
  await page.getByRole('button', { name: 'Save purchase' }).click()

  await expect(page).toHaveURL(/\/purchases$/)
  await expect(page.getByText('Cable supplier').first()).toBeVisible()
  await page.goto(`/products/${soldProductId}`)
  await expect(page.getByText('10', { exact: true }).first()).toBeVisible()
})

test('a sale takes goods out of stock, and a payment settles it', async ({ page }) => {
  await signIn(page)
  await page.goto('/sales/new')
  await page.locator('label:has(select)').first().locator('select').selectOption(String(locationId))
  await page.getByRole('combobox', { name: /^Product/ }).selectOption(String(soldProductId))
  await page.getByLabel('Quantity').fill('2')
  await page.getByLabel('Price per piece').fill('10')
  await page.getByLabel('Not paid yet').check()
  await page.getByRole('button', { name: 'Save sale' }).click()
  await expect(page).toHaveURL(/\/sales$/)

  await page.getByRole('button', { name: 'Record payment' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel(/Amount/)).toHaveValue(/^20/)
  await dialog.getByRole('button', { name: 'Save payment' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: 'Record payment' })).toBeHidden()
})

test('lost goods are written off as a loss', async ({ page, request }) => {
  await post(request, '/api/v1/purchases', {
    supplierId,
    purchaseDate: today(),
    currency: 'EUR',
    lines: [{ productId: lostProductId, locationId, quantity: 5, unitPrice: '3' }],
  })
  await signIn(page)
  await page.goto(`/products/${lostProductId}`)
  await page.getByRole('button', { name: 'Remove', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Lost' }).click()
  await expect(dialog.getByRole('checkbox')).toBeChecked()
  await dialog.getByRole('button', { name: 'Remove batch' }).click()
  await expect(page.getByText(/counted as a loss/)).toBeVisible()
})

test('the dashboard adds it all up', async ({ page }) => {
  await signIn(page)
  await expect(bigNumber(page, 'Money invested')).toContainText('55.00') // 10 × 4 + 5 × 3
  await expect(bigNumber(page, 'Revenue')).toContainText('20.00')
  await expect(bigNumber(page, 'Costs')).toContainText('15.00') // the lost batch
  await expect(bigNumber(page, 'Profit')).toContainText('3.00') // 20 − 2 × 4 − 15 = −3
  await expect(bigNumber(page, 'Profit').locator('.loss')).toBeVisible()
})

test('comparing periods shows a change on all four cards', async ({ page }) => {
  await signIn(page)
  await page.locator('.head-tools button').first().click()
  await page.getByRole('checkbox', { name: 'Compare with the previous period' }).check()
  await page.getByRole('button', { name: /^Apply/ }).click()
  await expect(page.locator('.summary-grid .delta')).toHaveCount(4)
})

test('the dashboard follows the Settings currency and asks for a missing rate', async ({ page, request }) => {
  await signIn(page)
  await page.goto('/settings')
  await expect(page.getByText(/^Version \d+\.\d+\.\d+$/)).toBeVisible()
  await page.getByLabel('Default currency').selectOption('TND')
  await page.goto('/dashboard')
  await expect(page.getByText('Some amounts cannot be converted to TND')).toBeVisible()
  await expect(page.getByRole('link', { name: /EUR → TND/ })).toBeVisible()

  await post(request, '/api/v1/exchange-rates', { base: 'EUR', quote: 'TND', rate: '3', rateDate: today() })
  await page.reload()

  await expect(page.getByText('Some amounts cannot be converted to TND')).toBeHidden()
  await expect(bigNumber(page, 'Money invested')).toContainText('165.000')
  await expect(bigNumber(page, 'Revenue')).toContainText('60.000')
  await expect(bigNumber(page, 'Profit')).toContainText('9.000')
})
