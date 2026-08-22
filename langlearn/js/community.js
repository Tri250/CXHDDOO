/**
 * 社区交流 & 排行榜
 */
const Community = {
  init() {
    document.getElementById('post-submit')?.addEventListener('click', () => this.submitPost());
  },
  submitPost() {
    const user = State.getCurrentUser();
    if (!user) return;
    const content = document.getElementById('post-content').value.trim();
    const lang = document.getElementById('post-lang').value;
    if (!content) { App.showToast('请输入内容', 'error'); return; }
    const post = {
      id: 'p_' + Date.now(),
      userId: user.id,
      username: user.username,
      content,
      lang,
      createdAt: Date.now(),
      likes: 0,
      comments: [],
    };
    user.posts = [post, ...(user.posts || [])];
    user.stats.posts = (user.stats.posts || 0) + 1;
    State.setCurrentUser(user);
    State.addXP(XP_CONFIG.post, '发布社区动态');
    document.getElementById('post-content').value = '';
    this.renderPosts();
    App.showToast('发布成功 +' + XP_CONFIG.post + ' XP', 'success');
    State.checkBadges(user);
  },
  renderPosts() {
    const user = State.getCurrentUser();
    if (!user) return;
    const el = document.getElementById('post-list');
    if (!el) return;
    // 合并示例帖子（首次展示）+ 用户帖子
    const allPosts = this.getSeedPosts().concat(user.posts || []);
    el.innerHTML = allPosts.map(p => {
      const langName = p.lang === 'general' ? '通用' : (LANGUAGES[p.lang]?.nativeName || p.lang);
      const liked = user.likes?.[p.id];
      return `
        <div class="post">
          <div class="post-header">
            <div class="post-user">
              <div class="post-avatar">${p.username[0].toUpperCase()}</div>
              <div>
                <div class="post-name">${p.username}</div>
                <div class="post-time">${this.formatTime(p.createdAt)} · <span class="post-lang-tag">${langName}</span></div>
              </div>
            </div>
          </div>
          <div class="post-content">${this.escape(p.content)}</div>
          <div class="post-actions-bar">
            <div class="post-action ${liked ? 'liked' : ''}" data-like="${p.id}">${liked ? '❤️' : '🤍'} 点赞 ${p.likes || 0}</div>
            <div class="post-action">💬 评论 ${p.comments?.length || 0}</div>
            <div class="post-action">🔖 收藏</div>
          </div>
        </div>
      `;
    }).join('');
    el.querySelectorAll('[data-like]').forEach(el => {
      el.addEventListener('click', () => this.toggleLike(el.dataset.like));
    });
  },
  toggleLike(postId) {
    const user = State.getCurrentUser();
    if (!user) return;
    user.likes = user.likes || {};
    user.likes[postId] = !user.likes[postId];
    // 更新帖子 like 数
    user.posts = user.posts.map(p => {
      if (p.id === postId) p.likes = (p.likes || 0) + (user.likes[postId] ? 1 : -1);
      return p;
    });
    // 种子帖子点赞数暂存
    this._seedLikes = this._seedLikes || {};
    this._seedLikes[postId] = (this._seedLikes[postId] || 0) + (user.likes[postId] ? 1 : -1);
    State.setCurrentUser(user);
    this.renderPosts();
  },
  renderLeaderboard() {
    const el = document.getElementById('leaderboard');
    if (!el) return;
    const users = Object.values(State.getUsers());
    // 加入一些虚拟用户
    const all = users.concat(this.getSeedLeaderboard());
    all.sort((a, b) => (b.xp || 0) - (a.xp || 0));
    const top = all.slice(0, 8);
    el.innerHTML = top.map((u, i) => {
      const rank = i + 1;
      const medal = rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `#${rank}`;
      const cls = rank === 1 ? 'gold' : rank === 2 ? 'silver' : rank === 3 ? 'bronze' : '';
      return `
        <div class="leader-row">
          <div class="leader-rank ${cls}">${medal}</div>
          <div>
            <strong>${u.username}</strong>
            <div style="font-size:12px;color:var(--text-muted)">Lv.${u.level || 1} · ${u.stats?.languagesStudied?.length || 1} 种语言</div>
          </div>
          <div style="text-align:right;font-weight:700;color:var(--primary)">${u.xp || 0} XP</div>
        </div>
      `;
    }).join('');
  },
  getSeedPosts() {
    const now = Date.now();
    return [
      { id: 'seed-1', username: 'Hanako_东京', content: 'はじめて日本語で会話できました！みんなありがとうございます🌸', lang: 'ja', createdAt: now - 3600000, likes: 42, comments: 8 },
      { id: 'seed-2', username: 'Mike_London', content: "Just finished B2 listening practice! The key is to listen for keywords, not every single word. Keep going! 💪", lang: 'en', createdAt: now - 7200000, likes: 28, comments: 5 },
      { id: 'seed-3', username: '민수_서울', content: '오늘 한국어 단어 50개 외웠어요! 다들 파이팅! 🇰🇷', lang: 'ko', createdAt: now - 10800000, likes: 35, comments: 7 },
      { id: 'seed-4', username: 'Lily_Learner', content: '分享一个小技巧：每天花15分钟用目标语言自言自语，坚持三个月口语会质的飞跃！', lang: 'general', createdAt: now - 14400000, likes: 56, comments: 12 },
    ];
  },
  getSeedLeaderboard() {
    return [
      { username: 'Polyglot_Pro', level: 18, xp: 1850, stats: { languagesStudied: ['en','ja','ko','fr'] } },
      { username: '语言小天才', level: 15, xp: 1520, stats: { languagesStudied: ['en','ja'] } },
      { username: 'Sakura_樱', level: 12, xp: 1240, stats: { languagesStudied: ['ja','en'] } },
      { username: 'Korean_Kim', level: 10, xp: 980, stats: { languagesStudied: ['ko'] } },
      { username: 'EN_Native', level: 8, xp: 780, stats: { languagesStudied: ['en','ja'] } },
      { username: 'DreamLearner', level: 7, xp: 650, stats: { languagesStudied: ['en','ko','ja'] } },
    ];
  },
  formatTime(ts) {
    const d = new Date(ts);
    const diff = Date.now() - ts;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前';
    if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前';
    return d.toLocaleDateString('zh-CN');
  },
  escape(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },
};
