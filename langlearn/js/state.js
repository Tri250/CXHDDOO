/**
 * LanLearn 全局状态管理
 */
const State = {
  getUsers() {
    return JSON.parse(localStorage.getItem('ll_users') || '{}');
  },
  saveUsers(users) {
    localStorage.setItem('ll_users', JSON.stringify(users));
  },
  getCurrentUserId() {
    return localStorage.getItem('ll_current_user');
  },
  getCurrentUser() {
    const id = this.getCurrentUserId();
    if (!id) return null;
    const users = this.getUsers();
    return users[id] || null;
  },
  setCurrentUser(user) {
    const users = this.getUsers();
    users[user.id] = user;
    this.saveUsers(users);
  },
  registerUser(username, email, password, native) {
    const users = this.getUsers();
    const existing = Object.values(users).find(u => u.username === username || u.email === email);
    if (existing) {
      return { success: false, message: '用户名或邮箱已被注册' };
    }
    const id = 'u_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7);
    const user = {
      id,
      username,
      email,
      password,
      native: native || 'zh',
      createdAt: Date.now(),
      xp: 0,
      level: 1,
      dailyXP: {},
      lastLoginDate: null,
      stats: {
        logins: 0,
        wordsLearned: 0,
        lessonsCompleted: 0,
        listenDone: 0,
        posts: 0,
        languagesStudied: [],
      },
      progress: {}, // { [langCode]: { [level]: { [lessonId]: { completed: true, score } } } }
      vocab: { [langCode]: {} }, // 单词掌握情况
      badges: [],
      posts: [],
      likes: {},
      maxSpeakingScore: 0,
      currentLanguage: 'en',
      currentLevel: 'A1',
      currentLessonId: null,
      studyHistory: [],
    };
    users[id] = user;
    this.saveUsers(users);
    return { success: true, user };
  },
  loginUser(username, password) {
    const users = this.getUsers();
    const user = Object.values(users).find(u => u.username === username && u.password === password);
    if (!user) return { success: false, message: '用户名或密码错误' };
    user.stats.logins++;
    const today = new Date().toISOString().slice(0, 10);
    user.lastLoginDate = today;
    if (!user.dailyXP) user.dailyXP = {};
    if (!user.dailyXP[today]) user.dailyXP[today] = 0;
    this.setCurrentUser(user);
    localStorage.setItem('ll_current_user', user.id);
    return { success: true, user };
  },
  logout() {
    localStorage.removeItem('ll_current_user');
  },
  addXP(amount, reason) {
    const user = this.getCurrentUser();
    if (!user) return;
    user.xp = (user.xp || 0) + amount;
    const today = new Date().toISOString().slice(0, 10);
    user.dailyXP = user.dailyXP || {};
    user.dailyXP[today] = (user.dailyXP[today] || 0) + amount;
    // Level up
    const newLevel = Math.floor(user.xp / 100) + 1;
    user.level = newLevel;
    // 记录学习历史
    user.studyHistory = user.studyHistory || [];
    user.studyHistory.push({ date: today, xp: amount, reason, ts: Date.now() });
    if (user.studyHistory.length > 500) user.studyHistory.shift();
    this.setCurrentUser(user);
    this.checkBadges(user);
  },
  checkBadges(user) {
    const unlocked = user.badges || [];
    BADGES.forEach(b => {
      if (!unlocked.includes(b.id) && b.check(user)) {
        unlocked.push(b.id);
        setTimeout(() => App.showToast(`🎉 解锁成就：${b.name}`, 'success'), 400);
      }
    });
    user.badges = unlocked;
    this.setCurrentUser(user);
  },
  updateProgress(lang, level, lessonId, data) {
    const user = this.getCurrentUser();
    if (!user) return;
    user.progress = user.progress || {};
    user.progress[lang] = user.progress[lang] || {};
    user.progress[lang][level] = user.progress[lang][level] || {};
    user.progress[lang][level][lessonId] = { ...user.progress[lang][level][lessonId], ...data, updatedAt: Date.now() };
    this.setCurrentUser(user);
  },
  trackLanguage(langCode) {
    const user = this.getCurrentUser();
    if (!user) return;
    user.stats.languagesStudied = user.stats.languagesStudied || [];
    if (!user.stats.languagesStudied.includes(langCode)) {
      user.stats.languagesStudied.push(langCode);
      this.setCurrentUser(user);
      this.checkBadges(user);
    }
  },
};
