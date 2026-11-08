import type { ToolVO } from '../api/api'

/** `GET /tools` as the backend answers it (ToolFacadeServiceTest pins the same names and labels). */
export const TOOLS: ToolVO[] = [
  tool('KnowledgeTool', 'KnowledgeTool', 'Knowledge search', 'FIXED'),
  tool('cityTool', 'getCity', 'Sample city', 'FIXED'),
  tool('dateTool', 'getDate', 'Date', 'FIXED'),
  tool('weatherTool', 'weather', 'Sample weather', 'FIXED'),
  tool('dataBaseTool', 'databaseQuery', 'Database', 'OPTIONAL'),
  tool('emailTool', 'sendEmail', 'Email', 'OPTIONAL'),
]

function tool(name: string, callName: string, label: string, type: ToolVO['type']): ToolVO {
  return { name, callName, label, description: `${label}.`, type }
}
