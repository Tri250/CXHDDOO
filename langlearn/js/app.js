/**
 * 主应用控制器：路由、渲染、交互
 */
const App = {
  currentRoute: 'auth',
  lessonState: null, // 当前打开的课程

  init() {
    Auth.init();
    Community.init();
    // 全局导航事件委托
    document.addEventListener('click', (e) => {
      const route = e.target.closest('[data-route]')?.dataset.route;
      if (route) {
        e.preventDefault();
        this.navigate(route);
        this.render();
      }
    });
    // Modal
    document.getElementById('modal-close').addEventListener('click', () => {
      document.getElementById('modal').classList.add('hidden');
    });
    // 键盘 ESC 关闭 modal
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') document.getElementById('modal').classList.add('hidden');
    });
    // 首次渲染
    this.render();
  },

  navigate(route) {
    this.currentRoute = route;
    // 未登录强制回 auth（home 除外？统一检查）
    const protectedRoutes = ['home', 'courses', 'practice', 'progress', 'community', 'achievements', 'profile'];
    const user = State.getCurrentUser();
    if (protectedRoutes.includes(route) && !user) {
      this.currentRoute = 'auth';
    }
    if (route === 'auth' && user) {
      this.currentRoute = 'home';
    }
  },

  render() {
    const user = State.getCurrentUser();
    const header = document.getElementById('app-header');
    // 视图切换
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    const view = document.getElementById('view-' + this.currentRoute);
    if (view) view.classList.add('active');

    if (!user) {
      header.classList.add('hidden');
      return;
    }
    header.classList.remove('hidden');

    // 顶部用户信息
    document.getElementById('user-chip').textContent = '👤 ' + user.username;
    // 导航高亮
    document.querySelectorAll('.nav-link').forEach(a => {
      a.classList.toggle('active', a.dataset.route === this.currentRoute);
    });

    // 各视图渲染
    switch (this.currentRoute) {
      case 'home': this.renderHome(user); break;
      case 'courses': this.renderCourses(user); break;
      case 'practice': this.renderPractice(); break;
      case 'progress': ProgressTracker.renderOverview(user); Recommender.renderRecommendations('personal-list', user); break;
      case 'community': Community.renderPosts(); Community.renderLeaderboard(); break;
      case 'achievements': this.renderAchievements(user); break;
      case 'profile': this.renderProfile(user); break;
    }
  },

  /* ============ 首页 ============ */
  renderHome(user) {
    // Hero stats
    const todayXP = user.dailyXP?.[new Date().toISOString().slice(0, 10)] || 0;
    document.getElementById('hero-stats').innerHTML = `
      <div><div class="num">Lv.${user.level}</div><div class="label">当前等级</div></div>
      <div><div class="num">${user.xp}</div><div class="label">总 XP</div></div>
      <div><div class="num">${todayXP}</div><div class="label">今日 XP</div></div>
      <div><div class="num">${computeStreak(user)}🔥</div><div class="label">连击天数</div></div>
    `;

    // 继续学习
    const continueList = document.getElementById('continue-list');
    const progress = user.progress || {};
    const currentLang = user.currentLanguage || 'en';
    const currentLevel = user.currentLevel || 'A1';
    const lessons = COURSES[currentLang]?.[currentLevel] || [];
    const userProg = progress[currentLang]?.[currentLevel] || {};
    const pending = lessons.filter(l => !userProg[l.id]?.completed);
    const continueItems = (pending.length ? pending : lessons).slice(0, 3);
    if (!continueItems.length) {
      continueList.innerHTML = '<div style="grid-column:1/-1;color:var(--text-muted);text-align:center;padding:20px">暂无待学课程</div>';
    } else {
      continueList.innerHTML = continueItems.map(l => {
        const progData = userProg[l.id] || {};
        const pct = progData.score ? progData.score : 0;
        return `
          <div class="card" data-continue='${JSON.stringify({ lang: currentLang, level: currentLevel, id: l.id }).replace(/'/g, "&apos;")}'>
            <div class="card-icon">📗</div>
            <div class="card-title">${l.title}</div>
            <div class="card-desc">${LANGUAGES[currentLang].flag} ${currentLevel} · ${l.desc}</div>
            <div class="card-meta">
              <span>📖 ${l.vocab} 词 · ✏️ ${l.exercises} 练</span>
              <span>进度 ${pct}%</span>
            </div>
            <div class="card-progress" style="width:${pct}%"></div>
          </div>
        `;
      }).join('');
      continueList.querySelectorAll('.card').forEach(card => {
        card.addEventListener('click', () => {
          const data = JSON.parse(card.dataset.continue);
          this.openLesson(data.lang, data.level, data.id);
        });
      });
    }

    // 推荐
    Recommender.renderRecommendations('recommend-list', user);
  },

  /* ============ 课程选择 ============ */
  renderCourses(user) {
    const tabsEl = document.getElementById('lang-tabs');
    tabsEl.innerHTML = Object.values(LANGUAGES).map(l => `
      <div class="lang-tab ${l.code === user.currentLanguage ? 'active' : ''}" data-lang="${l.code}">
        ${l.flag} ${l.nativeName}
      </div>
    `).join('');
    tabsEl.querySelectorAll('.lang-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        user.currentLanguage = tab.dataset.lang;
        State.setCurrentUser(user);
        State.trackLanguage(user.currentLanguage);
        this.renderCourses(user);
      });
    });

    // 等级选择
    const levelsEl = document.getElementById('levels-list');
    levelsEl.innerHTML = LEVELS.map(l =>
      `<button class="level-btn ${l === user.currentLevel ? 'active' : ''}" data-level="${l}">${l}</button>`
    ).join('');
    levelsEl.querySelectorAll('.level-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        user.currentLevel = btn.dataset.level;
        State.setCurrentUser(user);
        this.renderCourses(user);
      });
    });

    // 课程列表
    const lessonsEl = document.getElementById('lessons-list');
    const lessons = COURSES[user.currentLanguage]?.[user.currentLevel] || [];
    const prog = user.progress?.[user.currentLanguage]?.[user.currentLevel] || {};
    lessonsEl.innerHTML = lessons.map((l, i) => {
      const completed = prog[l.id]?.completed;
      const score = prog[l.id]?.score || 0;
      return `
        <div class="lesson-card ${completed ? 'completed' : ''}" data-lesson="${l.id}">
          <div class="lesson-num">${completed ? '✓' : i + 1}</div>
          <h3>${l.title}</h3>
          <div class="lesson-desc">${l.desc}</div>
          <div style="font-size:12px;color:var(--text-muted);margin-bottom:8px">
            📖 ${l.vocab} 词 · ✏️ ${l.exercises} 练习 · ${completed ? '得分 ' + score : '未开始'}
          </div>
          <div class="lesson-progress-bar"><div class="lesson-progress-fill" style="width:${score}%"></div></div>
        </div>
      `;
    }).join('');
    lessonsEl.querySelectorAll('.lesson-card').forEach(card => {
      card.addEventListener('click', () => this.openLesson(user.currentLanguage, user.currentLevel, card.dataset.lesson));
    });
  },

  /* ============ 打开课程：学习 + 练习 ============ */
  openLesson(lang, level, lessonId) {
    const lessons = COURSES[lang][level];
    const lesson = lessons.find(l => l.id === lessonId);
    if (!lesson) return;
    const user = State.getCurrentUser();
    user.currentLanguage = lang;
    user.currentLevel = level;
    user.currentLessonId = lessonId;
    State.setCurrentUser(user);
    State.trackLanguage(lang);

    // 打开课程模态
    const modal = document.getElementById('modal');
    const body = document.getElementById('modal-body');
    const vocab = VOCAB[lang]?.[level] || [];
    const grammarList = GRAMMAR[lang]?.filter(g => g.level === level) || [];
    const sentences = SENTENCES[lang] || [];

    body.innerHTML = `
      <div style="text-align:center;margin-bottom:24px">
        <div style="font-size:48px">${LANGUAGES[lang].flag}</div>
        <h2 style="font-size:22px;margin:8px 0">${lesson.title}</h2>
        <p style="color:var(--text-muted);font-size:14px">${level} · ${lesson.desc}</p>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:24px">
        <button class="btn btn-primary" data-lesson-action="words">📖 开始单词 (${vocab.length})</button>
        <button class="btn btn-outline" data-lesson-action="grammar">✏️ 语法练习 (${grammarList.length})</button>
        <button class="btn btn-outline" data-lesson-action="speaking">🎙️ 口语跟读 (${sentences.length})</button>
      </div>
      <div style="background:var(--bg);border-radius:12px;padding:16px;margin-bottom:16px">
        <div style="font-size:13px;font-weight:600;margin-bottom:8px">📚 本课程学习目标</div>
        <div style="font-size:13px;color:var(--text-muted)">
          · 掌握 ${lesson.vocab} 个核心词汇<br/>
          · 完成 ${lesson.exercises} 个互动练习<br/>
          · 建立 ${LANGUAGES[lang].nativeName} 的系统知识框架
        </div>
      </div>
      <button class="btn btn-success btn-block" data-lesson-action="complete">✅ 标记已完成</button>
    `;

    body.querySelectorAll('[data-lesson-action]').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = btn.dataset.lessonAction;
        if (action === 'complete') {
          this.completeLesson(lang, level, lessonId, lesson);
          modal.classList.add('hidden');
        } else {
          modal.classList.add('hidden');
          Practice.open(lang, action);
        }
      });
    });

    modal.classList.remove('hidden');
  },

  completeLesson(lang, level, lessonId, lesson) {
    State.updateProgress(lang, level, lessonId, { completed: true, score: 100, completedAt: Date.now() });
    const user = State.getCurrentUser();
    user.stats.lessonsCompleted = (user.stats.lessonsCompleted || 0) + 1;
    user.stats.wordsLearned = (user.stats.wordsLearned || 0) + (lesson.vocab || 0);
    State.setCurrentUser(user);
    State.addXP(XP_CONFIG.lesson, `完成课程：${lesson.title}`);
    this.showToast(`🎉 课程完成！+${XP_CONFIG.lesson} XP`, 'success');
    State.checkBadges(user);
    this.render();
  },

  /* ============ 练习视图跳转 ============ */
  openPractice(lang, module) {
    Practice.open(lang, module);
  },

  /* ============ 成就 ============ */
  renderAchievements(user) {
    const statsEl = document.getElementById('user-stats-big');
    const totalWords = Object.values(user.vocab || {}).reduce((acc, l) => acc + Object.values(l).filter(v => v.known).length, 0);
    const totalLessons = Object.values(user.progress || {}).reduce((acc, l) => {
      Object.values(l || {}).forEach(level => Object.values(level || {}).forEach(x => { if (x?.completed) acc++; }));
      return acc;
    }, 0);

    statsEl.innerHTML = `
      <div class="stat-card"><div class="stat-value">${user.level}</div><div class="stat-label">当前等级</div></div>
      <div class="stat-card"><div class="stat-value">${user.xp}</div><div class="stat-label">总 XP</div></div>
      <div class="stat-card"><div class="stat-value">${totalWords}</div><div class="stat-label">掌握单词</div></div>
      <div class="stat-card"><div class="stat-value">${totalLessons}</div><div class="stat-label">完成课程</div></div>
      <div class="stat-card"><div class="stat-value">${user.maxSpeakingScore || 0}</div><div class="stat-label">最高口语分</div></div>
      <div class="stat-card"><div class="stat-value">${computeStreak(user)}</div><div class="stat-label">学习连击</div></div>
    `;

    const grid = document.getElementById('badge-grid');
    grid.innerHTML = BADGES.map(b => {
      const unlocked = (user.badges || []).includes(b.id);
      return `
        <div class="badge ${unlocked ? 'unlocked' : 'locked'}">
          <div class="badge-icon">${b.icon}</div>
          <div class="badge-name">${b.name}</div>
          <div class="badge-desc">${b.desc}</div>
          <div class="badge-progress">${unlocked ? '✅ 已解锁' : '🔒 未解锁'}</div>
        </div>
      `;
    }).join('');
  },

  /* ============ 个人资料 ============ */
  renderProfile(user) {
    const el = document.getElementById('profile-card');
    const langs = (user.stats.languagesStudied || []).map(l => LANGUAGES[l]?.nativeName || l).join('、') || '尚未开始';
    const registerDate = new Date(user.createdAt);
    el.innerHTML = `
      <div class="profile-header">
        <div class="profile-avatar">${user.username[0].toUpperCase()}</div>
        <div class="profile-info">
          <h3>${user.username}</h3>
          <p>${user.email} · 加入于 ${registerDate.toLocaleDateString('zh-CN')}</p>
          <p>当前等级 Lv.${user.level} · ${user.xp} XP</p>
        </div>
      </div>
      <h4 style="margin-bottom:12px">🌍 学习语言</h4>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:24px">
        ${(user.stats.languagesStudied || []).length ? (user.stats.languagesStudied || []).map(l =>
          `<span class="badge-chip" style="background:linear-gradient(135deg,#ede9fe,#fce7f3);color:var(--primary)">${LANGUAGES[l].flag} ${LANGUAGES[l].nativeName}</span>`
        ).join('') : '<span style="color:var(--text-muted)">点击课程开始学习第一种语言吧</span>'}
      </div>
      <h4 style="margin-bottom:12px">⚙️ 设置</h4>
      <div style="display:flex;gap:12px;flex-wrap:wrap">
        <button class="btn btn-outline" id="clear-history">清空学习记录</button>
        <button class="btn btn-ghost" id="logout-btn-2">退出登录</button>
      </div>
    `;
    document.getElementById('clear-history').addEventListener('click', () => {
      if (confirm('确定要清空所有学习记录吗？此操作不可撤销。')) {
        const u = State.getCurrentUser();
        u.xp = 0; u.level = 1; u.dailyXP = {}; u.progress = {}; u.vocab = {}; u.badges = []; u.maxSpeakingScore = 0; u.studyHistory = [];
        State.setCurrentUser(u);
        this.showToast('已清空学习记录', 'success');
        this.render();
      }
    });
    document.getElementById('logout-btn-2').addEventListener('click', () => {
      State.logout();
      this.navigate('auth');
      this.render();
    });
  },

  showToast(msg, type = 'info') {
    // 简易 toast：在 modal-body 上方附加
    let toast = document.getElementById('global-toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'global-toast';
      toast.style.cssText = `
        position:fixed;top:80px;left:50%;transform:translateX(-50%);
        padding:12px 24px;border-radius:10px;color:#fff;font-weight:600;
        z-index:2000;box-shadow:0 8px 30px rgba(0,0,0,0.2);
        transition:opacity 0.3s;opacity:0;pointer-events:none;
      `;
      document.body.appendChild(toast);
    }
    toast.style.background = type === 'success' ? '#22c55e' : type === 'error' ? '#ef4444' : '#6366f1';
    toast.textContent = msg;
    toast.style.opacity = '1';
    setTimeout(() => { toast.style.opacity = '0'; }, 2500);
  },
};

document.addEventListener('DOMContentLoaded', () => App.init());
