/**
 * 个性化推荐系统
 */
const Recommender = {
  /**
   * 根据用户学习情况推荐内容：
   * 1) 薄弱语言/级别优先
   * 2) 已学课程后续章节
   * 3) 间隔复习遗忘单词
   * 4) 推荐下一进阶等级
   */
  getRecommendations(user) {
    const recs = [];
    const progress = user.progress || {};
    const currentLang = user.currentLanguage || 'en';
    const currentLevel = user.currentLevel || 'A1';

    // 1) 当前语言-级别的下一节课
    const lessons = COURSES[currentLang]?.[currentLevel] || [];
    const langProgress = progress[currentLang]?.[currentLevel] || {};
    const nextLesson = lessons.find(l => !langProgress[l.id]?.completed);
    if (nextLesson) {
      recs.push({
        type: 'lesson',
        icon: '📘',
        title: `继续学习：${nextLesson.title}`,
        desc: `${LANGUAGES[currentLang].nativeName} · ${currentLevel} · ${nextLesson.desc}`,
        action: { lang: currentLang, level: currentLevel, lessonId: nextLesson.id },
        tag: '继续学习',
      });
    } else {
      // 已完成当前级别，推荐下一级
      const levelIdx = LEVELS.indexOf(currentLevel);
      const nextLevel = LEVELS[levelIdx + 1];
      if (nextLevel && COURSES[currentLang]?.[nextLevel]) {
        const firstLesson = COURSES[currentLang][nextLevel][0];
        recs.push({
          type: 'lesson',
          icon: '🚀',
          title: `挑战 ${nextLevel}：${firstLesson.title}`,
          desc: `已完成 ${currentLevel}，继续进阶 ${LANGUAGES[currentLang].nativeName}！`,
          action: { lang: currentLang, level: nextLevel, lessonId: firstLesson.id },
          tag: '进阶推荐',
        });
      }
    }

    // 2) 薄弱点：推荐语法练习 (选择用户较低分的语言)
    const langs = Object.keys(LANGUAGES);
    const weakestLang = langs.find(l => l !== currentLang && user.stats.languagesStudied?.includes(l)) || langs[(langs.indexOf(currentLang) + 1) % langs.length];
    const grammarList = GRAMMAR[weakestLang] || [];
    if (grammarList.length) {
      recs.push({
        type: 'grammar',
        icon: '📝',
        title: `语法练习 · ${LANGUAGES[weakestLang].nativeName}`,
        desc: `${grammarList[0].title} - ${grammarList[0].desc}`,
        action: { lang: weakestLang, module: 'grammar' },
        tag: '弱项强化',
      });
    }

    // 3) 听力训练
    const listeningList = LISTENING[currentLang] || [];
    if (listeningList.length) {
      recs.push({
        type: 'listening',
        icon: '👂',
        title: '听力训练',
        desc: `${listeningList.length} 段真实场景听力材料等你挑战`,
        action: { lang: currentLang, module: 'listening' },
        tag: '综合提升',
      });
    }

    // 4) 口语跟读
    const sentenceList = SENTENCES[currentLang] || [];
    if (sentenceList.length) {
      recs.push({
        type: 'speaking',
        icon: '🎙️',
        title: '口语跟读',
        desc: `${sentenceList.length} 句常用场景表达`,
        action: { lang: currentLang, module: 'speaking' },
        tag: '口语实战',
      });
    }

    // 5) 另一种语言入门
    const otherLang = langs.find(l => l !== currentLang);
    if (otherLang) {
      recs.push({
        type: 'newLang',
        icon: '🌏',
        title: `开启 ${LANGUAGES[otherLang].nativeName} 之旅`,
        desc: `拓展你的语言版图，尝试 ${LANGUAGES[otherLang].flag} ${LANGUAGES[otherLang].name}`,
        action: { lang: otherLang, level: 'A1', module: 'words' },
        tag: '多语拓展',
      });
    }

    return recs;
  },
  renderRecommendations(containerId, user) {
    const recs = this.getRecommendations(user);
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = recs.map((r, i) => `
      <div class="card" data-rec='${JSON.stringify(r.action).replace(/'/g, "&apos;")}' data-idx="${i}">
        <span class="badge-chip">${r.tag}</span>
        <div class="card-icon">${r.icon}</div>
        <div class="card-title">${r.title}</div>
        <div class="card-desc">${r.desc}</div>
      </div>
    `).join('');

    el.querySelectorAll('.card').forEach((card) => {
      card.addEventListener('click', () => {
        const data = JSON.parse(card.dataset.rec);
        this.executeRecommendation(data);
      });
    });
  },
  executeRecommendation(action) {
    if (!action) return;
    if (action.module) {
      App.openPractice(action.lang, action.module);
    } else if (action.lessonId) {
      App.openLesson(action.lang, action.level, action.lessonId);
    }
  },
};
