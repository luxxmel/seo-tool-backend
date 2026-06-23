<template>
  <v-app>
    <v-app-bar
      app
      elevation="0"
      class="app-navbar"
      height="72"
    >
      <div class="nav-shell">
        <router-link to="/" class="brand-link">
          <div class="brand-mark">
            <span></span>
          </div>

          <div class="brand-text">
            <strong>IndexFlow</strong>
            <small>SEO Operations</small>
          </div>
        </router-link>

        <v-spacer></v-spacer>

        <div class="nav-actions">
          <v-btn
            to="/"
            variant="text"
            class="nav-btn"
            exact
          >
            <v-icon size="18" class="me-2">mdi-view-dashboard-outline</v-icon>
            Dashboard
          </v-btn>

          <v-btn
            to="/indexing"
            variant="text"
            class="nav-btn"
          >
            <v-icon size="18" class="me-2">mdi-rocket-launch-outline</v-icon>
            Indexing
          </v-btn>

          <v-btn
            to="/audit"
            variant="text"
            class="nav-btn"
          >
            <v-icon size="18" class="me-2">mdi-shield-search</v-icon>
            Audit
          </v-btn>
        </div>
      </div>
    </v-app-bar>

    <v-main class="app-main">
      <router-view />
    </v-main>
  </v-app>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');

:root {
  color-scheme: dark;
  --app-bg: #070b12;
  --app-bg-soft: #0b1120;
  --app-border: rgba(148, 163, 184, 0.14);
  --app-text: #f8fafc;
  --app-muted: #94a3b8;
  --app-accent: #22d3ee;
  --app-accent-2: #38bdf8;
  --app-success: #22c55e;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
  margin: 0;
  font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: var(--app-bg);
}

body {
  color: var(--app-text);
  overflow-x: hidden;
}

.app-navbar {
  background:
    linear-gradient(180deg, rgba(7, 11, 18, 0.92), rgba(7, 11, 18, 0.72)) !important;
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--app-border) !important;
}

.nav-shell {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 28px;
  display: flex;
  align-items: center;
}

.brand-link {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--app-text);
  text-decoration: none;
}

.brand-mark {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 255, 255, 0.5), transparent 22%),
    linear-gradient(135deg, var(--app-accent), var(--app-accent-2));
  box-shadow: 0 16px 34px rgba(34, 211, 238, 0.22);
}

.brand-mark span {
  width: 18px;
  height: 18px;
  border: 3px solid rgba(3, 7, 18, 0.88);
  border-radius: 999px;
  position: relative;
}

.brand-mark span::after {
  content: "";
  position: absolute;
  width: 9px;
  height: 3px;
  right: -8px;
  bottom: -3px;
  border-radius: 999px;
  background: rgba(3, 7, 18, 0.88);
  transform: rotate(45deg);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-text strong {
  font-size: 1.02rem;
  letter-spacing: -0.03em;
  font-weight: 800;
}

.brand-text small {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(15, 23, 42, 0.52);
  border-radius: 999px;
}

.nav-btn {
  color: var(--app-muted) !important;
  border-radius: 999px !important;
  padding: 0 14px !important;
  height: 40px !important;
  font-size: 0.82rem !important;
  font-weight: 700 !important;
  letter-spacing: 0 !important;
  text-transform: none !important;
}

.nav-btn:hover {
  color: var(--app-text) !important;
  background: rgba(148, 163, 184, 0.1) !important;
}

.nav-btn.router-link-active {
  color: #041018 !important;
  background: linear-gradient(135deg, var(--app-accent), var(--app-accent-2)) !important;
  box-shadow: 0 10px 26px rgba(34, 211, 238, 0.2);
}

.app-main {
  min-height: 100vh;
  padding-top: 72px;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.14), transparent 32%),
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.11), transparent 30%),
    linear-gradient(180deg, #070b12 0%, #0b1120 42%, #07080d 100%);
}

.glass-card {
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(18px);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 24px;
  box-shadow: 0 22px 70px rgba(0, 0, 0, 0.28);
}

.v-btn {
  font-family: 'Inter', system-ui, sans-serif !important;
}

@media (max-width: 768px) {
  .nav-shell {
    padding: 0 16px;
  }

  .brand-text small {
    display: none;
  }

  .nav-actions {
    gap: 2px;
    padding: 4px;
  }

  .nav-btn {
    min-width: 42px !important;
    padding: 0 10px !important;
  }

  .nav-btn .v-btn__content {
    font-size: 0;
  }

  .nav-btn .v-icon {
    margin-inline-end: 0 !important;
  }
}

@media (max-width: 520px) {
  .brand-text strong {
    display: none;
  }

  .brand-mark {
    width: 38px;
    height: 38px;
  }
}
</style>