export interface TestStep {
  id: string
  order: number
  text: string
}

export interface TestScenario {
  id: string
  name: string
  order: number
  steps: TestStep[]
}

export interface TestCase {
  id: string
  projectId: string
  name: string
  description: string | null
  status: string
  naturalLanguage: string
  generatedCode: string | null
  locatorMemory: string | null
  intentContract: string | null
  scenarios: TestScenario[]
  createdAt: string
  updatedAt: string
}

export interface TestCaseRequest {
  name: string
  description: string
  naturalLanguage: string
}
