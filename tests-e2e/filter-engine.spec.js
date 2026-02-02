import { test, expect } from '@playwright/test';

// Define the pages that share our unified filter logic
const targetPages = [
    { name: 'Champions', url: '/champions' },
    { name: 'Profile', url: '/profile' },
    { name: 'Team Builder', url: '/teams/builder' }
];

// ARCHITECT'S NOTE: Animation-Aware Toggle
async function toggleFilters(page, shouldOpen) {
    const wrapper = page.locator('#filter-wrapper');

    // Check state efficiently
    // We use evaluate to get the TRUE computed style, ignoring animation frames
    const isVisible = await wrapper.isVisible() && await wrapper.evaluate(el => window.getComputedStyle(el).opacity !== '0');

    if (shouldOpen && !isVisible) {
        await page.click('#filter-text');

        // CRITICAL FIX: The CSS transition takes 500ms.
        // We must wait for this to finish, otherwise Playwright clicks a moving target.
        await expect(wrapper).toHaveCSS('opacity', '1');
        await page.waitForTimeout(600); // 500ms transition + 100ms buffer

    } else if (!shouldOpen && isVisible) {
        await page.click('#filter-text');

        // Wait for it to close fully
        await expect(wrapper).toHaveCSS('opacity', '0');
        await page.waitForTimeout(600); // Ensure grid has moved back up
    }
}

for (const target of targetPages) {
    test.describe(`Filter Engine Logic: ${target.name}`, () => {

        test.beforeEach(async ({ page }) => {
            await page.goto(target.url);
            await expect(page.locator('#visible-champion-count')).toBeVisible();
            await toggleFilters(page, true); // Open filters to set them
        });

        test('should hide cards when unchecking a rarity', async ({ page }) => {
            const initialCount = parseInt(await page.locator('#visible-champion-count').innerText());

            // Uncheck 'Legendary'
            await page.uncheck('.filter-rarity[data-filter-name="Legendary"]');

            const newCount = parseInt(await page.locator('#visible-champion-count').innerText());

            expect(newCount).toBeLessThan(initialCount);
            // Verify no Legendary cards are visible
            await expect(page.locator('.roster-card[data-rarity="Legendary"]:visible')).toHaveCount(0);
        });

        test('should filter by name search', async ({ page }) => {
            // 1. DYNAMIC SELECTOR setup
            let cardSelector;

            if (target.url.includes('builder')) {
                cardSelector = '.builder-champion-card';
            } else if (target.url.includes('profile')) {
                cardSelector = '.roster-card';
            } else {
                // Main Champions Page
                cardSelector = '.champion-card';
            }

            // 2. WAIT FOR DOM: Ensure elements exist
            const firstCard = page.locator(cardSelector).first();
            await expect(firstCard).toBeAttached();

            // 3. GRAB DATA
            const rawName = await firstCard.getAttribute('data-name');

            if (!rawName) {
                test.skip(`Skipping search test on ${target.name}: No champions found.`);
                return;
            }

            console.log(`[${target.name}] Dynamic Search Test: Found '${rawName}'`);

            // 4. SEARCH
            const searchInput = page.locator('#champion-search');
            await searchInput.fill(rawName);
            await page.waitForTimeout(500);

            // 5. CLOSE UI
            await toggleFilters(page, false);

            // 6. VERIFY
            // The count label usually lives in #visible-champion-count,
            // but double check if the main page uses a different ID for the counter too.
            // If this fails, we might need a dynamic selector for the counter as well.
            const countLabel = page.locator('#visible-champion-count');
            await expect(countLabel).toBeVisible();
            const newCount = parseInt(await countLabel.innerText());

            expect(newCount).toBeGreaterThan(0);

            const targetCard = page.locator(`${cardSelector}[data-name="${rawName}"]:visible`);
            await expect(targetCard.first()).toBeVisible();
        });

        test('should handle "Clear all" and "Check all"', async ({ page }) => {
                    // 1. CLEAR ALL
                    // Action: Click the "Clear all" button
                    await page.click('input[value="Clear all"]');
                    await page.waitForTimeout(300); // Allow UI to update

                    // Assertion A: The filtering logic (Empty = All) means items should STILL be visible
                    const clearCountText = await page.locator('#visible-champion-count').innerText();
                    expect(parseInt(clearCountText)).toBeGreaterThan(0);

                    // Assertion B: Verify the BUTTON worked by checking the checkboxes themselves
                    // We expect 0 checkboxes to be checked
                    const checkedBoxes = page.locator('.filter-checkbox:checked');
                    expect(await checkedBoxes.count()).toBe(0);

                    // 2. CHECK ALL
                    // Action: Click the "Check all" button
                    await page.click('input[value="Check all"]');
                    await page.waitForTimeout(300);

                    // Assertion C: Items should still be visible
                    const checkCountText = await page.locator('#visible-champion-count').innerText();
                    expect(parseInt(checkCountText)).toBeGreaterThan(0);

                    // Assertion D: Verify the BUTTON worked
                    // We expect NO checkboxes to be unchecked
                    const uncheckedBoxes = page.locator('.filter-checkbox:not(:checked)');
                    expect(await uncheckedBoxes.count()).toBe(0);
                });
    });
}

// --- UPDATED TEAM BUILDER TESTS (Final Version) ---
test.describe('Team Builder Specific Logic', () => {

    test('Siege Condition should sync with checkboxes', async ({ page }) => {
        await page.goto('/teams/builder');

        // 1. OPEN FILTERS FIRST
        // We must open the menu so the dropdown becomes visible/interactable
        await toggleFilters(page, true);

        // 2. LOCATE DROPDOWN
        const dropdown = page.locator('#siege-condition-selector');
        await expect(dropdown).toBeVisible();

        // 3. SELECT CONDITION
        // FIX: Use a simple String ('Affinity: Void'), not a Regex.
        await dropdown.selectOption({ label: 'Affinity: Void' });
        await page.waitForTimeout(300);

        // 4. VERIFY: 'Void' checkbox should be checked
        const voidCheckbox = page.locator('.filter-affinity[data-filter-name="Void"]');
        await expect(voidCheckbox).toBeChecked();

        // 5. VERIFY: 'Magic' checkbox should NOT be checked
        const magicCheckbox = page.locator('.filter-affinity[data-filter-name="Magic"]');
        await expect(magicCheckbox).not.toBeChecked();

        // 6. VERIFY: Grid Content
        // We use the builder-specific class here
        const visibleCard = page.locator('.builder-champion-card:visible').first();

        // Safety check: ensure we actually have cards to test
        if (await visibleCard.count() > 0) {
            await expect(visibleCard).toHaveAttribute('data-affinity', 'Void');
        } else {
             console.log("Warning: No Void champions found to verify grid content.");
        }
    });

    test('"No Condition" should select all', async ({ page }) => {
        await page.goto('/teams/builder');

        // 1. OPEN FILTERS
        await toggleFilters(page, true);

        // 2. LOCATE DROPDOWN
        const dropdown = page.locator('#siege-condition-selector');
        await expect(dropdown).toBeVisible();

        // 3. Select 'Void' first to set a state
        await dropdown.selectOption({ label: 'Affinity: Void' });

        // 4. Select "No Condition" (Value is usually empty string for default option)
        await dropdown.selectOption({ value: '' });
        await page.waitForTimeout(300);

        // 5. VERIFY: All Affinities checked
        const allAffinities = page.locator('.filter-affinity');
        expect(await allAffinities.count()).toBeGreaterThan(0);

        for (const checkbox of await allAffinities.all()) {
            await expect(checkbox).toBeChecked();
        }
    });
});