import axios from 'axios'
import { ElMessage } from 'element-plus'

// axios 实例：统一处理后端 ApiResponse 结构（code=0 成功）
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code === 0) {
      return body.data
    }
    // 业务错误（如资金不足 40003）
    ElMessage.error((body && body.message) || '请求失败')
    return Promise.reject(new Error((body && body.message) || '请求失败'))
  },
  (err) => {
    ElMessage.error(err.message || '网络异常，请确认后端服务已启动')
    return Promise.reject(err)
  }
)

export default http

// ========== 产品 ==========
export const listProducts = () => http.get('/products')

// ========== 订单 ==========
export const createSubscription = (payload) => http.post('/orders/subscription', payload)
export const createRedemption = (payload) => http.post('/orders/redemption', payload)
export const listSubscriptionOrders = (params) =>
  http.get('/orders/subscription', { params })
export const listRedemptionOrders = (params) =>
  http.get('/orders/redemption', { params })
export const getOrderDetail = (orderId) => http.get(`/orders/${orderId}`)

// ========== 客户：持仓 / 资金 ==========
export const listPositions = (customerId) =>
  http.get(`/customers/${customerId}/positions`)
export const getFundsAccount = (customerId) =>
  http.get(`/customers/${customerId}/funds-account`)

// ========== 管理端：净值发布 / T+1 确认 ==========
export const publishNetValue = (payload) => http.post('/admin/net-values', payload)
export const runConfirmations = (payload) => http.post('/admin/confirmations/run', payload)
