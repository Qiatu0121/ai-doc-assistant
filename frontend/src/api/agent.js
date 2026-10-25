import http from './http'

export function chatWithAgent(message) {
  return http.post(
    '/agent/chat',
    { message },
    // Agent 可能连续调用多个工具，给出比普通接口更长的等待时间
    { timeout: 300_000 },
  )
}
