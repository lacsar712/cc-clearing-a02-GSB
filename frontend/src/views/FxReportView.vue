<template>
  <div class="page">
    <h2 class="page-title">汇率本与折算净额报告</h2>
    <p class="page-desc">维护币种对汇率与生效日，按结算日把各币种净头寸折算为目标币种；缺汇率直接失败，不按 1 兜底</p>

    <!-- 折算汇总 -->
    <div class="card-panel">
      <div class="toolbar">
        <el-date-picker v-model="query.settleDate" type="date" value-format="YYYY-MM-DD" placeholder="结算日" />
        <el-select v-model="query.targetCurrency" style="width:150px" filterable allow-create
                   default-first-option placeholder="目标币种">
          <el-option v-for="c in CURRENCIES" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" :loading="reportLoading" @click="generateReport">生成折算汇总</el-button>
        <span class="hint">汇率取「生效日 ≤ 结算日」的最新一条；支持直接与反向币种对</span>
      </div>

      <el-alert v-if="reportError" :title="reportError" type="error" show-icon :closable="false"
                style="margin-bottom:12px" />

      <template v-if="report">
        <div class="report-head">
          <strong>
            结算日 {{ report.settleDate }} 折算至 {{ report.targetCurrency }}
          </strong>
          <el-tag type="info" style="margin-left:8px">COMPLETED 批次 {{ report.runCount }} 个</el-tag>
          <span class="grand">折算净头寸合计：{{ fmt(report.grandTotalConverted) }} {{ report.targetCurrency }}</span>
        </div>

        <el-table :data="report.lines" stripe style="margin-top:12px">
          <el-table-column prop="memberId" label="会员 ID" min-width="200">
            <template #default="{ row }">
              <span class="mono">{{ row.memberId }}</span>
              <div>{{ row.memberName || '' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="sourceCurrency" label="源币种" width="90" />
          <el-table-column label="净头寸（原币，正应收/负应付）" min-width="200">
            <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
          </el-table-column>
          <el-table-column label="折算因子" min-width="130">
            <template #default="{ row }">{{ fmt(row.rateFactor) }}</template>
          </el-table-column>
          <el-table-column label="取数方式" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="directionType(row.rateDirection)">
                {{ directionLabel(row.rateDirection) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rateEffectiveDate" label="汇率生效日" width="120">
            <template #default="{ row }">{{ row.rateEffectiveDate || '—' }}</template>
          </el-table-column>
          <el-table-column label="折算后净额" min-width="190">
            <template #default="{ row }">
              <strong>{{ fmt(row.convertedAmount) }}</strong> {{ row.targetCurrency }}
            </template>
          </el-table-column>
        </el-table>

        <div class="sub-totals">
          <span class="sub-title">各源币种净头寸小计（轧差守恒，理论合计为 0）：</span>
          <el-tag v-for="t in report.currencyTotals" :key="t.currency"
                  class="sub-tag" :type="Number(t.netAmount) === 0 ? 'success' : 'info'">
            {{ t.currency }} {{ fmt(t.netAmount) }}
          </el-tag>
        </div>
      </template>
      <el-empty v-else-if="!reportError" description="请选择结算日与目标币种后生成折算汇总" />
    </div>

    <!-- 汇率表 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="toolbar" style="justify-content:space-between">
        <strong>汇率本（{{ rates.length }} 条）</strong>
        <div>
          <el-button @click="loadRates">刷新</el-button>
          <el-button type="primary" :disabled="!auth.isOperator" @click="openCreate">新增汇率</el-button>
        </div>
      </div>
      <el-table :data="rates" v-loading="ratesLoading" stripe>
        <el-table-column label="币种对" min-width="130">
          <template #default="{ row }">
            <strong>{{ row.baseCurrency }}/{{ row.quoteCurrency }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="汇率" min-width="200">
          <template #default="{ row }">
            1 {{ row.baseCurrency }} = {{ fmt(row.rate) }} {{ row.quoteCurrency }}
          </template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日" width="130" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!auth.isOperator" @click="openEdit(row)">
              修改
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="!auth.isOperator" class="readonly-hint">当前为只读账号，仅可查看汇率与报告；新增/修改请使用操作员账号。</p>
    </div>

    <!-- 新增 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '修改汇率' : '新增汇率'" width="420px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="基础币种" v-if="!editing">
          <el-select v-model="form.baseCurrency" style="width:100%" filterable allow-create
                     default-first-option placeholder="如 EUR">
            <el-option v-for="c in CURRENCIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="报价币种" v-if="!editing">
          <el-select v-model="form.quoteCurrency" style="width:100%" filterable allow-create
                     default-first-option placeholder="如 USD">
            <el-option v-for="c in CURRENCIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="币种对">
          <strong>{{ form.baseCurrency }}/{{ form.quoteCurrency }}</strong>（币种对不可改）
        </el-form-item>
        <el-form-item label="汇率">
          <el-input-number v-model="form.rate" :min="0.00000001" :precision="8" :step="0.01"
                           style="width:100%" controls-position="right" />
        </el-form-item>
        <el-form-item label="生效日">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD"
                          style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
const CURRENCIES = ['USD', 'CNY', 'EUR']

const today = new Date().toISOString().slice(0, 10)

const query = ref({ settleDate: today, targetCurrency: 'USD' })
const report = ref(null)
const reportLoading = ref(false)
const reportError = ref('')

const rates = ref([])
const ratesLoading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = ref({ baseCurrency: '', quoteCurrency: '', rate: 1, effectiveDate: today })

function fmt(n) {
  if (n === null || n === undefined || n === '') return '—'
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 })
}

function directionLabel(d) {
  if (d === 'DIRECT') return '直接汇率'
  if (d === 'INVERSE') return '反向汇率'
  return '同币种'
}

function directionType(d) {
  if (d === 'DIRECT') return 'success'
  if (d === 'INVERSE') return 'warning'
  return 'info'
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

async function generateReport() {
  reportLoading.value = true
  reportError.value = ''
  try {
    const { data } = await api.get('/fx-reports/converted-net', { params: query.value })
    report.value = data
  } catch (e) {
    report.value = null
    reportError.value = e.response?.data?.message || '折算报告生成失败'
  } finally {
    reportLoading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { baseCurrency: 'EUR', quoteCurrency: 'USD', rate: 1.1, effectiveDate: query.value.settleDate }
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  form.value = {
    baseCurrency: row.baseCurrency,
    quoteCurrency: row.quoteCurrency,
    rate: Number(row.rate),
    effectiveDate: row.effectiveDate
  }
  dialogVisible.value = true
}

async function save() {
  if (!editing.value && (!form.value.baseCurrency || !form.value.quoteCurrency)) {
    ElMessage.warning('请填写币种对')
    return
  }
  if (!editing.value && form.value.baseCurrency === form.value.quoteCurrency) {
    ElMessage.warning('基础币种与报价币种必须不同')
    return
  }
  if (!form.value.rate || Number(form.value.rate) <= 0) {
    ElMessage.warning('汇率必须为正数')
    return
  }
  if (!form.value.effectiveDate) {
    ElMessage.warning('请选择生效日')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await api.put(`/fx-rates/${editing.value.rateId}`, {
        rate: form.value.rate,
        effectiveDate: form.value.effectiveDate
      })
      ElMessage.success('汇率已修改')
    } else {
      await api.post('/fx-rates', {
        baseCurrency: form.value.baseCurrency,
        quoteCurrency: form.value.quoteCurrency,
        rate: form.value.rate,
        effectiveDate: form.value.effectiveDate
      })
      ElMessage.success('汇率已新增')
    }
    dialogVisible.value = false
    await loadRates()
    // 改一条汇率后，已生成的报告立即按新汇率重算
    if (report.value) {
      await generateReport()
    }
  } finally {
    saving.value = false
  }
}

onMounted(loadRates)
</script>

<style scoped>
.hint {
  color: var(--muted);
  font-size: 13px;
}
.report-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.grand {
  margin-left: auto;
  font-weight: 700;
  color: var(--accent);
}
.sub-totals {
  margin-top: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.sub-title {
  color: var(--muted);
  font-size: 13px;
}
.readonly-hint {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 13px;
}
</style>
