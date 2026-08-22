/**
 * 进度追踪
 */
const ProgressTracker = {
  init() {},
  renderOverview(user) {
    const el = document.getElementById('progress-overview');
    const today = new Date().toISOString().slice(0, 10);
    const todayXP = user.dailyXP?.[today] || 0;
    const weekXP = this.getWeekXP(user);
    const totalWords = Object.values(user.vocab || {}).reduce((acc, lang) => acc + Object.values(lang).filter(v => v.known).length, 0);
    const totalLessons = Object.values(user.progress || {}).reduce((acc, lang) => {
      Object.values(lang || {}).forEach(level => {
        Object.values(level || {}).forEach(l => { if (l?.completed) acc++; });
      });
      return acc;
    }, 0);

    el.innerHTML = `
      <div class="stat-card">
        <div class="stat-value">${user.xp}</div>
        <div class="stat-label">总经验值 XP</div>
        <div class="stat-trend up">Lv.${user.level}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">${todayXP}</div>
        <div class="stat-label">今日 XP</div>
        <div class="stat-trend up">🔥 连续 ${computeStreak(user)} 天</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">${weekXP}</div>
        <div class="stat-label">本周 XP</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">${totalWords}</div>
        <div class="stat-label">掌握单词</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">${totalLessons}</div>
        <div class="stat-label">完成课程</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">${(user.badges?.length || 0)}</div>
        <div class="stat-label">解锁徽章</div>
      </div>
    `;

    // 总经验进度
    const nextLevelXP = user.level * 100;
    const currentLevelXP = (user.level - 1) * 100;
    const progress = Math.min(100, Math.round(((user.xp - currentLevelXP) / 100) * 100));

    const detail = document.getElementById('progress-detail');
    const history = (user.studyHistory || []).slice(-10).reverse();
    const historyHTML = history.length ? history.map(h => `
      <div class="progress-row">
        <div class="progress-row-info">
          <span class="progress-row-icon">📘</span>
          <div>
            <div><strong>${h.reason}</strong></div>
            <div style="font-size:12px;color:var(--text-muted)">${new Date(h.ts).toLocaleString('zh-CN')}</div>
          </div>
        </div>
        <div class="stat-trend up">+${h.xp} XP</div>
      </div>
    `).join('') : '<div style="padding:20px;text-align:center;color:var(--text-muted)">暂无学习记录，开始你的第一次学习吧！</div>';

    detail.innerHTML = `
      <h3 style="margin-bottom:16px">📈 等级进度</h3>
      <div style="display:flex;justify-content:space-between;font-size:13px;color:var(--text-muted);margin-bottom:6px">
        <span>Lv.${user.level}</span><span>${progress}%</span>
      </div>
      <div class="progress-bar"><div class="progress-bar-fill" style="width:${progress}%"></div></div>
      <p style="font-size:12px;color:var(--text-muted);margin-bottom:20px">再获得 ${nextLevelXP - user.xp} XP 即可升级到 Lv.${user.level + 1}</p>
      <h3 style="margin:16px 0">📝 最近学习记录</h3>
      ${historyHTML}
    `;
  },
  getWeekXP(user) {
    const today = new Date();
    let total = 0;
    for (let i = 0; i < 7; i++) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const key = d.toISOString().slice(0, 10);
      total += (user.dailyXP?.[key] || 0);
    }
    return total;
  },
};
