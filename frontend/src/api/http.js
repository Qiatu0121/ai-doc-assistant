import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30_000,
})

// 后端业务异常同样返回 HTTP 200，需要按 Result.code 判定成功与否
http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (!payload || typeof payload !== 'object' || !Object.hasOwn(payload, 'code')) {
      return payload
    }
    if (payload.code === 200) {
      return payload.data
    }
    return Promise.reject(new Error(payload.message || '请求失败'))
  },
  (error) => {
    if (error.response?.data?.message) {
      return Promise.reject(new Error(error.response.data.message))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    if (!error.response) {
      return Promise.reject(new Error('无法连接后端服务，请确认服务已启动'))
    }
    return Promise.reject(new Error(error.message || '请求失败'))
  },
)

export default http
