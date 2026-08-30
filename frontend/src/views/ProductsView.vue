<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listProducts, createSubscription, createRedemption } from '../api'

const products = ref([])
const loading = ref(false)

// 申购弹窗
const subVisible = ref(false)
const subForm = ref({ customerId: 'C001', productId: '', productName: '', subscriptionAmount: '' })
const subLoading = ref(false)

// 赎回弹窗
const redVisible = ref(false)
const redForm = ref({ customerId: 'C001', productId: '', productName: '', redemptionShares: '' })
const redLoading = ref(false)

async function load() {
  loading.value = true
  try {
    products.value = (await listProducts()).list || []
  } finally {
    loading.value = false
  }
}
onMounted(load)

function openSubscription(row) {
  subForm.value = { customerId: 'C001', productId: row.productId, productName: row.productName, subscriptionAmount: '' }
  subVisible.value = true
}
function openRedemption(row) {
  redForm.value = { customerId: 'C001', productId: row.productId, productName: row.productName, redemptionShares: '' }
  redVisible.value = true
}

async function doSubscription() {
  subLoading.value = true
  try {
    const r = await createSubscription({
      customerId: subForm.value.customerId,
      productId: subForm.value.productId,
      subscriptionAmount: subForm.value.subscriptionAmount
    })
    ElMessage.success(`申购成功！订单号：${r.orderId}，T日：${r.tDay}`)
    subVisible.value = false
  } catch (e) { /* 错误已提示 */ } finally {
    subLoading.value = false
  }
}

async function doRedemption() {
  redLoading.value = true
  try {
    const r = await createRedemption({
      customerId: redForm.value.customerId,
      productId: redForm.value.productId,
      redemptionShares: redForm.value.redemptionShares
    })
    ElMessage.success(`赎回下单成功！订单号：${r.orderId}，T日：${r.tDay}`)
    redVisible.value = false
  } catch (e) { /* 错误已提示 */ } finally {
    redLoading.value = false
  }
}

const statusMap = { ON_SALE: '在售', SUSPENDED_SUBSCRIPTION: '暂停申购', CLOSED: '已关闭' }
const statusType = { ON_SALE: 'success', SUSPENDED_SUBSCRIPTION: 'warning', CLOSED: 'info' }
</script>

<template>
  <div>
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>可申购产品</span>
          <el-button size="small" @click="load" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="products" v-loading="loading" stripe>
        <el-table-column prop="productCode" label="产品代码" width="110" />
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.riskLevel === 'L5' || row.riskLevel === 'L4' ? 'danger' : 'primary'">
              {{ row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申购费率" width="110">
          <template #default="{ row }">{{ (row.subscriptionFeeRate * 100).toFixed(2) }}%</template>
        </el-table-column>
        <el-table-column label="起购金额" width="120">
          <template #default="{ row }">¥{{ row.minSubscriptionAmount.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType[row.status] || 'info'">{{ statusMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="row.status !== 'ON_SALE'" @click="openSubscription(row)">申购</el-button>
            <el-button size="small" type="warning" :disabled="row.status !== 'ON_SALE'" @click="openRedemption(row)">赎回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 申购弹窗 -->
    <el-dialog v-model="subVisible" :title="`申购 ${subForm.productName}`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="客户编号">
          <el-input v-model="subForm.customerId" disabled />
        </el-form-item>
        <el-form-item label="申购金额">
          <el-input-number
            v-model="subForm.subscriptionAmount"
            :min="0" :precision="2" :step="100" style="width: 100%"
            placeholder="请输入申购金额"
          />
        </el-form-item>
        <el-form-item label="起购金额">
          <span style="color: #909399">以产品列表为准（金额不得低于起购金额）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" :loading="subLoading" @click="doSubscription">确认申购</el-button>
      </template>
    </el-dialog>

    <!-- 赎回弹窗 -->
    <el-dialog v-model="redVisible" :title="`赎回 ${redForm.productName}`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="客户编号">
          <el-input v-model="redForm.customerId" disabled />
        </el-form-item>
        <el-form-item label="赎回份额">
          <el-input-number
            v-model="redForm.redemptionShares"
            :min="0" :precision="2" :step="100" style="width: 100%"
            placeholder="请输入赎回份额"
          />
        </el-form-item>
        <el-form-item label="注意">
          <span style="color: #e6a23c">赎回份额不能超过可用份额；持有不足 7 天将收取 1.5% 赎回费</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="redVisible = false">取消</el-button>
        <el-button type="warning" :loading="redLoading" @click="doRedemption">确认赎回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-card { border-radius: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
</style>
