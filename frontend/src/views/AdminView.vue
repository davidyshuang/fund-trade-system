<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listProducts, publishNetValue, runConfirmations } from '../api'

const products = ref([])
const navForm = ref({ productId: '', navDate: '', nav: '' })
const navLoading = ref(false)
const confirmForm = ref({ tDay: '' })
const confirmLoading = ref(false)
const confirmResult = ref(null)

async function load() {
  const data = await listProducts()
  products.value = data.list || []
  // 默认取第一个产品 + 今天
  if (products.value.length && !navForm.value.productId) {
    navForm.value.productId = products.value[0].productId
  }
  if (!navForm.value.navDate) navForm.value.navDate = todayStr()
  if (!confirmForm.value.tDay) confirmForm.value.tDay = todayStr()
}
function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}
onMounted(load)

async function doPublish() {
  navLoading.value = true
  try {
    await publishNetValue({
      productId: navForm.value.productId,
      navDate: navForm.value.navDate,
      nav: navForm.value.nav
    })
    ElMessage.success(`已发布 ${navForm.value.productId} 于 ${navForm.value.navDate} 的净值 ${navForm.value.nav}`)
  } catch (e) { /* 错误已提示 */ } finally {
    navLoading.value = false
  }
}

async function doConfirm() {
  confirmLoading.value = true
  try {
    const r = await runConfirmations({ tDay: confirmForm.value.tDay })
    confirmResult.value = r
    ElMessage.success(`确认完成：申购成功 ${r.subscriptionConfirmed}，赎回成功 ${r.redemptionConfirmed}`)
  } catch (e) { /* 错误已提示 */ } finally {
    confirmLoading.value = false
  }
}
</script>

<template>
  <div>
    <el-alert
      title="管理端操作说明"
      type="info"
      :closable="false"
      show-icon
      description="第一步：为 T 日发布产品净值；第二步：触发 T+1 确认批处理，系统将确认 T 日的全部申购与赎回订单（份额入账、资金扣款/到账）。T 日 = 订单的 tDay 字段。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" class="page-card">
      <template #header><div class="card-header">① 发布净值</div></template>
      <el-form inline label-width="80px">
        <el-form-item label="产品">
          <el-select v-model="navForm.productId" style="width: 200px">
            <el-option v-for="p in products" :key="p.productId" :value="p.productId" :label="`${p.productId} ${p.productName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="净值日期">
          <el-date-picker v-model="navForm.navDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="净值">
          <el-input-number v-model="navForm.nav" :min="0" :precision="4" :step="0.01" style="width: 160px" placeholder="如 1.2500" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="navLoading" @click="doPublish">发布净值</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="page-card" style="margin-top: 16px">
      <template #header><div class="card-header">② 触发 T+1 确认批处理</div></template>
      <el-form inline label-width="80px">
        <el-form-item label="T日">
          <el-date-picker v-model="confirmForm.tDay" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="confirmLoading" @click="doConfirm">执行确认</el-button>
        </el-form-item>
      </el-form>

      <div v-if="confirmResult" class="result-box">
        <div class="result-item">申购确认成功：<b>{{ confirmResult.subscriptionConfirmed }}</b></div>
        <div class="result-item">申购确认失败：<b>{{ confirmResult.subscriptionFailed }}</b></div>
        <div class="result-item">赎回确认成功：<b>{{ confirmResult.redemptionConfirmed }}</b></div>
        <div class="result-item">赎回确认失败：<b>{{ confirmResult.redemptionFailed }}</b></div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-card { border-radius: 10px; }
.card-header { font-weight: 600; }
.result-box {
  margin-top: 8px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 8px;
  padding: 12px 16px;
}
.result-item { font-size: 14px; }
.result-item b { color: #67c23a; }
@media (max-width: 768px) {
  .result-box { flex-direction: column; gap: 6px; }
}
</style>
