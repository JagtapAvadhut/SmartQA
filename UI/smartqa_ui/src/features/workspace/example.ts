export const ORANGEHRM_URL = 'https://opensource-demo.orangehrmlive.com/'

export const ORANGEHRM_INSTRUCTIONS = `Open the OrangeHRM application.

Click the 'Username' text box and type "admin".

Click the "Password" text box and type "admin123".

Click the "Login" button.

Click on Search text box in the left sidebar and type "Admin".

Select "Admin" from menu item.

Click on "+ Add" button.

Click on the "---Select---" under User Role.

Select "ESS" item from option.

Click on the "Type for hints..." text box under Employee Name and type "Radha Gupta".

Click on "Save" button.

Verify text as "Passwords do not match".`

export function isValidHttpUrl(value: string): boolean {
  const trimmed = value.trim()
  try {
    const parsed = new URL(trimmed)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

export function testNameFrom(applicationUrl: string, instructions: string): string {
  const firstLine = instructions
    .split('\n')
    .map((line) => line.trim())
    .find((line) => line.length > 0)
  if (firstLine) {
    return firstLine.length > 60 ? `${firstLine.slice(0, 57)}...` : firstLine
  }
  try {
    return new URL(applicationUrl).hostname.replace(/^www\./, '')
  } catch {
    return 'Untitled test'
  }
}
