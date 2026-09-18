<template>
  <div class="page">
    <h2 class="page-title">汇率报告</h2>
    <p class="page-desc">维护币种对汇率（操作员可改、只读不可改），按结算日与目标币种折算已完成批次的净头寸</p>

    <div class="card-panel">
      <div class="toolbar" style="justify-content:space-between">
        <strong>汇率表</strong>
        <el-button type="primary" :disabled="!auth.isOperator" @click="openEdit(null)">新增/修改汇率</el-button>
      </div>
      <el-table :data="rates" v-loading="ratesLoading" stripe style="margin-top:12px">
        <el-table-column label="币种对" width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.baseCurrency }} → {{ row.quoteCurrency }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rate" label="汇率" min-width="160" />
        <el-table-column prop="effectiveDate" label="生效日" width="120" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!auth.isOperator" @click="openEdit(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card-panel" style="margin-top:16px">
      <strong>折算净额报告</strong>
      <div class="toolbar" style="margin-top:12px">
        <el-date-picker v-model="settleDate" type="date" value-format="YYYY-MM-DD" placeholder="结算日" />
        <el-select v-model="targetCurrency" style="width:140px" placeholder="目标币种">
          <el-option label="USD" value="USD" />
          <el-option label="CNY" value="CNY" />
          <el-option label="EUR" value="EUR" />
        </el-select>
        <el-button type="primary" :loading="reportLoading" @click="generate">生成报告</el-button>
      </div>

      <el-alert
        v-if="reportError"
        type="error"
        :title="reportError"
        :closable="false"
        style="margin-top:12px"
        show-icon
      />

      <template v-if="report">
        <el-table :data="report.lines" stripe style="margin-top:12px">
          <el-table-column label="会员" min-width="220">
            <template #default="{ row }">
              <span class="mono">{{ row.memberId }}</span>
              <div>{{ nameOf(row.memberId) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="currency" label="原币种" width="90" />
          <el-table-column prop="netAmount" label="净头寸（原币）" min-width="160" />
          <el-table-column prop="rate" label="采用汇率" min-width="140" />
          <el-table-column prop="convertedAmount" :label="`折算净额（${report.targetCurrency}）`" min-width="180" />
        </el-table>

        <div class="totals">
          <el-table :data="report.memberTotals" stripe style="max-width:640px">
            <el-table-column label="会员" min-width="220">
              <template #default="{ row }">
                <span class="mono">{{ row.memberId }}</span>
                <div>{{ nameOf(row.memberId) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="totalConverted" :label="`折算合计（${report.targetCurrency}）`" min-width="180" />
          </el-table>
          <div class="grand">总计（{{ report.targetCurrency }}）：{{ report.grandTotal }}</div>
        </div>
      </template>
      <el-empty v-else-if="!reportError" description="选择结算日与目标币种后生成报告" :image-size="60" />
    </div>

    <el-dialog v-model="editVisible" :title="editForm.existing ? '修改汇率' : '新增汇率'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="基础币种">
          <el-select v-model="editForm.baseCurrency" :disabled="editForm.existing" style="width:100%">
            <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="报价币种">
          <el-select v-model="editForm.quoteCurrency" :disabled="editForm.existing" style="width:100%">
            <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日">
          <el-date-picker
            v-model="editForm.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="editForm.existing"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="汇率">
          <el-input v-model="editForm.rate" placeholder="如 1.08" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const currencies = ['USD', 'CNY', 'EUR']

const rates = ref([])
const ratesLoading = ref(false)
const settleDate = ref(new Date().toISOString().slice(0, 10))
const targetCurrency = ref('USD')
const report = ref(null)
const reportError = ref('')
const reportLoading = ref(false)
const memberMap = ref({})

const editVisible = ref(false)
const saving = ref(false)
const editForm = ref({ existing: false, baseCurrency: 'EUR', quoteCurrency: 'USD', rate: '', effectiveDate: '' })

function nameOf(id) {
  return memberMap.value[id] || ''
}

async function loadRates() {
  ratesLoading.value = true
  try {
    const { data } = await api.get('/fx-rates')
    rates.value = data
  } finally {
    ratesLoading.value = false
  }
}

async function generate() {
  reportLoading.value = true
  reportError.value = ''
  try {
    const { data } = await api.get('/fx-reports/converted-net', {
      params: { settleDate: settleDate.value, targetCurrency: targetCurrency.value }
    })
    report.value = data
  } catch (e) {
    report.value = null
    reportError.value = e.response?.data?.message || '报告生成失败'
  } finally {
    reportLoading.value = false
  }
}

function openEdit(row) {
  if (row) {
    editForm.value = {
      existing: true,
      baseCurrency: row.baseCurrency,
      quoteCurrency: row.quoteCurrency,
      rate: String(row.rate),
      effectiveDate: row.effectiveDate
    }
  } else {
    editForm.value = {
      existing: false,
      baseCurrency: 'EUR',
      quoteCurrency: 'USD',
      rate: '',
      effectiveDate: settleDate.value
    }
  }
  editVisible.value = true
}

async function saveRate() {
  const f = editForm.value
  if (!f.rate || Number(f.rate) <= 0) {
    ElMessage.warning('汇率必须为正数')
    return
  }
  if (f.baseCurrency === f.quoteCurrency) {
    ElMessage.warning('基础币种与报价币种必须不同')
    return
  }
  saving.value = true
  try {
    await api.post('/fx-rates', {
      baseCurrency: f.baseCurrency,
      quoteCurrency: f.quoteCurrency,
      rate: f.rate,
      effectiveDate: f.effectiveDate
    })
    editVisible.value = false
    ElMessage.success('汇率已保存')
    await loadRates()
    if (report.value) {
      await generate()
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loadRates()
  try {
    const { data } = await api.get('/members')
    memberMap.value = Object.fromEntries(data.map((x) => [x.memberId, x.name]))
  } catch (e) {
    // member names are cosmetic; ignore
  }
})
</script>

<style scoped>
.totals {
  margin-top: 16px;
}
.grand {
  margin-top: 12px;
  font-weight: 700;
}
</style>
