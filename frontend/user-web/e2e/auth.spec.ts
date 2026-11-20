import { expect, test } from '@playwright/test'

test.describe('auth page', () => {
  test('desktop shows brand narrative and login card', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByText('把收藏夹，')).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录拾页' })).toBeVisible()
    await expect(page.locator('.auth-brand')).toBeVisible()
    await expect(page.locator('.brand-preview')).toBeVisible()
    await expect(page.locator('.auth-card')).toBeVisible()
  })

  test('switches between login and register', async ({ page }) => {
    await page.goto('/login')

    await page.getByRole('button', { name: '注册', exact: true }).click()
    await expect(page.getByRole('heading', { name: '创建你的知识库' })).toBeVisible()
    await expect(page.getByPlaceholder('怎么称呼你（可留空）')).toBeVisible()
    await expect(page.getByPlaceholder('再输入一次')).toBeVisible()
    await expect(page.getByPlaceholder('如需邀请码请填写')).toBeVisible()

    await page.getByRole('button', { name: '登录', exact: true }).first().click()
    await expect(page.getByRole('heading', { name: '登录拾页' })).toBeVisible()
    await expect(page.getByPlaceholder('再输入一次')).toHaveCount(0)
  })

  test('shows validation for invalid email', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('you@example.com').fill('not-an-email')
    await page.getByPlaceholder('至少 8 位').fill('Password123')
    await page.locator('.submit').click()

    await expect(page.getByText('请输入有效的邮箱地址')).toBeVisible()
  })

  test('mobile keeps a usable single-column form', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login')

    await expect(page.locator('.mobile-brand')).toBeVisible()
    await expect(page.locator('.auth-card')).toBeVisible()
    await expect(page.getByRole('heading', { name: '登录拾页' })).toBeVisible()
  })
})
