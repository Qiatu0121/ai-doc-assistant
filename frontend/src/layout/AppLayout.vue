<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const pageTitle = computed(() => route.meta.title || 'AI 文档助手')
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <el-icon :size="22"><Reading /></el-icon>
        </div>
        <div class="brand-copy">
          <strong>AI 文档助手</strong>
          <span>Knowledge · Agent</span>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item index="/documents">
          <el-icon><FolderOpened /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><ChatDotRound /></el-icon>
          <span>Agent 问答</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">
        <span class="status-dot"></span>
        <div>
          <strong>本地工作区</strong>
          <small>V1 · 单用户模式</small>
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">AI DOC ASSISTANT</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="api-badge">
          <span class="api-dot"></span>
          后端 /api
        </div>
      </header>

      <div class="page-body">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #f4f6fb;
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex: 0 0 248px;
  flex-direction: column;
  width: 248px;
  height: 100vh;
  padding: 28px 16px 20px;
  color: #dbe4f3;
  background:
    radial-gradient(circle at 20% 0%, rgba(99, 102, 241, 0.28), transparent 36%),
    linear-gradient(180deg, #111a2f 0%, #0b1222 100%);
  box-sizing: border-box;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 10px 28px;
}

.brand-mark {
  display: grid;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 13px;
  box-shadow: 0 12px 28px rgba(79, 70, 229, 0.35);
  place-items: center;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.brand-copy strong {
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy span {
  font-size: 10px;
  color: #7f8ea8;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.side-menu {
  border-right: 0;
  background: transparent;
}

.side-menu :deep(.el-menu-item) {
  height: 46px;
  margin-bottom: 6px;
  padding: 0 14px !important;
  color: #93a1b8;
  border-radius: 11px;
  font-size: 14px;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.side-menu :deep(.el-menu-item .el-icon) {
  margin-right: 11px;
  font-size: 18px;
}

.side-menu :deep(.el-menu-item:hover) {
  color: #eef2ff;
  background: rgba(148, 163, 184, 0.1);
}

.side-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.9), rgba(124, 58, 237, 0.7));
  box-shadow: 0 10px 24px rgba(67, 56, 202, 0.28);
}

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding: 14px 12px;
  background: rgba(148, 163, 184, 0.07);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
}

.status-dot,
.api-dot {
  display: inline-block;
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  background: #34d399;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.12);
}

.sidebar-foot div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-foot strong {
  font-size: 12px;
  font-weight: 500;
  color: #cbd5e1;
}

.sidebar-foot small {
  font-size: 10px;
  color: #64748b;
}

.main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 30px 36px 18px;
  box-sizing: border-box;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 700;
  color: #8b95ab;
  letter-spacing: 0.18em;
}

.topbar h1 {
  margin: 0;
  font-size: 27px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.035em;
}

.api-badge {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 13px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  color: #64748b;
  background: #fff;
  border: 1px solid #e6eaf2;
  border-radius: 999px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.api-dot {
  width: 6px;
  height: 6px;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.14);
}

.page-body {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 8px 36px 48px;
  box-sizing: border-box;
}

@media (max-width: 900px) {
  .app-shell {
    flex-direction: column;
  }

  .sidebar {
    position: static;
    flex: none;
    flex-direction: row;
    align-items: center;
    width: 100%;
    height: auto;
    padding: 12px 16px;
  }

  .brand {
    padding: 0;
  }

  .brand-mark {
    width: 36px;
    height: 36px;
    border-radius: 11px;
  }

  .brand-copy span,
  .sidebar-foot {
    display: none;
  }

  .side-menu {
    display: flex;
    margin-left: auto;
  }

  .side-menu :deep(.el-menu-item) {
    height: 38px;
    margin: 0 0 0 4px;
    padding: 0 12px !important;
  }

  .side-menu :deep(.el-menu-item .el-icon) {
    margin-right: 6px;
    font-size: 16px;
  }

  .topbar {
    padding: 24px 20px 14px;
  }

  .page-body {
    padding: 6px 20px 32px;
  }
}

@media (max-width: 560px) {
  .brand-copy,
  .api-badge {
    display: none;
  }

  .topbar h1 {
    font-size: 23px;
  }

  .topbar {
    padding: 20px 16px 12px;
  }

  .page-body {
    padding: 4px 16px 28px;
  }

  .side-menu :deep(.el-menu-item) {
    padding: 0 10px !important;
    font-size: 13px;
  }
}
</style>
