import { createProject, getProject, listProjects, updateProject } from '../../api/projects'
import { createTestCase, getTestCase, listTestCases, updateTestCase } from '../../api/testcases'
import type { Project } from '../../types/project'
import type { TestCase } from '../../types/testcase'
import { testNameFrom } from './example'
import { collapseRepeatedInstructions } from './instructions'
import { readWorkspaceSession, writeWorkspaceSession } from './session'

export interface RecentTest {
  projectId: string
  projectName: string
  applicationUrl: string
  testCase: TestCase
}

export async function persistWorkspaceInput(
  applicationUrl: string,
  instructions: string,
): Promise<{ project: Project; testCase: TestCase }> {
  const url = applicationUrl.trim()
  const text = collapseRepeatedInstructions(instructions)
  const stored = readWorkspaceSession()

  let project: Project | null = null
  if (stored.projectId) {
    try {
      project = await getProject(stored.projectId)
    } catch {
      project = null
    }
  }
  if (!project) {
    const projects = await listProjects()
    project = projects.find((item) => item.name === 'SmartQA Workspace') ?? null
  }

  if (project) {
    if (project.applicationUrl !== url) {
      project = await updateProject(project.id, {
        name: project.name,
        description: project.description ?? '',
        applicationUrl: url,
        environment: project.environment ?? 'local',
      })
    }
  } else {
    project = await createProject({
      name: 'SmartQA Workspace',
      description: 'Primary Test Generation Workspace',
      applicationUrl: url,
      environment: 'local',
    })
  }

  let testCase: TestCase | null = null
  if (stored.testCaseId) {
    try {
      const loaded = await getTestCase(stored.testCaseId)
      if (loaded.projectId === project.id) {
        testCase = loaded
      }
    } catch {
      testCase = null
    }
  }
  if (!testCase) {
    // Never silently reuse another test — that makes New Test show the previous case.
    testCase = null
  }

  const name = testNameFrom(url, text)
  if (testCase) {
    if (testCase.naturalLanguage !== text || !testCase.name) {
      testCase = await updateTestCase(testCase.id, {
        name: testCase.name || name,
        description: testCase.description ?? '',
        naturalLanguage: text,
      })
    }
  } else {
    testCase = await createTestCase(project.id, {
      name,
      description: '',
      naturalLanguage: text,
    })
  }
  writeWorkspaceSession(project.id, testCase.id)
  return { project, testCase }
}

export async function loadRecentTests(): Promise<RecentTest[]> {
  const projects = await listProjects()
  const groups = await Promise.all(
    projects.map(async (project) => {
      const cases = await listTestCases(project.id)
      return cases.map((testCase) => ({
        projectId: project.id,
        projectName: project.name,
        applicationUrl: project.applicationUrl,
        testCase: {
          ...testCase,
          naturalLanguage: collapseRepeatedInstructions(testCase.naturalLanguage ?? ''),
        },
      }))
    }),
  )
  return groups
    .flat()
    .sort((left, right) => right.testCase.updatedAt.localeCompare(left.testCase.updatedAt))
    .slice(0, 8)
}
