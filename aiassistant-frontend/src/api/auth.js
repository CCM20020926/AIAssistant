import request from './request.js'

export function login(data) {
  return request.post('/api/auth/login', data)
}

export function register(data) {
  return request.post('/api/auth/register', data)
}

export function logout() {
  return request.post('/api/auth/logout')
}
