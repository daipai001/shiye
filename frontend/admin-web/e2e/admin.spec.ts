import { expect, test } from '@playwright/test'

test.describe('admin business flow', () => {
  test('login, team audit and audit log pages', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('admin@shiye.local').fill(
      process.env.ADMIN_EMAIL || 'admin@shiye.local',
    )
    await page.getByPlaceholder('管理员密码').fill(
      process.env.ADMIN_PASSWORD || 'Shiye@2026!',
    )
    await page.getByRole('button', { name: '进入后台' }).click()

    await expect(page).toHaveURL(/\/overview/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '总览' })).toBeVisible()

    await page.getByRole('link', { name: '团队审计' }).click()
    await expect(page.getByRole('heading', { name: '团队审计' })).toBeVisible()
    await expect(page.getByText('团队邀请、加入、移除')).toBeVisible()

    await page.getByRole('link', { name: '审计日志' }).click()
    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()

    await page.getByRole('link', { name: '团队与配额' }).click()
    await expect(page.getByRole('heading', { name: '团队与配额' })).toBeVisible()
  })
})
