<script setup>
import { ref, shallowRef, onMounted } from 'vue'
import { listProducts } from './api'

// 四个视图
import ProductsView from './views/ProductsView.vue'
import OrdersView from './views/OrdersView.vue'
import PositionsView from './views/PositionsView.vue'
import AdminView from './views/AdminView.vue'

const activeTab = ref('products')
const tabs = [
  { name: 'products', label: '产品与申购', icon: '💰' },
  { name: 'orders', label: '订单查询', icon: '📋' },
  { name: 'positions', label: '持仓与资金', icon: '📊' },
  { name: 'admin', label: '管理端', icon: '⚙️' }
]
const currentView = shallowRef(ProductsView)

function switchTab(name) {
  activeTab.value = name
  const map = { products: ProductsView, orders: OrdersView, positions: PositionsView, admin: AdminView }
  currentView.value = map[name]
}

// 顶部问候语 + 后端连通性
const serverOk = ref(false)
onMounted(async () => {
  try {
    await listProducts()
    serverOk.value = true
  } catch (e) {
    serverOk.value = false
  }
})
</script>

<template>
  <div class="layout">
    <!-- 顶部导航 -->
    <header class="topbar">
      <div class="brand">
        <span class="logo">📈</span>
        <span class="title">基金申购赎回管理系统</span>
        <el-tag size="small" :type="serverOk ? 'success' : 'danger'" class="conn">
          {{ serverOk ? '后端已连接' : '后端未连接' }}
        </el-tag>
      </div>
      <div class="customer-info">客户：C001 ｜ 测试账户</div>
    </header>

    <!-- 视图切换（移动端可横向滚动） -->
    <nav class="tabbar">
      <div
        v-for="t in tabs"
        :key="t.name"
        class="tab"
        :class="{ active: activeTab === t.name }"
        @click="switchTab(t.name)"
      >
        <span class="tab-icon">{{ t.icon }}</span>
        <span>{{ t.label }}</span>
      </div>
    </nav>

    <!-- 内容区 -->
    <main class="content">
      <component :is="currentView" />
    </main>
  </div>
</template>

<style scoped>
.layout { min-height: 100%; display: flex; flex-direction: column; }
.topbar {
  background: #fff;
  padding: 14px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 10;
}
.brand { display: flex; align-items: center; gap: 10px; }
.logo { font-size: 22px; }
.title { font-size: 17px; font-weight: 600; }
.customer-info { font-size: 13px; color: #909399; }
.tabbar {
  display: flex;
  gap: 4px;
  background: #fff;
  padding: 8px 16px 0;
  border-bottom: 1px solid #e4e7ed;
  overflow-x: auto;
}
.tab {
  flex: 1;
  min-width: 110px;
  text-align: center;
  padding: 12px 0;
  cursor: pointer;
  color: #606266;
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 15px;
  white-space: nowrap;
}
.tab.active { color: #409eff; border-bottom-color: #409eff; font-weight: 600; }
.tab-icon { font-size: 17px; }
.content { flex: 1; padding: 20px; max-width: 1100px; width: 100%; margin: 0 auto; }
@media (max-width: 768px) {
  .content { padding: 12px; }
  .tab { min-width: 84px; font-size: 13px; }
  .customer-info { display: none; }
}
</style>
