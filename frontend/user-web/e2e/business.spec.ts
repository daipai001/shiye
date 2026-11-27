import { expect, test } from '@playwright/test'

test.describe('business flow', () => {
  test('register, upload, chat with citation and view usage', async ({ page }) => {
    const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 10000)}@shiye.local`
    const password = 'E2ePassword123'

    await page.goto('/login')
    await page.getByRole('button', { name: '注册', exact: true }).click()
    await page.getByPlaceholder('怎么称呼你（可留空）').fill('E2E User')
    await page.getByPlaceholder('you@example.com').fill(email)
    await page.getByPlaceholder('至少 8 位').fill(password)
    await page.getByPlaceholder('再输入一次').fill(password)
    await page.locator('button.submit').click()

    await expect(page).toHaveURL(/\/library/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '资料库' })).toBeVisible()

    const content = [
      'E2E retrieval validation note.',
      'RAG chunk overlap and citation validation.',
      'The quick brown fox jumps over the lazy dog.',
      '团队知识库切片与引用端到端验证。',
    ].join(' ').repeat(20)
    const fileInput = page.locator('input[type=file]').first()
    await fileInput.setInputFiles({
      name: 'e2e-note.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(content, 'utf8'),
    })

    await expect(page.getByText('e2e-note').first()).toBeVisible({ timeout: 60_000 })

    await page.goto('/chat')
    const composer = page.locator('textarea.composer-input')
    await composer.fill('RAG chunk overlap citation validation')
    await composer.press('Enter')
    await expect(page.locator('.citation-card').first()).toBeVisible({ timeout: 60_000 })

    await page.goto('/usage')
    await expect(page.getByRole('heading', { name: '用量', level: 1 })).toBeVisible()
    const usedTokens = page.locator('.usage-card').first().locator('b')
    await expect(usedTokens).toBeVisible()
    await expect(usedTokens).not.toHaveText('0')
  })
})
