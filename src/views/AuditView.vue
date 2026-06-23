<template>
  <div class="index-page">
    <div class="container py-5" style="max-width: 1280px;">
      <!-- Hero -->
      <div class="hero-card mb-4">
        <div class="d-flex flex-column flex-lg-row justify-content-between align-items-start align-items-lg-center gap-3">
          <div>
            <div class="eyebrow mb-2">
              <span class="status-dot"></span>
              Google Index Intelligence
            </div>

            <h1 class="hero-title mb-2">
              Index Check Workspace
            </h1>

            <p class="hero-subtitle mb-0">
              Verify whether URLs are discoverable in Google Search using backend-powered
              <span class="text-info font-monospace">site:url</span> checks, HTTP diagnostics, noindex detection and server access signals.
            </p>
          </div>

          <div class="hero-badge">
            <i class="bi bi-shield-check me-2"></i>
            BACKEND MODE
          </div>
        </div>
      </div>

      <!-- Progress -->
      <div v-if="isChecking" class="progress-panel mb-4 animate-fade-in">
        <div class="d-flex justify-content-between align-items-center mb-2 small">
          <span class="text-info font-monospace">
            Running index scan: {{ progress }}%
          </span>

          <span class="text-muted text-uppercase font-monospace">
            Processing URLs...
          </span>
        </div>

        <div class="progress progress-shell">
          <div
            class="progress-bar bg-info progress-bar-striped progress-bar-animated"
            role="progressbar"
            :style="{ width: progress + '%' }"
          ></div>
        </div>
      </div>

      <!-- Summary -->
      <div v-if="indexResults.length > 0" class="summary-grid mb-4 animate-fade-in">
        <div class="summary-card">
          <span class="summary-label">Total URLs</span>
          <strong>{{ indexResults.length }}</strong>
        </div>

        <div class="summary-card success">
          <span class="summary-label">Indexed</span>
          <strong>{{ indexedCount }}</strong>
        </div>

        <div class="summary-card warning">
          <span class="summary-label">Not Found</span>
          <strong>{{ notIndexedCount }}</strong>
        </div>

        <div class="summary-card danger">
          <span class="summary-label">Google Blocked</span>
          <strong>{{ googleBlockedCount }}</strong>
        </div>
      </div>

      <div class="row g-4">
        <!-- Input -->
        <div class="col-12 col-lg-5">
          <div class="panel h-100">
            <div class="panel-header">
              <div>
                <h2 class="panel-title mb-1">URL Input</h2>
                <p class="panel-subtitle mb-0">
                  Paste one URL per line. The backend will check HTTP status, final URL, title, noindex, server blocking and Google index visibility.
                </p>
              </div>
            </div>

            <div class="panel-body d-flex flex-column">
              <label for="indexTextArea" class="form-label field-label">
                URLs to check
              </label>

              <textarea
                id="indexTextArea"
                v-model="urlListText"
                class="form-control custom-textarea"
                rows="13"
                placeholder="https://mecivietnam.com/article-example&#10;https://medium.com/@mecivietnam/post-example&#10;https://x.com/mecivietnam/status/example"
                :disabled="isChecking"
              ></textarea>

              <div class="input-meta mt-3">
                <span>
                  <i class="bi bi-list-ul me-1"></i>
                  {{ parsedUrlCount }} URLs detected
                </span>

                <span>
                  <i class="bi bi-clock-history me-1"></i>
                  Backend scan
                </span>
              </div>

              <button
                @click="runCheckIndex"
                class="btn scan-btn mt-4"
                :disabled="isChecking || !urlListText.trim()"
              >
                <span v-if="isChecking" class="spinner-border spinner-border-sm me-2"></span>
                <i v-else class="bi bi-search me-2"></i>
                {{ isChecking ? 'Scanning...' : 'Run Index Check' }}
              </button>

              <div class="notice-box mt-3">
                <div class="notice-icon">
                  <i class="bi bi-info-circle"></i>
                </div>

                <div>
                  <strong>How it works</strong>
                  <p class="mb-0">
                    This tool checks Google Search visibility through backend requests using
                    <span class="font-monospace">site:url</span>. If Google blocks automated checking, the result will be marked as
                    <strong>Google Blocked</strong>, not as Not Indexed.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Results -->
        <div class="col-12 col-lg-7">
          <div class="panel h-100">
            <div class="panel-header d-flex justify-content-between align-items-center">
              <div>
                <h2 class="panel-title mb-1">Index Results</h2>
                <p class="panel-subtitle mb-0">
                  Live diagnostics returned from your Spring Boot backend.
                </p>
              </div>

              <span class="result-count">
                {{ indexResults.length }} URLs
              </span>
            </div>

            <div class="table-responsive result-table-wrap">
              <table class="table table-dark table-hover align-middle mb-0 result-table">
                <thead>
                  <tr>
                    <th scope="col" class="ps-4" style="width: 39%;">URL</th>
                    <th scope="col" class="text-center" style="width: 13%;">HTTP</th>
                    <th scope="col" class="text-center" style="width: 20%;">Index</th>
                    <th scope="col" class="text-end pe-4" style="width: 28%;">Signal</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-if="indexResults.length === 0">
                    <td colspan="4" class="empty-state">
                      <div class="empty-icon">
                        <i class="bi bi-radar"></i>
                      </div>

                      <h3>No scan yet</h3>
                      <p>
                        Paste URLs on the left and run an index check to see Google visibility signals here.
                      </p>
                    </td>
                  </tr>

                  <tr v-for="link in indexResults" :key="link.url">
                    <td class="ps-4 url-cell" :title="link.url">
                      <a :href="link.url" target="_blank" rel="noopener" class="url-link">
                        <div class="text-truncate">
                          {{ link.url }}
                          <i class="bi bi-box-arrow-up-right small ms-1 text-muted"></i>
                        </div>
                      </a>

                      <div
                        v-if="link.finalUrl && link.finalUrl !== link.url"
                        class="small text-info mt-1 text-truncate"
                        :title="link.finalUrl"
                      >
                        Final: {{ link.finalUrl }}
                      </div>

                      <div v-if="link.title" class="small text-muted mt-1 text-truncate" :title="link.title">
                        Title: {{ link.title }}
                      </div>

                      <div v-if="link.checkedAt" class="small text-muted mt-1">
                        Checked at {{ link.checkedAt }}
                      </div>
                    </td>

                    <td class="text-center">
                      <span class="smart-badge" :class="getHttpClass(link.httpStatus)">
                        {{ link.httpStatus || 'N/A' }}
                      </span>
                    </td>

                    <td class="text-center">
                      <span class="smart-badge text-uppercase" :class="getIndexClass(link.indexStatus)">
                        {{ getIndexLabel(link.indexStatus) }}
                      </span>
                    </td>

                    <td class="text-end pe-4">
                      <span class="small signal-text" :class="getNoteClass(link)">
                        {{ link.note || getDefaultNote(link) }}
                      </span>

                      <div class="flag-row mt-2">
                        <span v-if="link.noindex" class="flag warning">noindex</span>
                        <span v-if="link.blocked" class="flag danger">blocked</span>
                        <span v-if="link.loginWall" class="flag warning">login wall</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="indexResults.length > 0" class="panel-footer">
              <span>Total: {{ indexResults.length }}</span>
              <span>Indexed: {{ indexedCount }}</span>
              <span>Not found: {{ notIndexedCount }}</span>
              <span>Need review: {{ reviewCount }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="footer-note mt-4">
        <i class="bi bi-lightning-charge me-1"></i>
        Tip: Use smaller batches for more reliable Google search checking. Large automated batches may trigger temporary Google protection.
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import axios from 'axios'

const API_BASE_URL = 'https://seo-tool-backend-phw2.onrender.com/api/seo'

const urlListText = ref('')
const indexResults = ref([])
const isChecking = ref(false)
const progress = ref(0)

const parsedUrlCount = computed(() => {
  return parseUrls().length
})

const indexedCount = computed(() => {
  return indexResults.value.filter(item => item.indexStatus === 'INDEXED').length
})

const notIndexedCount = computed(() => {
  return indexResults.value.filter(item => item.indexStatus === 'NOT_INDEXED').length
})

const googleBlockedCount = computed(() => {
  return indexResults.value.filter(item => item.indexStatus === 'GOOGLE_BLOCK').length
})

const reviewCount = computed(() => {
  return indexResults.value.filter(item =>
    !['INDEXED', 'NOT_INDEXED'].includes(item.indexStatus)
  ).length
})

function parseUrls() {
  return urlListText.value
    .split('\n')
    .map(url => url.trim())
    .filter(url => url !== '')
}

const runCheckIndex = async () => {
  const urls = parseUrls()

  if (urls.length === 0) {
    alert('Please enter at least one URL.')
    return
  }

  isChecking.value = true
  progress.value = 8

  indexResults.value = urls.map(url => ({
    url,
    finalUrl: url,
    httpStatus: '...',
    indexed: null,
    indexStatus: 'PENDING',
    note: 'Waiting for backend scan...',
    title: '',
    noindex: false,
    blocked: false,
    loginWall: false,
    checkedAt: ''
  }))

  const progressTimer = setInterval(() => {
    if (progress.value < 92) {
      progress.value += 7
    }
  }, 280)

  try {
    const response = await axios.post(`${API_BASE_URL}/check-index`, {
      urls
    })

    const data = response.data

    if (Array.isArray(data)) {
      indexResults.value = data.map(item => ({
        url: item.url || '',
        finalUrl: item.finalUrl || item.url || '',
        httpStatus: item.httpStatus || 'ERROR',
        indexed: item.indexed ?? null,
        indexStatus: item.indexStatus || 'UNKNOWN',
        note: item.note || '',
        title: item.title || '',
        noindex: Boolean(item.noindex),
        blocked: Boolean(item.blocked),
        loginWall: Boolean(item.loginWall),
        checkedAt: item.checkedAt || ''
      }))
    } else {
      indexResults.value = urls.map(url => ({
        url,
        finalUrl: url,
        httpStatus: 'ERROR',
        indexed: null,
        indexStatus: 'ERROR',
        note: 'Backend returned an unexpected response format.',
        title: '',
        noindex: false,
        blocked: false,
        loginWall: false,
        checkedAt: ''
      }))
    }

    progress.value = 100
  } catch (error) {
    indexResults.value = urls.map(url => ({
      url,
      finalUrl: url,
      httpStatus: 'ERROR',
      indexed: null,
      indexStatus: 'ERROR',
      note: 'Could not connect to backend. Check Render deployment, CORS or /api/seo/check-index endpoint.',
      title: '',
      noindex: false,
      blocked: false,
      loginWall: false,
      checkedAt: ''
    }))

    progress.value = 100
    alert('Could not connect to backend. Please check Render deployment or API endpoint.')
  } finally {
    clearInterval(progressTimer)

    setTimeout(() => {
      isChecking.value = false
      progress.value = 0
    }, 500)
  }
}

function getHttpClass(status) {
  if (status === '...') {
    return 'badge-neutral'
  }

  if (status === 'ERROR' || status === 'INVALID') {
    return 'badge-danger'
  }

  const code = Number(status)

  if (Number.isNaN(code)) {
    return 'badge-neutral'
  }

  if (code >= 200 && code < 300) {
    return 'badge-success'
  }

  if (code >= 300 && code < 400) {
    return 'badge-warning'
  }

  return 'badge-danger'
}

function getIndexClass(status) {
  if (status === 'PENDING') {
    return 'badge-neutral'
  }

  if (status === 'INDEXED') {
    return 'badge-success'
  }

  if (status === 'NOT_INDEXED') {
    return 'badge-warning'
  }

  if (status === 'GOOGLE_BLOCK') {
    return 'badge-danger'
  }

  if (status === 'ERROR') {
    return 'badge-danger'
  }

  return 'badge-warning'
}

function getIndexLabel(status) {
  if (status === 'PENDING') {
    return 'Pending'
  }

  if (status === 'INDEXED') {
    return 'Indexed'
  }

  if (status === 'NOT_INDEXED') {
    return 'Not Found'
  }

  if (status === 'GOOGLE_BLOCK') {
    return 'Google Blocked'
  }

  if (status === 'ERROR') {
    return 'Error'
  }

  return 'Review'
}

function getDefaultNote(link) {
  if (link.indexStatus === 'INDEXED') {
    return 'URL was found in Google Search results.'
  }

  if (link.indexStatus === 'NOT_INDEXED') {
    return 'URL was not found through site:url search.'
  }

  if (link.indexStatus === 'GOOGLE_BLOCK') {
    return 'Google blocked the automated search request.'
  }

  if (link.indexStatus === 'ERROR') {
    return 'The scan could not be completed.'
  }

  return 'Manual review may be required.'
}

function getNoteClass(link) {
  if (link.indexStatus === 'INDEXED') {
    return 'text-success'
  }

  if (link.indexStatus === 'NOT_INDEXED') {
    return 'text-warning'
  }

  if (link.indexStatus === 'GOOGLE_BLOCK' || link.indexStatus === 'ERROR') {
    return 'text-danger'
  }

  return 'text-muted'
}
</script>

<style scoped>
.index-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(13, 202, 240, 0.16), transparent 32%),
    radial-gradient(circle at top right, rgba(111, 66, 193, 0.14), transparent 30%),
    linear-gradient(180deg, #070b12 0%, #0b0f17 42%, #07080d 100%);
  color: #f8f9fa;
}

.hero-card,
.panel,
.progress-panel {
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.78);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.32);
  border-radius: 24px;
}

.hero-card {
  padding: 30px;
  position: relative;
  overflow: hidden;
}

.hero-card::after {
  content: "";
  position: absolute;
  width: 260px;
  height: 260px;
  right: -90px;
  top: -100px;
  border-radius: 999px;
  background: rgba(13, 202, 240, 0.14);
  filter: blur(6px);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #67e8f9;
  font-size: 0.78rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-weight: 700;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 18px rgba(34, 197, 94, 0.9);
}

.hero-title {
  font-size: clamp(2rem, 4vw, 3.3rem);
  letter-spacing: -0.05em;
  font-weight: 850;
  line-height: 1;
}

.hero-subtitle {
  color: #94a3b8;
  max-width: 760px;
  font-size: 0.98rem;
}

.hero-badge {
  border: 1px solid rgba(13, 202, 240, 0.28);
  background: rgba(8, 145, 178, 0.12);
  color: #67e8f9;
  border-radius: 999px;
  padding: 10px 16px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.82rem;
  font-weight: 700;
  white-space: nowrap;
  z-index: 1;
}

.progress-panel {
  padding: 18px 22px;
}

.progress-shell {
  height: 7px;
  background: #020617;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 999px;
  overflow: hidden;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.72);
  border-radius: 18px;
  padding: 16px 18px;
}

.summary-card strong {
  display: block;
  margin-top: 4px;
  font-size: 1.7rem;
  line-height: 1;
}

.summary-label {
  color: #94a3b8;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-card.success {
  border-color: rgba(34, 197, 94, 0.28);
}

.summary-card.warning {
  border-color: rgba(234, 179, 8, 0.3);
}

.summary-card.danger {
  border-color: rgba(248, 113, 113, 0.3);
}

.panel {
  overflow: hidden;
}

.panel-header {
  padding: 22px 24px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(2, 6, 23, 0.26);
}

.panel-title {
  font-size: 1.08rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.panel-subtitle {
  color: #94a3b8;
  font-size: 0.86rem;
}

.panel-body {
  padding: 24px;
}

.panel-footer {
  padding: 14px 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  color: #94a3b8;
  font-size: 0.82rem;
}

.field-label {
  color: #67e8f9;
  font-weight: 700;
  font-size: 0.88rem;
}

.custom-textarea {
  background: rgba(2, 6, 23, 0.86) !important;
  border: 1px solid rgba(148, 163, 184, 0.2) !important;
  color: #e5e7eb !important;
  border-radius: 18px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9rem;
  resize: none;
  padding: 16px;
}

.custom-textarea:focus {
  border-color: rgba(13, 202, 240, 0.75) !important;
  box-shadow: 0 0 0 0.25rem rgba(13, 202, 240, 0.13);
}

.input-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #94a3b8;
  font-size: 0.82rem;
}

.scan-btn {
  width: 100%;
  border: 0;
  border-radius: 16px;
  color: #031018;
  background: linear-gradient(135deg, #22d3ee, #38bdf8);
  font-weight: 850;
  padding: 12px 16px;
  box-shadow: 0 14px 32px rgba(56, 189, 248, 0.22);
}

.scan-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 18px 38px rgba(56, 189, 248, 0.3);
}

.scan-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.notice-box {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(234, 179, 8, 0.18);
  background: rgba(234, 179, 8, 0.06);
  border-radius: 16px;
  color: #cbd5e1;
  font-size: 0.82rem;
}

.notice-icon {
  color: #facc15;
  font-size: 1rem;
  padding-top: 1px;
}

.result-count {
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.8);
  color: #cbd5e1;
  padding: 7px 11px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-family: 'Courier New', Courier, monospace;
}

.result-table-wrap {
  max-height: 540px;
}

.result-table {
  --bs-table-bg: transparent;
  --bs-table-hover-bg: rgba(30, 41, 59, 0.52);
  color: #e5e7eb;
}

.result-table thead {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #0f172a;
}

.result-table th {
  padding: 14px 16px;
  color: #94a3b8 !important;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.result-table td {
  padding: 16px;
  border-color: rgba(148, 163, 184, 0.09);
  font-size: 0.88rem;
}

.url-cell {
  max-width: 380px;
}

.url-link {
  color: #f8fafc;
  text-decoration: none;
  transition: color 0.15s ease;
}

.url-link:hover {
  color: #67e8f9;
}

.smart-badge {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-width: 76px;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 0.72rem;
  font-weight: 800;
  font-family: 'Courier New', Courier, monospace;
  border: 1px solid transparent;
}

.badge-success {
  color: #86efac;
  background: rgba(22, 163, 74, 0.12);
  border-color: rgba(34, 197, 94, 0.3);
}

.badge-warning {
  color: #fde68a;
  background: rgba(202, 138, 4, 0.12);
  border-color: rgba(234, 179, 8, 0.3);
}

.badge-danger {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.12);
  border-color: rgba(248, 113, 113, 0.3);
}

.badge-neutral {
  color: #cbd5e1;
  background: rgba(100, 116, 139, 0.14);
  border-color: rgba(148, 163, 184, 0.22);
}

.signal-text {
  display: inline-block;
  max-width: 260px;
}

.flag-row {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.flag {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 7px;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
}

.flag.warning {
  color: #fde68a;
  background: rgba(202, 138, 4, 0.14);
}

.flag.danger {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.14);
}

.empty-state {
  text-align: center;
  padding: 70px 24px !important;
  color: #94a3b8;
}

.empty-icon {
  width: 58px;
  height: 58px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  margin-bottom: 16px;
  background: rgba(13, 202, 240, 0.08);
  color: #67e8f9;
  font-size: 1.45rem;
}

.empty-state h3 {
  font-size: 1.05rem;
  color: #e5e7eb;
  margin-bottom: 6px;
}

.empty-state p {
  margin: 0 auto;
  max-width: 380px;
  font-size: 0.88rem;
}

.footer-note {
  color: #94a3b8;
  font-size: 0.84rem;
  text-align: center;
}

.animate-fade-in {
  animation: fadeIn 0.28s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 991px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-card {
    padding: 24px;
  }

  .result-table-wrap {
    max-height: 480px;
  }
}

@media (max-width: 575px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .input-meta {
    flex-direction: column;
  }

  .panel-footer {
    flex-direction: column;
    gap: 6px;
  }
}
</style>