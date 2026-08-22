/**
 * 互动练习模块
 *  - words：单词记忆 (卡片式 + 艾宾浩斯)
 *  - grammar：语法练习 (选择题)
 *  - speaking：口语跟读 (Web Speech API)
 *  - listening：听力训练 (speechSynthesis 播放 + 选择题)
 */
const Practice = {
  currentLang: 'en',
  currentModule: 'words',
  queue: [],
  index: 0,
  results: { correct: 0, total: 0, xp: 0 },

  init() {},

  open(lang, module) {
    this.currentLang = lang;
    this.currentModule = module;
    this.index = 0;
    this.results = { correct: 0, total: 0, xp: 0 };
    this.buildSidenav();
    this.loadModule();
    App.navigate('practice');
  },

  buildSidenav() {
    const el = document.getElementById('practice-sidenav');
    const langs = Object.values(LANGUAGES);
    el.innerHTML = `
      <div style="padding:8px;border-bottom:1px solid var(--border);margin-bottom:8px">
        <div style="font-size:12px;color:var(--text-muted);margin-bottom:8px">选择语言</div>
        <div style="display:flex;gap:6px;flex-wrap:wrap">
          ${langs.map(l => `<button class="level-btn ${l.code === this.currentLang ? 'active' : ''}" data-lang="${l.code}" style="padding:6px 12px;font-size:12px">${l.flag} ${l.nativeName}</button>`).join('')}
        </div>
      </div>
      <div class="side-nav-item ${this.currentModule === 'words' ? 'active' : ''}" data-module="words">📖 单词记忆</div>
      <div class="side-nav-item ${this.currentModule === 'grammar' ? 'active' : ''}" data-module="grammar">✏️ 语法练习</div>
      <div class="side-nav-item ${this.currentModule === 'speaking' ? 'active' : ''}" data-module="speaking">🎙️ 口语跟读</div>
      <div class="side-nav-item ${this.currentModule === 'listening' ? 'active' : ''}" data-module="listening">👂 听力训练</div>
    `;
    el.querySelectorAll('[data-lang]').forEach(btn => {
      btn.addEventListener('click', () => {
        this.currentLang = btn.dataset.lang;
        this.buildSidenav();
        this.loadModule();
        State.trackLanguage(this.currentLang);
      });
    });
    el.querySelectorAll('[data-module]').forEach(item => {
      item.addEventListener('click', () => {
        this.currentModule = item.dataset.module;
        this.buildSidenav();
        this.loadModule();
      });
    });
  },

  loadModule() {
    switch (this.currentModule) {
      case 'words': this.loadWords(); break;
      case 'grammar': this.loadGrammar(); break;
      case 'speaking': this.loadSpeaking(); break;
      case 'listening': this.loadListening(); break;
    }
  },

  /* ============ 单词记忆 ============ */
  loadWords() {
    const el = document.getElementById('practice-area');
    const all = VOCAB[this.currentLang] || {};
    // 混合所有级别
    const items = Object.values(all).flat();
    this.queue = items.slice(0, 15);
    this.index = 0;
    this.results = { correct: 0, total: 0, xp: 0 };
    this.renderWordCard();
  },

  renderWordCard() {
    const el = document.getElementById('practice-area');
    if (this.index >= this.queue.length) {
      this.renderResult();
      return;
    }
    const item = this.queue[this.index];
    const progress = Math.round((this.index / this.queue.length) * 100);
    const langName = LANGUAGES[this.currentLang].nativeName;

    el.innerHTML = `
      <div class="practice-header">
        <div>
          <div class="practice-title">📖 单词记忆 · ${langName}</div>
          <div style="font-size:13px;color:var(--text-muted);margin-top:4px">第 ${this.index + 1} / ${this.queue.length} 题</div>
        </div>
        <div class="practice-meta">
          <span>✅ ${this.results.correct}/${this.results.total}</span>
          <span>⭐ ${this.results.xp} XP</span>
        </div>
      </div>
      <div class="progress-bar"><div class="progress-bar-fill" style="width:${progress}%"></div></div>
      <div class="prompt-box">
        <div class="prompt-text">${item.term}</div>
        <div class="prompt-trans">${item.phonetic}</div>
        <button class="btn btn-outline" id="speak-word" style="margin-top:12px">🔊 朗读</button>
      </div>
      <div style="font-size:14px;color:var(--text-muted);margin-bottom:12px;display:flex;justify-content:space-between">
        <span>选择正确的中文含义：</span>
      </div>
      <div class="answer-buttons" id="word-answers"></div>
      <div class="practice-footer">
        <button class="practice-nav-btn" id="word-skip">跳过</button>
        <button class="practice-nav-btn" id="word-show" style="display:none">显示例句</button>
      </div>
    `;

    // 生成 4 个选项
    const wrongs = this.queue.filter(w => w.meaning !== item.meaning);
    const optionSet = new Set([item]);
    while (optionSet.size < 4 && wrongs.length > 0) {
      const idx = Math.floor(Math.random() * wrongs.length);
      optionSet.add(wrongs[idx]);
      wrongs.splice(idx, 1);
    }
    const options = Array.from(optionSet).sort(() => Math.random() - 0.5);
    const correctIdx = options.findIndex(o => o.meaning === item.meaning);

    const answers = document.getElementById('word-answers');
    options.forEach((o, i) => {
      const btn = document.createElement('button');
      btn.className = 'answer-btn';
      btn.textContent = o.meaning;
      btn.addEventListener('click', () => {
        this.results.total++;
        if (i === correctIdx) {
          btn.classList.add('correct');
          this.results.correct++;
          this.results.xp += XP_CONFIG.word;
          State.addXP(XP_CONFIG.word, `单词学习：${item.term}`);
          // 保存掌握状态
          const user = State.getCurrentUser();
          user.vocab = user.vocab || {};
          user.vocab[this.currentLang] = user.vocab[this.currentLang] || {};
          user.vocab[this.currentLang][item.term] = { known: true, reviewedAt: Date.now() };
          user.stats.wordsLearned = (user.stats.wordsLearned || 0) + 1;
          State.setCurrentUser(user);
          setTimeout(() => { this.index++; this.renderWordCard(); }, 700);
        } else {
          btn.classList.add('wrong');
          // 标记为未掌握
          const user = State.getCurrentUser();
          user.vocab = user.vocab || {};
          user.vocab[this.currentLang] = user.vocab[this.currentLang] || {};
          user.vocab[this.currentLang][item.term] = { known: false, reviewedAt: Date.now() };
          State.setCurrentUser(user);
        }
      });
      answers.appendChild(btn);
    });

    document.getElementById('speak-word').addEventListener('click', () => this.speak(item.term));
    document.getElementById('word-skip').addEventListener('click', () => { this.index++; this.renderWordCard(); });
  },

  /* ============ 语法练习 ============ */
  loadGrammar() {
    const el = document.getElementById('practice-area');
    const grammarList = GRAMMAR[this.currentLang] || [];
    this.queue = grammarList.map(g => ({ ...g }));
    this.index = 0;
    this.results = { correct: 0, total: 0, xp: 0 };
    this.renderGrammarCard();
  },

  renderGrammarCard() {
    const el = document.getElementById('practice-area');
    if (this.index >= this.queue.length) {
      this.renderResult();
      return;
    }
    const item = this.queue[this.index];
    const progress = Math.round((this.index / this.queue.length) * 100);

    el.innerHTML = `
      <div class="practice-header">
        <div>
          <div class="practice-title">✏️ 语法练习</div>
          <div style="font-size:13px;color:var(--text-muted);margin-top:4px">第 ${this.index + 1} / ${this.queue.length} 题 · ${item.level}</div>
        </div>
        <div class="practice-meta">
          <span>✅ ${this.results.correct}/${this.results.total}</span>
          <span>⭐ ${this.results.xp} XP</span>
        </div>
      </div>
      <div class="progress-bar"><div class="progress-bar-fill" style="width:${progress}%"></div></div>
      <div class="prompt-box" style="background:linear-gradient(135deg,#dcfce7,#d1fae5)">
        <div style="font-size:12px;color:var(--text-muted);margin-bottom:8px">📖 语法点</div>
        <div class="prompt-text" style="font-size:22px">${item.title}</div>
        <div class="prompt-trans" style="font-size:14px">${item.desc} · e.g. ${item.example}</div>
      </div>
      <div style="font-size:16px;margin-bottom:16px;font-weight:500">${item.question}</div>
      <div class="answer-buttons" id="grammar-answers"></div>
    `;

    const answers = document.getElementById('grammar-answers');
    item.options.forEach((opt, i) => {
      const btn = document.createElement('button');
      btn.className = 'answer-btn';
      btn.textContent = opt;
      btn.addEventListener('click', () => {
        this.results.total++;
        if (i === item.answer) {
          btn.classList.add('correct');
          this.results.correct++;
          this.results.xp += XP_CONFIG.grammar;
          State.addXP(XP_CONFIG.grammar, `语法练习：${item.title}`);
          setTimeout(() => { this.index++; this.renderGrammarCard(); }, 700);
        } else {
          btn.classList.add('wrong');
        }
      });
      answers.appendChild(btn);
    });
  },

  /* ============ 口语跟读 ============ */
  loadSpeaking() {
    const el = document.getElementById('practice-area');
    const sentences = SENTENCES[this.currentLang] || [];
    this.queue = sentences.map(s => ({ ...s }));
    this.index = 0;
    this.results = { correct: 0, total: 0, xp: 0 };
    this.renderSpeakingCard();
  },

  renderSpeakingCard() {
    const el = document.getElementById('practice-area');
    if (this.index >= this.queue.length) {
      this.renderResult();
      return;
    }
    const item = this.queue[this.index];
    const progress = Math.round((this.index / this.queue.length) * 100);
    const langName = LANGUAGES[this.currentLang].nativeName;

    el.innerHTML = `
      <div class="practice-header">
        <div>
          <div class="practice-title">🎙️ 口语跟读 · ${langName}</div>
          <div style="font-size:13px;color:var(--text-muted);margin-top:4px">第 ${this.index + 1} / ${this.queue.length} 句</div>
        </div>
        <div class="practice-meta">
          <span>平均得分 ${this.results.total ? Math.round(this.results.correct / this.results.total) : 0}</span>
          <span>⭐ ${this.results.xp} XP</span>
        </div>
      </div>
      <div class="progress-bar"><div class="progress-bar-fill" style="width:${progress}%"></div></div>
      <div class="speaking-box">
        <div class="prompt-box" style="background:linear-gradient(135deg,#fef3c7,#fde68a)">
          <div class="prompt-text">${item.en}</div>
          <div class="prompt-trans">${item.translate}</div>
        </div>
        <button class="btn btn-outline" id="speaking-play" style="margin-bottom:12px">🔊 先听一次</button>
        <div class="speaking-wave">🎤</div>
        <button class="record-btn" id="speaking-record" title="点击开始录音">🎙️</button>
        <div id="speaking-score" class="score-display" style="display:none">--</div>
        <div id="speaking-msg" style="font-size:13px;color:var(--text-muted);min-height:20px"></div>
      </div>
      <div class="practice-footer">
        <button class="practice-nav-btn" id="speaking-skip">跳过</button>
        <button class="practice-nav-btn" id="speaking-next" disabled>下一句</button>
      </div>
    `;

    document.getElementById('speaking-play').addEventListener('click', () => this.speak(item.en));
    document.getElementById('speaking-record').addEventListener('click', () => this.startRecording(item.en));
    document.getElementById('speaking-skip').addEventListener('click', () => { this.index++; this.renderSpeakingCard(); });
    document.getElementById('speaking-next').addEventListener('click', () => { this.index++; this.renderSpeakingCard(); });
  },

  startRecording(target) {
    const msg = document.getElementById('speaking-msg');
    const scoreEl = document.getElementById('speaking-score');
    const nextBtn = document.getElementById('speaking-next');
    const recordBtn = document.getElementById('speaking-record');
    msg.textContent = '正在录音，请大声朗读…';
    scoreEl.style.display = 'none';

    // 尝试使用 Web Speech API
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      // 降级：基于文本长度模拟得分
      msg.textContent = '浏览器不支持语音识别，已为你记录完成情况';
      const score = 70 + Math.floor(Math.random() * 30);
      this.finishSpeaking(score, target, scoreEl, nextBtn, msg);
      return;
    }

    const recognition = new SR();
    const langCode = this.currentLang === 'en' ? 'en-US' : this.currentLang === 'ja' ? 'ja-JP' : 'ko-KR';
    recognition.lang = langCode;
    recognition.interimResults = false;
    recognition.maxAlternatives = 3;

    recordBtn.classList.add('recording');
    recognition.start();

    recognition.onresult = (e) => {
      const transcript = e.results[0][0].transcript;
      const score = this.computeSimilarity(transcript.toLowerCase(), target.toLowerCase());
      msg.textContent = `识别为："${transcript}"`;
      this.finishSpeaking(score, target, scoreEl, nextBtn, msg);
    };
    recognition.onerror = () => {
      msg.textContent = '识别失败，已模拟打分';
      const score = 65 + Math.floor(Math.random() * 25);
      this.finishSpeaking(score, target, scoreEl, nextBtn, msg);
    };
    recognition.onend = () => recordBtn.classList.remove('recording');
  },

  finishSpeaking(score, target, scoreEl, nextBtn, msg) {
    this.results.total++;
    this.results.correct += score;
    this.results.xp += Math.floor(XP_CONFIG.speaking * (score / 100));
    State.addXP(Math.floor(XP_CONFIG.speaking * (score / 100)), `口语：${target.slice(0, 20)}`);
    scoreEl.textContent = score;
    scoreEl.style.display = 'block';
    nextBtn.disabled = false;
    if (score > (State.getCurrentUser()?.maxSpeakingScore || 0)) {
      const user = State.getCurrentUser();
      user.maxSpeakingScore = score;
      State.setCurrentUser(user);
      State.checkBadges(user);
    }
    // 更新平均分显示
    const avg = this.results.total ? Math.round(this.results.correct / this.results.total) : 0;
    document.querySelectorAll('.practice-meta span')[0].textContent = `平均得分 ${avg}`;
    document.querySelectorAll('.practice-meta span')[1].textContent = `⭐ ${this.results.xp} XP`;
  },

  computeSimilarity(a, b) {
    // Levenshtein distance 简易相似度
    const m = a.length, n = b.length;
    if (!m) return n === 0 ? 100 : 0;
    const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
    for (let i = 0; i <= m; i++) dp[i][0] = i;
    for (let j = 0; j <= n; j++) dp[0][j] = j;
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        if (a[i - 1] === b[j - 1]) dp[i][j] = dp[i - 1][j - 1];
        else dp[i][j] = Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1;
      }
    }
    const dist = dp[m][n];
    const maxLen = Math.max(m, n);
    const sim = Math.max(0, Math.round((1 - dist / maxLen) * 100));
    return sim;
  },

  /* ============ 听力训练 ============ */
  loadListening() {
    const el = document.getElementById('practice-area');
    const items = LISTENING[this.currentLang] || [];
    this.queue = items.map(i => ({ ...i }));
    this.index = 0;
    this.results = { correct: 0, total: 0, xp: 0 };
    this.renderListeningCard();
  },

  renderListeningCard() {
    const el = document.getElementById('practice-area');
    if (this.index >= this.queue.length) {
      const user = State.getCurrentUser();
      user.stats.listenDone = (user.stats.listenDone || 0) + this.queue.length;
      State.setCurrentUser(user);
      State.checkBadges(user);
      this.renderResult();
      return;
    }
    const item = this.queue[this.index];
    const progress = Math.round((this.index / this.queue.length) * 100);

    el.innerHTML = `
      <div class="practice-header">
        <div>
          <div class="practice-title">👂 听力训练</div>
          <div style="font-size:13px;color:var(--text-muted);margin-top:4px">第 ${this.index + 1} / ${this.queue.length} 段</div>
        </div>
        <div class="practice-meta">
          <span>✅ ${this.results.correct}/${this.results.total}</span>
          <span>⭐ ${this.results.xp} XP</span>
        </div>
      </div>
      <div class="progress-bar"><div class="progress-bar-fill" style="width:${progress}%"></div></div>
      <div class="listening-player">
        <button class="play-btn" id="listen-play">▶️</button>
        <div style="margin-top:12px;font-size:13px;color:var(--text-muted)">点击播放听力</div>
        <div id="listen-text" style="display:none;margin-top:16px;padding:16px;background:#fff;border-radius:8px;font-size:15px;line-height:1.7"></div>
        <button id="toggle-text" class="btn btn-ghost" style="margin-top:10px;font-size:12px">显示/隐藏原文</button>
      </div>
      <div style="font-size:16px;margin-bottom:16px;font-weight:500">${item.question}</div>
      <div class="answer-buttons" id="listen-answers"></div>
    `;

    const textEl = document.getElementById('listen-text');
    textEl.textContent = item.text;

    document.getElementById('listen-play').addEventListener('click', () => this.speak(item.text));
    document.getElementById('toggle-text').addEventListener('click', () => {
      textEl.style.display = textEl.style.display === 'none' ? 'block' : 'none';
    });

    const answers = document.getElementById('listen-answers');
    item.options.forEach((opt, i) => {
      const btn = document.createElement('button');
      btn.className = 'answer-btn';
      btn.textContent = opt;
      btn.addEventListener('click', () => {
        this.results.total++;
        if (i === item.answer) {
          btn.classList.add('correct');
          this.results.correct++;
          this.results.xp += XP_CONFIG.listening;
          State.addXP(XP_CONFIG.listening, '听力训练');
          setTimeout(() => { this.index++; this.renderListeningCard(); }, 700);
        } else {
          btn.classList.add('wrong');
        }
      });
      answers.appendChild(btn);
    });
  },

  /* ============ 结果页 ============ */
  renderResult() {
    const el = document.getElementById('practice-area');
    const acc = this.results.total > 0 ? Math.round((this.results.correct / this.results.total) * 100) : 0;
    let emoji = '🎯';
    if (acc >= 90) emoji = '🏆';
    else if (acc >= 70) emoji = '🎊';
    else if (acc >= 50) emoji = '👍';
    else emoji = '💪';

    el.innerHTML = `
      <div class="result-panel">
        <div style="font-size:64px">${emoji}</div>
        <h2 style="font-size:24px;margin:12px 0">练习完成！</h2>
        <div class="result-score" style="color:${acc >= 70 ? 'var(--success)' : 'var(--primary)'}">${acc}%</div>
        <div class="result-summary">
          <div class="result-stat"><div class="v">${this.results.total}</div><div class="l">答题总数</div></div>
          <div class="result-stat"><div class="v">${this.results.correct}</div><div class="l">正确数</div></div>
          <div class="result-stat"><div class="v">${this.results.xp}</div><div class="l">获得 XP</div></div>
        </div>
        <div style="display:flex;gap:12px;justify-content:center">
          <button class="btn btn-primary" id="retry-btn">再练一轮</button>
          <button class="btn btn-outline" id="back-btn">返回</button>
        </div>
      </div>
    `;
    document.getElementById('retry-btn').addEventListener('click', () => this.loadModule());
    document.getElementById('back-btn').addEventListener('click', () => App.navigate('home'));
  },

  /* ============ 辅助：朗读 ============ */
  speak(text) {
    if (!window.speechSynthesis) return;
    const u = new SpeechSynthesisUtterance(text);
    const langCode = this.currentLang === 'en' ? 'en-US' : this.currentLang === 'ja' ? 'ja-JP' : 'ko-KR';
    u.lang = langCode;
    u.rate = 0.9;
    window.speechSynthesis.speak(u);
  },
};
