package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutocompleteHandlerTest {

    @Test
    void waitsForDelayedAutocompleteOption() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <label>Employee Name
                        <input id="emp" placeholder="Type for hints" aria-autocomplete="list" />
                      </label>
                      <script>
                        const input = document.getElementById('emp');
                        input.addEventListener('input', () => {
                          setTimeout(() => {
                            const list = document.createElement('div');
                            list.setAttribute('role', 'listbox');
                            const option = document.createElement('div');
                            option.setAttribute('role', 'option');
                            option.textContent = 'Radha Gupta';
                            list.appendChild(option);
                            document.body.appendChild(list);
                          }, 3500);
                        });
                      </script>
                    </body></html>
                    """);
            Locator input = page.locator("#emp");
            input.fill("Radha Gupta");
            AutocompleteHandler.confirmSelectionIfNeeded(page, input, "Radha Gupta");
            assertEquals("Radha Gupta", page.getByRole(com.microsoft.playwright.options.AriaRole.OPTION).first().innerText().trim());
            browser.close();
        }
    }

    @Test
    void prefersNonExportLikeSuggestionWhenDomesticHostExpected() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <label>Search
                        <input id="q" placeholder="Type for hints" aria-autocomplete="list" />
                      </label>
                      <div role="listbox">
                        <div role="option" data-href="https://export.example.com/buy">Double screen export buyers</div>
                        <div role="option" data-href="https://www.example.com/search">Double screen mobile phone near Mumbai</div>
                      </div>
                      <div id="picked"></div>
                      <script>
                        document.querySelectorAll('[role=option]').forEach(opt => {
                          opt.addEventListener('click', () => {
                            document.getElementById('picked').textContent = opt.getAttribute('data-href');
                          });
                        });
                      </script>
                    </body></html>
                    """);
            Locator input = page.locator("#q");
            input.fill("Double screen");
            AutocompleteHandler.confirmSelectionIfNeeded(page, input, "Double screen mobile phone near Mumbai",
                    "https://www.example.com/");
            assertEquals("https://www.example.com/search", page.locator("#picked").innerText().trim());
            browser.close();
        }
    }
}
