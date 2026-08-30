<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listSubscriptionOrders, listRedemptionOrders, getOrderDetail } from '../api'

const orderType = ref('subscription')
const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 订单详情抽屉
const detailVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { customerId: 'C001', pageNum: pageNum.value, pageSize: pageSize.value }
    const api = orderType.value === 'subscription' ? listSubscriptionOrders : listRedemptionOrders
    const data = await api(params)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(orderType, () => { pageNum.value = 1; load() })

function onPageChange(p) { pageNum.value = p; load() }

async function openDetail(orderId) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getOrderDetail(orderId)
  } finally {
    detailLoading.value = false
  }
}

const subStatusMap = { CREATED: '待冻结', FUNDS_FROZEN: '资金已冻结', CONFIRMED: '已确认', CONFIRM_FAILED: '确认失败' }
const redStatusMap = { CREATED: '待冻结', SHARES_FROZEN: '份额已冻结', CONFIRMED: '已确认', CONFIRM_FAILED: '确认失败' }
const statusType = { CONFIRMED: 'success', FUNDS_FROZEN: 'primary', SHARES_FROZEN: 'primary', CREATED: 'info', CONFIRM_FAILED: 'danger' }
</script>

<template>
  <div>
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>订单查询（客户 C001）</span>
          <el-button size="small" @click="load" :loading="loading">刷新</el-button>
        </div>
      </template>

      <el-radio-group v-model="orderType" class="type-switch">
        <el-radio-button value="subscription">申购订单</el-radio-button>
        <el-radio-button value="redemption">赎回订单</el-radio-button>
      </el-radio-group>

      <el-table :data="list" v-loading="loading" stripe style="margin-top: 14px">
        <template v-if="orderType === 'subscription'">
          <el-table-column prop="orderId" label="订单号" min-width="230" show-overflow-tooltip />
          <el-table-column prop="productId" label="产品" width="80" />
          <el-table-column label="申购金额" width="120">
            <template #default="{ row }">¥{{ row.subscriptionAmount.toFixed(2) }}</template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column prop="orderId" label="订单号" min-width="230" show-overflow-tooltip />
          <el-table-column prop="productId" label="产品" width="80" />
          <el-table-column label="赎回份额" width="120">
            <template #default="{ row }">{{ row.redemptionShares.toFixed(2) }} 份</template>
          </el-table-column>
        </template>
        <el-table-column prop="tDay" label="T日" width="110" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType[row.status] || 'info'">
              {{ (orderType === 'subscription' ? subStatusMap : redStatusMap)[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openDetail(row.orderId)">详情/轨迹</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        style="margin-top: 14px; justify-content: flex-end"
        @current-change="onPageChange"
      />
    </el-card>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情与状态轨迹" size="480px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderId }}</el-descriptions-item>
            <el-descriptions-item label="产品">{{ detail.productId }}</el-descriptions-item>
            <el-descriptions-item label="T日">{{ detail.tDay }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusType[detail.status] || 'info'">
                {{ (orderType === 'subscription' ? subStatusMap : redStatusMap)[detail.status] || detail.status }}
              </el-tag>
            </el-descriptions-item>
            <template v-if="detail.subscriptionAmount !== null && detail.subscriptionAmount !== undefined">
              <el-descriptions-item label="申购金额">¥{{ detail.subscriptionAmount.toFixed(2) }}</el-descriptions-item>
            </template>
            <template v-if="detail.redemptionShares !== null && detail.redemptionShares !== undefined">
              <el-descriptions-item label="赎回份额">{{ detail.redemptionShares.toFixed(2) }} 份</el-descriptions-item>
            </template>
            <template v-if="detail.confirmedNetValue">
              <el-descriptions-item label="确认净值">{{ detail.confirmedNetValue }}</el-descriptions-item>
            </template>
            <template v-if="detail.confirmedShares">
              <el-descriptions-item label="确认份额">{{ detail.confirmedShares }} 份</el-descriptions-item>
            </template>
            <template v-if="detail.confirmedFee !== null && detail.confirmedFee !== undefined">
              <el-descriptions-item label="申购费">¥{{ detail.confirmedFee.toFixed(2) }}</el-descriptions-item>
            </template>
            <template v-if="detail.redemptionAmount !== null && detail.redemptionAmount !== undefined">
              <el-descriptions-item label="到账金额">¥{{ detail.redemptionAmount.toFixed(2) }}</el-descriptions-item>
            </template>
            <template v-if="detail.redemptionFee !== null && detail.redemptionFee !== undefined">
              <el-descriptions-item label="赎回费">¥{{ detail.redemptionFee.toFixed(2) }}</el-descriptions-item>
            </template>
            <template v-if="detail.failReason">
              <el-descriptions-item label="失败原因">
                <span style="color: #f56c6c">{{ detail.failReason }}</span>
              </el-descriptions-item>
            </template>
          </el-descriptions>

          <div class="trace-title">状态流转轨迹</div>
          <el-timeline>
            <el-timeline-item
              v-for="(t, i) in detail.traces"
              :key="i"
              :timestamp="t.occurredAt"
              :type="i === detail.traces.length - 1 ? 'success' : 'primary'"
            >
              <div class="trace-event">{{ t.triggerEvent }}</div>
              <div class="trace-status">{{ t.fromStatus || '—' }} → {{ t.toStatus }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-card { border-radius: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.type-switch { margin-top: 4px; }
.trace-title { font-weight: 600; margin: 18px 0 12px; }
.trace-event { font-weight: 600; }
.trace-status { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
