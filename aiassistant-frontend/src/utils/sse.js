import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getToken } from './storage'

export function chatStream(params, callbacks) {
  const { onThinking, onAnswer, onDone, onError } = callbacks
  const ctrl = new AbortController()

  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

  fetchEventSource(`${baseUrl}/api/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`
    },
    body: JSON.stringify(params),
    signal: ctrl.signal,

    onmessage(ev) {
      let data
      try {
        data = JSON.parse(ev.data)
      } catch {
        return
      }

      switch (ev.event) {
        case 'thinking':
          onThinking?.(data)
          break
        case 'answer':
          onAnswer?.(data)
          break
        case 'done':
          onDone?.(data)
          break
        case 'error':
          onError?.(data)
          break
      }
    },

    onerror(err) {
      onError?.({ content: err.message || '连接异常' })
      throw err
    },

    onclose() {
      // stream closed
    }
  })

  return ctrl
}
