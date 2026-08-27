import { apiData } from './client'
import type { AiHealthSnapshot, AiSettings } from '../types/intent'

export function getAiSettings() {
  return apiData<AiSettings>('/api/settings/ai')
}

export function getAiHealth() {
  return apiData<AiHealthSnapshot>('/api/health/ai')
}
