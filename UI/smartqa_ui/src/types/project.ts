export interface Project {
  id: string
  name: string
  description: string | null
  applicationUrl: string
  environment: string | null
  testCaseCount: number
  createdAt: string
  updatedAt: string
}

export interface ProjectRequest {
  name: string
  description: string
  applicationUrl: string
  environment: string
}
