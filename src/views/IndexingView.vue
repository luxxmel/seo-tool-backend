<template>
  <div class="index-page">
    <div class="container py-4 py-lg-5" style="max-width: 1240px;">
      <!-- Header -->
      <div class="page-hero mb-4">
        <div class="d-flex flex-column flex-lg-row justify-content-between gap-3 align-items-lg-center">
          <div>
            <div class="eyebrow mb-2">
              URL Discovery Engine
            </div>

            <h1 class="page-title mb-2">
              Direct URL Ping Workspace
            </h1>

            <p class="page-subtitle mb-0">
              Send discovery signals for social links, satellite posts and tier-2 URLs. The backend checks each URL and still records a discovery attempt even when the target server blocks automated access.
            </p>
          </div>

          <div class="mode-card">
            <span class="pulse-dot"></span>
            Direct Ping Mode
          </div>
        </div>
      </div>

      <!-- Stats -->
      <div class="row g-3 mb-4">
        <div class="col-6 col-lg-3">
          <div class="stat-card">
            <div class="stat-label">Total URLs</div>
            <div class="stat-value">{{ results.length }}</div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="stat-card">
            <div class="stat-label">Submitted</div>
            <div class="stat-value text-success">{{ successCount }}</div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="stat-card">
            <div class="stat-label">Failed / Skipped</div>
            <div class="stat-value text-danger">{{ failCount }}</div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="stat-card">
            <div class="stat-label">Blocked Signals</div>
            <div class="stat-value text-warning">{{ serverBlockedCount }}</div>
          </div>
        </div>
      </div>

      <div class="row g-4">
        <!-- Input -->
        <div class="col-12 col-lg-5">
          <div class="panel-card h-100">
            <div class="panel-header">
              <div>
                <h2 class="panel-title mb-1">
                  URL Batch
                </h2>

                <p class="panel-desc mb-0">
                  Paste one URL per line. The backend will inspect HTTP status, final URL, noindex, server blocking and then submit a discovery signal.
                </p>
              </div>
            </div>

            <textarea
              id="urlTextArea"
              v-model="urlListText"
              class="form-control url-textarea"
              rows="13"
              placeholder="https://www.facebook.com/share/p/example&#10;https://x.com/example/status/example&#10;https://medium.com/@example/post-example"
              :disabled="isLoading"
            ></textarea>

            <div class="input-meta mt-3">
              <span>
                <i class="bi bi-list-ul me-1"></i>
                {{ parsedUrlCount }} URLs detected
              </span>

              <span>
                <i class="bi bi-cpu me-1"></i>
                Backend assisted
              </span>
            </div>

            <div class="d-grid mt-3">
              <button
                @click="handleBulkPing"
                class="btn btn-primary btn-action"
                :disabled="!urlListText.trim() || isLoading"
              >
                <span v-if="isLoading" class="spinner-border spinner-border-sm me-2"></span>
                <i v-else class="bi bi-lightning-charge-fill me-2"></i>
                {{ isLoading ? 'Processing...' : 'Send Discovery Signals' }}
              </button>
            </div>

            <div class="hint-box mt-3">
              <div class="hint-icon">
                <i class="bi bi-info-circle"></i>
              </div>

              <div>
                <strong>Operational note:</strong>
                VPN only affects requests when the Java backend runs on the same machine using that VPN. If the backend is hosted on Render or a VPS, proxy/VPN handling must be configured in that backend environment.
              </div>
            </div>
          </div>
        </div>

        <!-- Results -->
        <div class="col-12 col-lg-7">
          <div class="panel-card h-100">
            <div class="panel-header result-header">
              <div>
                <h2 class="panel-title mb-1">
                  Processing Results
                </h2>

                <p class="panel-desc mb-0">
                  Track HTTP status, final URL, processing method and discovery status for every URL.
                </p>
              </div>

              <span class="count-badge">
                {{ results.length }} URLs
              </span>
            </div>

            <div v-if="summaryMessage" class="summary-box mb-3" :class="summaryClass">
              {{ summaryMessage }}
            </div>

            <div class="table-wrap">
              <table class="table align-middle mb-0 result-table">
                <thead>
                  <tr>
                    <th style="width: 46%;">URL</th>
                    <th class="text-center" style="width: 14%;">HTTP</th>
                    <th class="text-center" style="width: 17%;">Method</th>
                    <th class="text-end" style="width: 23%;">Status</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-if="results.length === 0">
                    <td colspan="4" class="empty-state">
                      <div class="empty-icon">
                        <i class="bi bi-radar"></i>
                      </div>

                      <div class="empty-title">
                        No data yet
                      </div>

                      <div class="empty-desc">
                        Paste URLs and send discovery signals to start processing.
                      </div>
                    </td>
                  </tr>

                  <tr v-for="item in results" :key="item.url">
                    <td class="url-cell" :title="item.url">
                      <a
                        :href="item.url"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="url-main text-truncate"
                      >
                        {{ item.url }}
                        <i class="bi bi-box-arrow-up-right small ms-1 text-muted"></i>
                      </a>

                      <div
                        v-if="item.message"
                        class="url-message text-truncate"
                        :title="item.message"
                      >
                        {{ item.message }}
                      </div>

                      <div
                        v-if="item.finalUrl && item.finalUrl !== item.url"
                        class="url-final text-truncate"
                        :title="item.finalUrl"
                      >
                        Final URL: {{ item.finalUrl }}
                      </div>

                      <div v-if="item.time" class="url-time">
                        Processed at {{ item.time }}
                      </div>
                    </td>

                    <td class="text-center">
                      <span class="soft-badge badge-http">
                        {{ item.httpStatus || '-' }}
                      </span>
                    </td>

                    <td class="text-center">
                      <span class="soft-badge" :class="getMethodClass(item.method)">
                        {{ item.method || '-' }}
                      </span>
                    </td>

                    <td class="text-end">
                      <span class="soft-badge text-uppercase" :class="getStatusClass(item.status)">
                        {{ item.status }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="results.length > 0" class="footer-note mt-3">
              Results have been saved to the Dashboard.
              <span v-if="serverBlockedCount > 0">
                {{ serverBlockedCount }} URL(s) show server blocking signals and may need backend proxy/VPN review.
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, nextTick } from 'vue'
import axios from 'axios'

const API_BASE_URL = 'https://seo-tool-backend-phw2.onrender.com/api/seo'

const urlListText = ref('')
const results = ref([])
const isLoading = ref(false)
const summaryMessage = ref('')
const summaryType = ref('')

const parsedUrlCount = computed(() => {
  return parseUrls().length
})

const serverBlockedCount = computed(() => {
  return results.value.filter(item =>
    item.method === 'Browser' ||
    item.httpStatus === 403 ||
    item.httpStatus === '403' ||
    item.httpStatus === 429 ||
    item.httpStatus === '429' ||
    item.status === 'Blocked'
  ).length
})

const successCount = computed(() => {
  return results.value.filter(item =>
    item.status === 'Submitted' ||
    item.status === 'Browser Fallback'
  ).length
})

const failCount = computed(() => {
  return results.value.filter(item =>
    item.status === 'Failed' ||
    item.status === 'Skipped'
  ).length
})

const summaryClass = computed(() => {
  if (summaryType.value === 'success') return 'summary-success'
  if (summaryType.value === 'warning') return 'summary-warning'
  if (summaryType.value === 'danger') return 'summary-danger'
  return ''
})

function parseUrls() {
  return urlListText.value
    .split('\n')
    .map(url => url.trim())
    .filter(url => url !== '')
}

const handleBulkPing = async () => {
  const urls = parseUrls()

  if (urls.length === 0) {
    showSummary('Please enter at least one URL.', 'warning')
    return
  }

  isLoading.value = true
  summaryMessage.value = ''
  summaryType.value = ''

  results.value = urls.map(url => ({
    url,
    time: getCurrentTime(),
    status: 'Processing',
    message: 'Checking URL with backend...',
    httpStatus: '',
    finalUrl: url,
    method: ''
  }))

  let backendData = []

  try {
    const response = await axios.post(`${API_BASE_URL}/direct-ping`, {
      urls
    })

    backendData = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    backendData = urls.map(url => ({
      url,
      time: getCurrentTime(),
      httpStatus: 'ERROR',
      finalUrl: url,
      alive: false,
      pingStatus: 'failed',
      message: 'Could not connect to backend. Check Render deployment, CORS or /api/seo/direct-ping endpoint.'
    }))
  }

  const browserPingResults = await runBrowserNoCorsPing(urls)

  results.value = urls.map(url => {
    const backendItem = backendData.find(item => item.url === url)
    const browserItem = browserPingResults.find(item => item.url === url)

    const backendSuccess =
      backendItem &&
      (
        backendItem.pingStatus === 'sent' ||
        backendItem.pingStatus === 'submitted_for_discovery'
      )

    const backendSkipped =
      backendItem &&
      (
        backendItem.pingStatus === 'skipped' ||
        backendItem.pingStatus === 'failed'
      )

    const browserSuccess = browserItem && browserItem.browserPing === true

    if (backendSuccess) {
      return {
        url,
        time: backendItem.time || getCurrentTime(),
        status: 'Submitted',
        message: backendItem.message || 'URL accepted. Discovery signal submitted.',
        httpStatus: backendItem.httpStatus || '',
        finalUrl: backendItem.finalUrl || url,
        method: 'Backend'
      }
    }

    if (browserSuccess) {
      return {
        url,
        time: getCurrentTime(),
        status: 'Browser Fallback',
        message: backendItem?.message || 'Backend was blocked or unavailable. Browser no-cors request was sent.',
        httpStatus: backendItem?.httpStatus || 'BROWSER',
        finalUrl: backendItem?.finalUrl || url,
        method: 'Browser'
      }
    }

    if (backendSkipped) {
      return {
        url,
        time: backendItem.time || getCurrentTime(),
        status: 'Skipped',
        message: backendItem.message || 'URL was not eligible for discovery submission.',
        httpStatus: backendItem.httpStatus || 'SKIP',
        finalUrl: backendItem.finalUrl || url,
        method: 'Skipped'
      }
    }

    return {
      url,
      time: getCurrentTime(),
      status: 'Failed',
      message: backendItem?.message || browserItem?.message || 'Could not process this URL.',
      httpStatus: backendItem?.httpStatus || 'ERROR',
      finalUrl: backendItem?.finalUrl || url,
      method: 'Failed'
    }
  })

  const dashboardData = {
    total: results.value.length,
    success: successCount.value,
    fail: failCount.value,
    logs: results.value.map(item => ({
      url: item.url,
      time: item.time,
      status: item.status === 'Failed' || item.status === 'Skipped' ? 'failed' : 'success',
      message: item.message,
      httpStatus: item.httpStatus,
      finalUrl: item.finalUrl,
      method: item.method
    }))
  }

  localStorage.setItem('seo_dashboard_data', JSON.stringify(dashboardData))

  isLoading.value = false
  await nextTick()

  let message = `Completed: ${successCount.value} discovery signal(s) submitted, ${failCount.value} URL(s) failed or skipped.`

  if (serverBlockedCount.value > 0) {
    message += ` ${serverBlockedCount.value} URL(s) show server blocking signals.`
  }

  showSummary(message, failCount.value > 0 || serverBlockedCount.value > 0 ? 'warning' : 'success')
}

async function runBrowserNoCorsPing(urls) {
  const output = []

  for (const url of urls) {
    try {
      await Promise.race([
        fetch(url, {
          method: 'GET',
          mode: 'no-cors',
          cache: 'no-store',
          credentials: 'omit'
        }),
        timeoutPromise(8000)
      ])

      output.push({
        url,
        browserPing: true,
        message: 'Browser no-cors request sent.'
      })
    } catch (error) {
      output.push({
        url,
        browserPing: false,
        message: error.message || 'Browser request failed.'
      })
    }

    await delay(800)
  }

  return output
}

function timeoutPromise(ms) {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error('Browser ping timeout')), ms)
  })
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function showSummary(message, type) {
  summaryMessage.value = message
  summaryType.value = type
}

function getStatusClass(status) {
  if (status === 'Submitted') {
    return 'badge-success'
  }

  if (status === 'Browser Fallback') {
    return 'badge-warning'
  }

  if (status === 'Processing') {
    return 'badge-processing'
  }

  if (status === 'Skipped') {
    return 'badge-muted'
  }

  if (status === 'Blocked') {
    return 'badge-warning'
  }

  return 'badge-danger'
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

function getCurrentTime() {
  return new Date().toLocaleTimeString('en-US', {
    hour12: false
  })
}
</script>

<style scoped>
.index-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.14), transparent 32%),
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.11), transparent 30%),
    linear-gradient(180deg, #070b12 0%, #0b1120 42%, #07080d 100%);
  color: #f8fafc;
}

.page-hero,
.stat-card,
.panel-card {
  background: rgba(15, 23, 42, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 28px;
  box-shadow: 0 22px 70px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(18px);
}

.page-hero {
  padding: 30px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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

.page-title {
  font-size: clamp(2rem, 4vw, 3.35rem);
  font-weight: 900;
  letter-spacing: -0.055em;
  color: #f8fafc;
  line-height: 1;
}

.page-subtitle {
  color: #94a3b8;
  font-size: 1rem;
  line-height: 1.65;
  max-width: 800px;
}

.mode-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: rgba(34, 197, 94, 0.1);
  color: #86efac;
  border: 1px solid rgba(34, 197, 94, 0.28);
  border-radius: 999px;
  padding: 12px 16px;
  font-weight: 850;
  box-shadow: 0 12px 30px rgba(34, 197, 94, 0.08);
  white-space: nowrap;
}

.pulse-dot {
  width: 10px;
  height: 10px;
  background: #22c55e;
  border-radius: 999px;
  box-shadow: 0 0 0 6px rgba(34, 197, 94, 0.14);
}

.stat-card {
  padding: 18px 20px;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 28px 76px rgba(0, 0, 0, 0.34);
}

.stat-label {
  color: #94a3b8;
  font-size: 0.76rem;
  font-weight: 850;
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

.stat-value {
  margin-top: 4px;
  color: #f8fafc;
  font-size: 1.9rem;
  font-weight: 900;
  letter-spacing: -0.04em;
}

.panel-card {
  padding: 22px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.result-header {
  align-items: center;
}

.panel-title {
  color: #f8fafc;
  font-size: 1.2rem;
  font-weight: 900;
  letter-spacing: -0.03em;
}

.panel-desc {
  color: #94a3b8;
  font-size: 0.92rem;
  line-height: 1.6;
}

.count-badge {
  background: rgba(99, 102, 241, 0.12);
  color: #c4b5fd;
  border: 1px solid rgba(129, 140, 248, 0.28);
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 0.82rem;
  font-weight: 850;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.url-textarea {
  min-height: 335px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(2, 6, 23, 0.86);
  color: #e5e7eb;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.9rem;
  line-height: 1.65;
  resize: vertical;
  padding: 16px;
}

.url-textarea::placeholder {
  color: #64748b;
}

.url-textarea:focus {
  background: rgba(2, 6, 23, 0.96);
  border-color: rgba(34, 211, 238, 0.75);
  box-shadow: 0 0 0 4px rgba(34, 211, 238, 0.12);
  color: #f8fafc;
}

.input-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #94a3b8;
  font-size: 0.82rem;
}

.btn-action {
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, #22d3ee, #38bdf8);
  color: #031018;
  font-weight: 900;
  padding: 13px 16px;
  box-shadow: 0 16px 32px rgba(34, 211, 238, 0.2);
}

.btn-action:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 20px 42px rgba(34, 211, 238, 0.28);
}

.btn-action:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.hint-box {
  display: flex;
  gap: 12px;
  background: rgba(234, 179, 8, 0.06);
  color: #cbd5e1;
  border: 1px solid rgba(234, 179, 8, 0.18);
  border-radius: 18px;
  padding: 14px;
  font-size: 0.87rem;
  line-height: 1.5;
}

.hint-icon {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  color: #facc15;
  font-weight: 900;
}

.summary-box {
  border-radius: 16px;
  padding: 13px 15px;
  font-size: 0.9rem;
  font-weight: 750;
}

.summary-success {
  background: rgba(22, 163, 74, 0.12);
  color: #86efac;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.summary-warning {
  background: rgba(202, 138, 4, 0.12);
  color: #fde68a;
  border: 1px solid rgba(234, 179, 8, 0.3);
}

.summary-danger {
  background: rgba(220, 38, 38, 0.12);
  color: #fca5a5;
  border: 1px solid rgba(248, 113, 113, 0.3);
}

.table-wrap {
  max-height: 455px;
  overflow: auto;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 18px;
}

.result-table {
  color: #e2e8f0;
  --bs-table-bg: transparent;
  --bs-table-hover-bg: rgba(30, 41, 59, 0.52);
}

.result-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #0f172a;
  color: #94a3b8;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 0.77rem;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 14px 16px;
}

.result-table tbody td {
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);
  padding: 14px 16px;
}

.result-table tbody tr:hover {
  background: rgba(30, 41, 59, 0.52);
}

.url-cell {
  max-width: 370px;
}

.url-main {
  display: block;
  color: #f8fafc;
  font-weight: 750;
  text-decoration: none;
}

.url-main:hover {
  color: #67e8f9;
}

.url-message {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 0.8rem;
}

.url-final {
  margin-top: 4px;
  color: #67e8f9;
  font-size: 0.8rem;
}

.url-time {
  margin-top: 4px;
  color: #64748b;
  font-size: 0.76rem;
}

.soft-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 0.72rem;
  font-weight: 850;
  line-height: 1;
  white-space: nowrap;
}

.badge-http {
  background: rgba(100, 116, 139, 0.14);
  color: #cbd5e1;
  border: 1px solid rgba(148, 163, 184, 0.22);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.badge-success {
  background: rgba(22, 163, 74, 0.12);
  color: #86efac;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.badge-warning {
  background: rgba(202, 138, 4, 0.12);
  color: #fde68a;
  border: 1px solid rgba(234, 179, 8, 0.3);
}

.badge-danger {
  background: rgba(220, 38, 38, 0.12);
  color: #fca5a5;
  border: 1px solid rgba(248, 113, 113, 0.3);
}

.badge-processing {
  background: rgba(37, 99, 235, 0.12);
  color: #93c5fd;
  border: 1px solid rgba(96, 165, 250, 0.28);
}

.badge-muted,
.badge-neutral {
  background: rgba(100, 116, 139, 0.14);
  color: #cbd5e1;
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.empty-state {
  height: 260px;
  text-align: center;
  color: #94a3b8;
  font-size: 0.95rem;
}

.empty-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  border-radius: 18px;
  background: rgba(13, 202, 240, 0.08);
  color: #67e8f9;
  font-size: 1.25rem;
  font-weight: 900;
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

.footer-note {
  color: #94a3b8;
  font-size: 0.87rem;
}

.footer-note span {
  display: block;
  color: #fde68a;
  margin-top: 4px;
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

@media (max-width: 991px) {
  .page-hero {
    padding: 22px;
  }

  .panel-card {
    padding: 18px;
  }

  .table-wrap {
    max-height: 520px;
  }

  .result-header {
    align-items: flex-start;
  }

  .count-badge {
    align-self: flex-start;
  }

  .input-meta {
    flex-direction: column;
  }
}
</style>