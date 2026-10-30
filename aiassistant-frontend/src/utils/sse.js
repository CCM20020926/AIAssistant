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
    openWhenHidden: true,

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

/**
 * Authenticated GET Server-Sent Events subscription.
 * Uses fetch-event-source (not native EventSource) so the Authorization header
 * can be sent. `url` is a path relative to the API base URL.
 *
 * @param {string} url SSE endpoint path
 * @param {{onMessage?: (evt:{event:string,data:any})=>void, onError?: (e:Error)=>void}} handlers
 * @returns {{close: () => void}}
 */
export function createAuthedEventSource(url, { onMessage, onError } = {}) {
  const ctrl = new AbortController()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

  fetchEventSource(`${baseUrl}${url}`, {
    headers: {
      'Accept': 'text/event-stream',
      'Authorization': `Bearer ${getToken()}`
    },
    signal: ctrl.signal,
    openWhenHidden: true,

    onmessage(ev) {
      let data
      try {
        data = JSON.parse(ev.data)
      } catch {
        data = ev.data
      }
      onMessage?.({ event: ev.event || 'message', data })
    },

    onerror(err) {
      onError?.(err)
      // Rethrow so fetch-event-source stops retrying; caller decides when to reconnect.
      throw err
    }
  }).catch(() => {})

  return {
    close() {
      ctrl.abort()
    }
  }
}
