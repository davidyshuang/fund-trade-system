<script setup>
import { ref, onMounted } from 'vue'
import { listPositions, getFundsAccount, listProducts } from '../api'

const positions = ref([])
const funds = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [pos, fa, prods] = await Promise.all([
      listPositions('C001'),
      getFundsAccount('C001'),
      listProducts()
    ])
    const nameMap = {}
    ;(prods.list || []).forEach((p) => { nameMap[p.productId] = p.productName })
    positions.value = (pos || []).map((x) => ({ ...x, productName: nameMap[x.productId] || x.productName }))
    funds.value = fa
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div>
    <!-- 资金账户卡片 -->
    <el-card shadow="never" class="page-card funds-card">
      <template #header>
        <div class="card-header"><span>资金账户</span><el-button size="small" @click="load" :loading="loading">刷新</el-button></div>
      </template>
      <div v-loading="loading" class="funds-grid">
        <div class="fund-item">
          <div class="fund-label">账户余额</div>
          <div class="fund-value main">¥{{ funds ? funds.balance.toFixed(2) : '—' }}</div>
        </div>
        <div class="fund-item">
          <div class="fund-label">冻结金额</div>
          <div class="fund-value warn">¥{{ funds ? funds.frozenAmount.toFixed(2) : '—' }}</div>
        </div>
        <div class="fund-item">
          <div class="fund-label">可用金额</div>
          <div class="fund-value">¥{{ funds ? funds.availableAmount.toFixed(2) : '—' }}</div>
        </div>
      </div>
    </el-card>

    <!-- 持仓列表 -->
    <el-card shadow="never" class="page-card" style="margin-top: 16px">
      <template #header><div class="card-header"><span>基金持仓</span></div></template>
      <el-empty v-if="!loading && positions.length === 0" description="暂无持仓" />
      <el-table v-else :data="positions" v-loading="loading" stripe>
        <el-table-column prop="productId" label="产品代码" width="110" />
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column label="总份额" width="130">
          <template #default="{ row }">{{ row.totalShares.toFixed(2) }} 份</template>
        </el-table-column>
        <el-table-column label="冻结份额" width="130">
          <template #default="{ row }">{{ row.frozenShares.toFixed(2) }} 份</template>
        </el-table-column>
        <el-table-column label="可用份额" width="130">
          <template #default="{ row }">
            <span style="color: #409eff; font-weight: 600">{{ row.availableShares.toFixed(2) }} 份</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-card { border-radius: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.funds-grid { display: flex; gap: 20px; flex-wrap: wrap; }
.fund-item { flex: 1; min-width: 150px; text-align: center; padding: 10px 0; }
.fund-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.fund-value { font-size: 22px; font-weight: 700; }
.fund-value.main { color: #67c23a; }
.fund-value.warn { color: #e6a23c; }
</style>
