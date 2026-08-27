import { apiData } from './client'
import type { Project, ProjectRequest } from '../types/project'

export function listProjects() {
  return apiData<Project[]>('/api/projects')
}

export function getProject(id: string) {
  return apiData<Project>(`/api/projects/${id}`)
}

export function createProject(request: ProjectRequest) {
  return apiData<Project>('/api/projects', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateProject(id: string, request: ProjectRequest) {
  return apiData<Project>(`/api/projects/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteProject(id: string) {
  return apiData<null>(`/api/projects/${id}`, { method: 'DELETE' })
}
