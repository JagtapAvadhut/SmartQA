package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDropdownHandlerTest {

    @Test
    void waitsForRealOptionInsteadOfPreexistingDropdownClass() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <div class="sidebar-dropdown">Decoy already on page</div>
                      <div class="field-group">
                        <label>User Role</label>
                        <div id="role-trigger" class="select-wrapper" tabindex="0">-- Select --</div>
                      </div>
                      <script>
                        const trigger = document.getElementById('role-trigger');
                        trigger.addEventListener('click', () => {
                          setTimeout(() => {
                            const list = document.createElement('div');
                            list.setAttribute('role', 'listbox');
                            const option = document.createElement('div');
                            option.setAttribute('role', 'option');
                            option.textContent = 'ESS';
                            list.appendChild(option);
                            document.body.appendChild(list);
                          }, 250);
                        });
                      </script>
                    </body></html>
                    """);
            CustomDropdownHandler.selectOption(page, page.locator("#role-trigger"), "ESS");
            assertEquals("ESS", page.getByRole(com.microsoft.playwright.options.AriaRole.OPTION).first().innerText().trim());
            browser.close();
        }
    }

    @Test
    void selectOptionMatchesWordPrefixOfVisibleOption() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <div id="status-trigger" aria-expanded="false">-- Select --</div>
                      <script>
                        const trigger = document.getElementById('status-trigger');
                        trigger.addEventListener('click', () => {
                          const list = document.createElement('div');
                          list.setAttribute('role', 'listbox');
                          const option = document.createElement('div');
                          option.setAttribute('role', 'option');
                          option.textContent = 'Enabled';
                          list.appendChild(option);
                          document.body.appendChild(list);
                          trigger.textContent = 'Enabled';
                        });
                      </script>
                    </body></html>
                    """);
            CustomDropdownHandler.selectOption(page, page.locator("#status-trigger"), "Enable");
            assertEquals("Enabled", page.locator("#status-trigger").innerText().trim());
            browser.close();
        }
    }

    @Test
    void selectsCheckboxInFilterPanelWithoutAriaOptions() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <button id="brand-trigger">Brand & Model</button>
                      <script>
                        const trigger = document.getElementById('brand-trigger');
                        trigger.addEventListener('click', () => {
                          const panel = document.createElement('div');
                          panel.setAttribute('class', 'dropdown-panel');
                          panel.innerHTML = '<input type="search" placeholder="Search brand">'
                            + '<label><input type="checkbox" id="volvo"> VOLVO</label>';
                          document.body.appendChild(panel);
                        });
                      </script>
                    </body></html>
                    """);
            CustomDropdownHandler.selectOption(page, page.locator("#brand-trigger"), "VOLVO");
            assertTrue(page.locator("#volvo").isChecked());
            browser.close();
        }
    }
}
