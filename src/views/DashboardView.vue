<template>
  <v-container class="dashboard-page py-8 px-6" fluid style="max-width: 1400px;">
    <!-- Header -->
    <div class="dashboard-hero mb-6">
      <div class="d-flex flex-column flex-md-row justify-space-between align-start align-md-center ga-4">
        <div>
          <div class="eyebrow mb-3">
            SEO Operations Center
          </div>

          <h1 class="hero-title mb-2">
            Dashboard
          </h1>

          <p class="hero-subtitle mb-0">
            Monitor processed URLs, discovery status, backend signals and recent indexing activity in one clean workspace.
          </p>
        </div>

        <v-chip class="live-chip" size="small" variant="flat">
          <span class="chip-dot"></span>
          Live monitor
        </v-chip>
      </div>
    </div>

    <!-- Stats -->
    <v-row class="mb-6">
      <v-col cols="12" sm="6" md="3">
        <v-card class="stat-card" variant="flat">
          <div class="pa-5">
            <div class="stat-icon stat-icon-blue">
              <v-icon size="20">mdi-link-variant</v-icon>
            </div>

            <span class="stat-label">
              Total Processed
            </span>

            <div class="d-flex align-baseline mt-2">
              <span class="stat-number">{{ stats.total }}</span>
              <span class="stat-unit ml-2">URLs</span>
            </div>
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card class="stat-card" variant="flat">
          <div class="pa-5">
            <div class="stat-icon stat-icon-green">
              <v-icon size="20">mdi-check-circle-outline</v-icon>
            </div>

            <span class="stat-label">
              Successful Signals
            </span>

            <div class="d-flex align-baseline mt-2">
              <span class="stat-number text-success">{{ stats.success }}</span>
              <span class="stat-rate text-success ml-2">{{ successRate }}%</span>
            </div>
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card class="stat-card" variant="flat">
          <div class="pa-5">
            <div class="stat-icon stat-icon-red">
              <v-icon size="20">mdi-alert-circle-outline</v-icon>
            </div>

            <span class="stat-label">
              Failed or Skipped
            </span>

            <div class="d-flex align-baseline mt-2">
              <span class="stat-number text-error">{{ stats.failed }}</span>
              <span class="stat-rate text-error ml-2">review</span>
            </div>
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card class="stat-card" variant="flat">
          <div class="pa-5">
            <div class="d-flex justify-space-between align-center mb-2">
              <span class="stat-label">
                Current Batch
              </span>

              <span class="quota-text">
                {{ stats.quotaUsed }} / {{ stats.quotaLimit }}
              </span>
            </div>

            <v-progress-linear
              :model-value="quotaPercent"
              height="8"
              color="#22d3ee"
              bg-color="rgba(148, 163, 184, 0.18)"
              rounded
              class="mt-5"
            ></v-progress-linear>

            <div class="quota-note mt-3">
              Tracks the latest batch processed from the Indexing workspace.
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- History -->
    <v-card class="table-card" variant="flat">
      <div class="table-toolbar">
        <div>
          <h3 class="table-title mb-1">
            Recent Activity
          </h3>

          <p class="table-desc mb-0">
            Activity data is loaded from the latest indexing run saved in your browser.
          </p>
        </div>

        <v-btn
          icon="mdi-refresh"
          variant="flat"
          size="small"
          class="refresh-btn"
          @click="loadDashboardData"
        ></v-btn>
      </div>

      <div class="table-wrap">
        <v-table class="custom-table">
          <thead>
            <tr>
              <th style="width: 14%;">Time</th>
              <th style="width: 52%;">URL</th>
              <th style="width: 14%;">HTTP</th>
              <th style="width: 12%;">Method</th>
              <th class="text-right" style="width: 8%;">Status</th>
            </tr>
          </thead>

          <tbody v-if="historyData.length > 0">
            <tr v-for="(item, index) in historyData" :key="index">
              <td class="time-cell">
                {{ item.time || '--:--:--' }}
              </td>

              <td class="url-cell">
                <div class="url-main text-truncate">
                  {{ item.url }}
                </div>

                <div
                  v-if="item.finalUrl && item.finalUrl !== item.url"
                  class="url-final text-truncate"
                >
                  Final: {{ item.finalUrl }}
                </div>

                <div
                  v-if="item.message"
                  class="url-message text-truncate"
                >
                  {{ item.message }}
                </div>
              </td>

              <td>
                <span class="soft-badge badge-http">
                  {{ item.httpStatus || '-' }}
                </span>
              </td>

              <td>
                <span class="soft-badge" :class="getMethodClass(item.method)">
                  {{ item.method || '-' }}
                </span>
              </td>

              <td class="text-right">
                <span :class="isSuccess(item) ? 'status-pill success' : 'status-pill error'">
                  {{ isSuccess(item) ? 'OK' : 'Fail' }}
                </span>
              </td>
            </tr>
          </tbody>

          <tbody v-else>
            <tr>
              <td colspan="5" class="empty-state">
                <div class="empty-icon">
                  <v-icon size="26">mdi-database-search-outline</v-icon>
                </div>

                <div class="empty-title">
                  No activity yet
                </div>

                <div class="empty-desc">
                  Run a task in the Indexing workspace and the latest results will appear here.
                </div>
              </td>
            </tr>
          </tbody>
        </v-table>
      </div>
    </v-card>
  </v-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const stats = ref({
  total: 0,
  success: 0,
  failed: 0,
  quotaUsed: 0,
  quotaLimit: 200
})

const historyData = ref([])

const successRate = computed(() => {
  if (!stats.value.total) {
    return 0
  }

  return Math.round((stats.value.success / stats.value.total) * 100)
})

const quotaPercent = computed(() => {
  if (!stats.value.quotaLimit) {
    return 0
  }

  return Math.min(100, Math.round((stats.value.quotaUsed / stats.value.quotaLimit) * 100))
})

function isSuccess(item) {
  return item.status === 'success' ||
    item.status === 'Thành công' ||
    item.status === 'Đã gửi' ||
    item.status === 'Browser fallback' ||
    item.pingStatus === 'submitted_for_discovery'
}

function getMethodClass(method) {
  if (method === 'Backend') {
    return 'badge-success'
  }

  if (method === 'Browser') {
    return 'badge-warning'
  }

  if (method === 'Skipped') {
    return 'badge-muted'
  }

  if (method === 'Failed') {
    return 'badge-danger'
  }

  return 'badge-neutral'
}

function loadDashboardData() {
  const saved = localStorage.getItem('seo_dashboard_data')

  if (!saved) {
    resetDashboard()
    return
  }

  try {
    const result = JSON.parse(saved)

    const total = Number(result.total || 0)
    const success = Number(result.success || 0)
    const failed = Number(result.fail || result.failed || 0)

    stats.value = {
      total,
      success,
      failed,
      quotaUsed: total,
      quotaLimit: 200
    }

    historyData.value = Array.isArray(result.logs) ? result.logs : []
  } catch (error) {
    console.error('Could not read dashboard data:', error)
    resetDashboard()
  }
}

function resetDashboard() {
  stats.value = {
    total: 0,
    success: 0,
    failed: 0,
    quotaUsed: 0,
    quotaLimit: 200
  }

  historyData.value = []
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.14), transparent 32%),
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.11), transparent 30%),
    linear-gradient(180deg, #070b12 0%, #0b1120 42%, #07080d 100%);
  color: #f8fafc;
}

.dashboard-hero,
.stat-card,
.table-card {
  background: rgba(15, 23, 42, 0.78) !important;
  border: 1px solid rgba(148, 163, 184, 0.16) !important;
  border-radius: 26px !important;
  box-shadow: 0 22px 70px rgba(0, 0, 0, 0.28) !important;
  backdrop-filter: blur(18px);
}

.dashboard-hero {
  padding: 30px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  color: #67e8f9;
  background: rgba(8, 145, 178, 0.12);
  border: 1px solid rgba(34, 211, 238, 0.22);
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 0.75rem;
  font-weight: 850;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.hero-title {
  color: #f8fafc;
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 900;
  letter-spacing: -0.055em;
  line-height: 1;
}

.hero-subtitle {
  color: #94a3b8;
  font-size: 1rem;
  max-width: 760px;
}

.live-chip {
  background: rgba(34, 197, 94, 0.1) !important;
  color: #86efac !important;
  border: 1px solid rgba(34, 197, 94, 0.28) !important;
  font-weight: 850;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.chip-dot {
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 0 6px rgba(34, 197, 94, 0.14);
}

.stat-card {
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 28px 76px rgba(0, 0, 0, 0.34) !important;
}

.stat-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  margin-bottom: 14px;
}

.stat-icon-blue {
  background: rgba(34, 211, 238, 0.11);
  color: #67e8f9;
}

.stat-icon-green {
  background: rgba(34, 197, 94, 0.11);
  color: #86efac;
}

.stat-icon-red {
  background: rgba(248, 113, 113, 0.11);
  color: #fca5a5;
}

.stat-label {
  color: #94a3b8;
  font-size: 0.75rem;
  font-weight: 850;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

.stat-number {
  color: #f8fafc;
  font-size: 2.15rem;
  font-weight: 900;
  letter-spacing: -0.055em;
}

.stat-unit,
.stat-rate,
.quota-note {
  color: #94a3b8;
  font-size: 0.82rem;
  font-weight: 750;
}

.quota-text {
  color: #e2e8f0;
  font-size: 0.9rem;
  font-weight: 850;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 22px 24px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.table-title {
  color: #f8fafc;
  font-size: 1.12rem;
  font-weight: 900;
  letter-spacing: -0.03em;
}

.table-desc {
  color: #94a3b8;
  font-size: 0.9rem;
}

.refresh-btn {
  background: rgba(15, 23, 42, 0.9) !important;
  color: #e2e8f0 !important;
  border: 1px solid rgba(148, 163, 184, 0.18) !important;
}

.table-wrap {
  overflow: auto;
  max-height: 560px;
}

.custom-table {
  background: transparent !important;
}

.custom-table th {
  color: #94a3b8 !important;
  background: rgba(2, 6, 23, 0.5) !important;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14) !important;
  font-size: 0.75rem !important;
  font-weight: 900 !important;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 14px 18px !important;
  white-space: nowrap;
}

.custom-table td {
  color: #e2e8f0 !important;
  border-bottom: 1px solid rgba(148, 163, 184, 0.08) !important;
  padding: 15px 18px !important;
  font-size: 0.875rem !important;
}

.custom-table tbody tr:hover {
  background: rgba(30, 41, 59, 0.52) !important;
}

.time-cell {
  color: #94a3b8 !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 700;
}

.url-cell {
  max-width: 600px;
}

.url-main {
  color: #f8fafc;
  font-weight: 780;
}

.url-final {
  margin-top: 4px;
  color: #67e8f9;
  font-size: 0.78rem;
}

.url-message {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 0.78rem;
}

.soft-badge,
.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 0.72rem;
  font-weight: 900;
  white-space: nowrap;
}

.badge-http {
  background: rgba(100, 116, 139, 0.14);
  color: #cbd5e1;
  border: 1px solid rgba(148, 163, 184, 0.22);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.badge-success,
.status-pill.success {
  background: rgba(22, 163, 74, 0.12);
  color: #86efac;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.badge-warning {
  background: rgba(202, 138, 4, 0.12);
  color: #fde68a;
  border: 1px solid rgba(234, 179, 8, 0.3);
}

.badge-danger,
.status-pill.error {
  background: rgba(220, 38, 38, 0.12);
  color: #fca5a5;
  border: 1px solid rgba(248, 113, 113, 0.3);
}

.badge-muted,
.badge-neutral {
  background: rgba(100, 116, 139, 0.14);
  color: #cbd5e1;
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.empty-state {
  height: 310px;
  text-align: center;
  color: #94a3b8 !important;
}

.empty-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  border-radius: 20px;
  background: rgba(13, 202, 240, 0.08);
  color: #67e8f9;
}

.empty-title {
  color: #f8fafc;
  font-weight: 900;
  font-size: 1rem;
}

.empty-desc {
  color: #94a3b8;
  margin-top: 4px;
  font-size: 0.9rem;
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.8);
}

::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 999px;
}

@media (max-width: 768px) {
  .dashboard-hero {
    padding: 22px;
  }

  .table-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .table-wrap {
    max-height: 620px;
  }
}
</style>