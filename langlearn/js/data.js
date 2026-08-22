/**
 * LanLearn 数据层：语言、分级课程、单词、语法、听力、句型等
 */

const LANGUAGES = {
  en: { code: 'en', name: 'English', flag: '🇬🇧', nativeName: '英语' },
  ja: { code: 'ja', name: '日本語', flag: '🇯🇵', nativeName: '日语' },
  ko: { code: 'ko', name: '한국어', flag: '🇰🇷', nativeName: '韩语' },
};

const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

const COURSES = {
  en: {
    A1: [
      { id: 'en-a1-1', title: 'Greetings & Introductions', desc: '学习日常问候与自我介绍', vocab: 20, exercises: 8 },
      { id: 'en-a1-2', title: 'Numbers & Dates', desc: '掌握数字、日期、时间表达', vocab: 25, exercises: 10 },
      { id: 'en-a1-3', title: 'Family Members', desc: '家庭成员与亲属关系', vocab: 18, exercises: 7 },
      { id: 'en-a1-4', title: 'Colors & Shapes', desc: '颜色、形状与感官描述', vocab: 15, exercises: 6 },
    ],
    A2: [
      { id: 'en-a2-1', title: 'Daily Routines', desc: '描述日常作息与习惯', vocab: 28, exercises: 10 },
      { id: 'en-a2-2', title: 'Food & Dining', desc: '餐厅点餐与美食对话', vocab: 32, exercises: 12 },
      { id: 'en-a2-3', title: 'Weather & Seasons', desc: '天气、气候与四季', vocab: 22, exercises: 9 },
      { id: 'en-a2-4', title: 'Shopping', desc: '购物场景实战表达', vocab: 30, exercises: 11 },
    ],
    B1: [
      { id: 'en-b1-1', title: 'Travel & Adventure', desc: '旅行见闻与冒险经历', vocab: 35, exercises: 12 },
      { id: 'en-b1-2', title: 'Work & Career', desc: '职场沟通与职业发展', vocab: 40, exercises: 14 },
      { id: 'en-b1-3', title: 'Technology & Life', desc: '科技与日常生活', vocab: 38, exercises: 13 },
    ],
    B2: [
      { id: 'en-b2-1', title: 'Society & Culture', desc: '社会现象与文化讨论', vocab: 45, exercises: 15 },
      { id: 'en-b2-2', title: 'Business English', desc: '商务英语实战', vocab: 50, exercises: 16 },
    ],
    C1: [{ id: 'en-c1-1', title: 'Advanced Debate', desc: '深度辩论与观点表达', vocab: 55, exercises: 18 }],
    C2: [{ id: 'en-c2-1', title: 'Mastery Proficiency', desc: '母语级精通训练', vocab: 60, exercises: 20 }],
  },
  ja: {
    A1: [
      { id: 'ja-a1-1', title: 'あいさつと自己紹介', desc: '日语基础问候语', vocab: 22, exercises: 8 },
      { id: 'ja-a1-2', title: 'ひらがな・カタカナ', desc: '五十音图完整掌握', vocab: 46, exercises: 15 },
      { id: 'ja-a1-3', title: '数字と時間', desc: '日语数字、日期与时间', vocab: 20, exercises: 8 },
    ],
    A2: [
      { id: 'ja-a2-1', title: '家族と友人', desc: '家庭成员与社交表达', vocab: 28, exercises: 10 },
      { id: 'ja-a2-2', title: '食事と料理', desc: '日料与餐厅对话', vocab: 32, exercises: 12 },
    ],
    B1: [
      { id: 'ja-b1-1', title: '旅行とお出かけ', desc: '日本旅行实战', vocab: 38, exercises: 13 },
      { id: 'ja-b1-2', title: '仕事と社会', desc: '职场日语与社会文化', vocab: 42, exercises: 14 },
    ],
    B2: [{ id: 'ja-b2-1', title: 'ビジネス日本語', desc: '商务日语进阶', vocab: 48, exercises: 16 }],
    C1: [{ id: 'ja-c1-1', title: 'ニュースと意見', desc: '新闻日语与深度表达', vocab: 52, exercises: 18 }],
    C2: [{ id: 'ja-c2-1', title: '日本語の達人', desc: '日语母语级精通', vocab: 58, exercises: 20 }],
  },
  ko: {
    A1: [
      { id: 'ko-a1-1', title: '인사와 자기소개', desc: '韩语基础问候', vocab: 20, exercises: 8 },
      { id: 'ko-a1-2', title: '한글 자모', desc: '韩文子母与发音', vocab: 28, exercises: 10 },
      { id: 'ko-a1-3', title: '숫자와 시간', desc: '韩语数字与时间', vocab: 22, exercises: 9 },
    ],
    A2: [
      { id: 'ko-a2-1', title: '가족과 친구', desc: '家庭与社交韩语', vocab: 26, exercises: 10 },
      { id: 'ko-a2-2', title: '음식과 식당', desc: '美食与餐厅对话', vocab: 30, exercises: 12 },
    ],
    B1: [
      { id: 'ko-b1-1', title: '여행과 문화', desc: '韩国旅行与文化', vocab: 36, exercises: 13 },
      { id: 'ko-b1-2', title: '직장생활', desc: '职场韩语表达', vocab: 40, exercises: 14 },
    ],
    B2: [{ id: 'ko-b2-1', title: '비즈니스 한국어', desc: '商务韩语', vocab: 46, exercises: 16 }],
    C1: [{ id: 'ko-c1-1', title: '뉴스와 토론', desc: '新闻与讨论韩语', vocab: 50, exercises: 18 }],
    C2: [{ id: 'ko-c2-1', title: '한국어 고급', desc: '韩语精通级训练', vocab: 56, exercises: 20 }],
  },
};

/**
 * 单词库（按语言 + 分级）
 */
const VOCAB = {
  en: {
    A1: [
      { term: 'Hello', phonetic: '/həˈloʊ/', meaning: '你好', example: 'Hello, nice to meet you.' },
      { term: 'Goodbye', phonetic: '/ˌɡʊdˈbaɪ/', meaning: '再见', example: 'Goodbye, see you tomorrow.' },
      { term: 'Thank you', phonetic: '/ˈθæŋk juː/', meaning: '谢谢', example: 'Thank you for your help.' },
      { term: 'Please', phonetic: '/pliːz/', meaning: '请', example: 'Please sit down.' },
      { term: 'Excuse me', phonetic: '/ɪkˈskjuːz miː/', meaning: '打扰一下', example: 'Excuse me, where is the station?' },
      { term: 'Yes', phonetic: '/jes/', meaning: '是的', example: 'Yes, I agree.' },
      { term: 'No', phonetic: '/noʊ/', meaning: '不', example: 'No, thank you.' },
      { term: 'Sorry', phonetic: '/ˈsɒri/', meaning: '抱歉', example: "I'm sorry for being late." },
      { term: 'Name', phonetic: '/neɪm/', meaning: '名字', example: 'My name is Tom.' },
      { term: 'Friend', phonetic: '/frend/', meaning: '朋友', example: 'She is my best friend.' },
      { term: 'Family', phonetic: '/ˈfæməli/', meaning: '家庭', example: 'I love my family.' },
      { term: 'Book', phonetic: '/bʊk/', meaning: '书', example: 'This book is interesting.' },
      { term: 'School', phonetic: '/skuːl/', meaning: '学校', example: "I go to school every day." },
      { term: 'Work', phonetic: '/wɜːrk/', meaning: '工作', example: "I work in a bank." },
      { term: 'Water', phonetic: '/ˈwɔːtər/', meaning: '水', example: "I'd like some water." },
    ],
    A2: [
      { term: 'Breakfast', phonetic: '/ˈbrekfəst/', meaning: '早餐', example: "I have breakfast at 7." },
      { term: 'Restaurant', phonetic: '/ˈrestrɒnt/', meaning: '餐厅', example: "Let's go to the restaurant." },
      { term: 'Weather', phonetic: '/ˈweðər/', meaning: '天气', example: "The weather is nice today." },
      { term: 'Shopping', phonetic: '/ˈʃɒpɪŋ/', meaning: '购物', example: "I love shopping on weekends." },
      { term: 'Hobby', phonetic: '/ˈhɒbi/', meaning: '爱好', example: "What is your hobby?" },
      { term: 'Travel', phonetic: '/ˈtrævəl/', meaning: '旅行', example: "I love to travel." },
      { term: 'Culture', phonetic: '/ˈkʌltʃər/', meaning: '文化', example: "I'm interested in Chinese culture." },
    ],
    B1: [
      { term: 'Experience', phonetic: '/ɪkˈspɪriəns/', meaning: '经验/经历', example: "It was a great experience." },
      { term: 'Opportunity', phonetic: '/ˌɒpəˈtjuːnəti/', meaning: '机会', example: "This is a great opportunity." },
      { term: 'Challenging', phonetic: '/ˈtʃælɪndʒɪŋ/', meaning: '具有挑战性的', example: "The job is challenging." },
      { term: 'Environment', phonetic: '/ɪnˈvaɪrənmənt/', meaning: '环境', example: "We should protect the environment." },
    ],
  },
  ja: {
    A1: [
      { term: 'こんにちは', phonetic: 'konnichiwa', meaning: '你好', example: 'こんにちは、元気ですか？' },
      { term: 'さようなら', phonetic: 'sayounara', meaning: '再见', example: 'さようなら、また明日。' },
      { term: 'ありがとう', phonetic: 'arigatou', meaning: '谢谢', example: 'ありがとうございます。' },
      { term: 'はい', phonetic: 'hai', meaning: '是的', example: 'はい、そうです。' },
      { term: 'いいえ', phonetic: 'iie', meaning: '不', example: 'いいえ、違います。' },
      { term: 'すみません', phonetic: 'sumimasen', meaning: '对不起', example: 'すみません、遅れました。' },
      { term: '私', phonetic: 'watashi', meaning: '我', example: '私は学生です。' },
      { term: '友達', phonetic: 'tomodachi', meaning: '朋友', example: '彼は私の友達です。' },
      { term: '学校', phonetic: 'gakkou', meaning: '学校', example: '学校へ行きます。' },
      { term: '水', phonetic: 'mizu', meaning: '水', example: '水をください。' },
    ],
    A2: [
      { term: '家族', phonetic: 'kazoku', meaning: '家族', example: '家族は大切です。' },
      { term: '料理', phonetic: 'ryouri', meaning: '料理', example: '料理が好きです。' },
      { term: '仕事', phonetic: 'shigoto', meaning: '工作', example: '仕事をしています。' },
      { term: '天気', phonetic: 'tenki', meaning: '天气', example: '今日はいい天気ですね。' },
    ],
  },
  ko: {
    A1: [
      { term: '안녕하세요', phonetic: 'annyeonghaseyo', meaning: '你好', example: '안녕하세요, 만나서 반갑습니다.' },
      { term: '안녕히 가세요', phonetic: 'annyeonghi gaseyo', meaning: '再见', example: '안녕히 가세요, 다음에 뵙겠습니다.' },
      { term: '감사합니다', phonetic: 'gamsahamnida', meaning: '谢谢', example: '도와주셔서 감사합니다.' },
      { term: '네', phonetic: 'ne', meaning: '是的', example: '네, 맞아요.' },
      { term: '아니요', phonetic: 'aniyo', meaning: '不', example: '아니요, 틀렸어요.' },
      { term: '미안합니다', phonetic: 'miamhamnida', meaning: '抱歉', example: '늦어서 미안합니다.' },
      { term: '이름', phonetic: 'ireum', meaning: '名字', example: '제 이름은 김민수입니다.' },
      { term: '친구', phonetic: 'chingu', meaning: '朋友', example: '그는 제 친구예요.' },
      { term: '학교', phonetic: 'hakgyo', meaning: '学校', example: '학교에 가요.' },
      { term: '물', phonetic: 'mul', meaning: '水', example: '물 한 잔 주세요.' },
    ],
    A2: [
      { term: '가족', phonetic: 'gajok', meaning: '家庭', example: '가족과 함께해요.' },
      { term: '음식', phonetic: 'eumsik', meaning: '食物', example: '음식을 좋아해요.' },
      { term: '직장', phonetic: 'jikjang', meaning: '职场', example: '직장에서 일해요.' },
    ],
  },
};

/**
 * 语法点
 */
const GRAMMAR = {
  en: [
    { id: 'en-g-1', title: '一般现在时', level: 'A1', desc: '主语 + 动词 + 其他', example: 'I play tennis every Sunday.', question: 'She ___ (play) tennis every day.', options: ['play', 'plays', 'playing', 'played'], answer: 1 },
    { id: 'en-g-2', title: '现在进行时', level: 'A1', desc: 'be + doing', example: 'I am reading a book now.', question: 'Look! The children ___ (play) in the park.', options: ['play', 'plays', 'are playing', 'played'], answer: 2 },
    { id: 'en-g-3', title: '一般过去时', level: 'A2', desc: '动词过去式', example: 'I visited Paris last year.', question: 'Yesterday I ___ (go) to the cinema.', options: ['go', 'goes', 'went', 'gone'], answer: 2 },
    { id: 'en-g-4', title: '情态动词 can', level: 'A1', desc: 'can + 动词原形', example: 'I can swim.', question: '___ you speak French?', options: ['Do', 'Are', 'Can', 'Is'], answer: 2 },
    { id: 'en-g-5', title: '比较级', level: 'A2', desc: '形容词比较级 + than', example: 'Tom is taller than me.', question: 'This book is ___ (interesting) than that one.', options: ['interesting', 'more interesting', 'most interesting', 'interestinger'], answer: 1 },
    { id: 'en-g-6', title: '现在完成时', level: 'B1', desc: 'have/has + 过去分词', example: 'I have lived here for 5 years.', question: 'She ___ (visit) Tokyo three times.', options: ['visit', 'visits', 'has visited', 'visited'], answer: 2 },
  ],
  ja: [
    { id: 'ja-g-1', title: 'は～です', level: 'A1', desc: 'AはBです (A是B)', example: '私は学生です。', question: '彼 ___ 先生です。', options: ['は', 'が', 'を', 'に'], answer: 0 },
    { id: 'ja-g-2', title: 'ます形', level: 'A1', desc: '动词ます形', example: '毎日日本語を勉強します。', question: '明日、映画を___。', options: ['見ます', '見ません', '見ました', '見て'], answer: 0 },
    { id: 'ja-g-3', title: 'て形', level: 'A1', desc: '动词て形', example: '本を読んでいます。', question: '音楽を___います。', options: ['聞く', '聞き', '聞いて', '聞け'], answer: 2 },
    { id: 'ja-g-4', title: '过去式 ました', level: 'A2', desc: '～ました', example: '昨日友達に会いました。', question: '先週、東京へ___。', options: ['行きます', '行きません', '行きました', '行って'], answer: 2 },
  ],
  ko: [
    { id: 'ko-g-1', title: '은/는', level: 'A1', desc: '助词 은/는 (表示主题)', example: '저는 학생입니다.', question: '김치 ___ 맛있어요.', options: ['은', '이', '를', '에'], answer: 0 },
    { id: 'ko-g-2', title: 'ㅂ니다/습니다', level: 'A1', desc: '尊敬/礼貌句式', example: '만나서 반갑습니다.', question: '저는 한국 사람___.', options: ['입니다', '습니다', '세요', '해요'], answer: 0 },
    { id: 'ko-g-3', title: '过去式', level: 'A1', desc: '었어요 / 했어요', example: '어제 영화를 봤어요.', question: '작년에 공부___.', options: ['해요', '했어요', '할 거예요', '하고'], answer: 1 },
    { id: 'ko-g-4', title: '比较句', level: 'A2', desc: 'A가 B보다～', example: '김치찌개가 더워요.', question: '영어___ 한국어보다 쉬워요.', options: ['가', '이', '는', '을'], answer: 0 },
  ],
};

/**
 * 句型（口语跟读）
 */
const SENTENCES = {
  en: [
    { en: 'How are you today?', translate: '你今天怎么样？' },
    { en: 'I would like a cup of coffee, please.', translate: '请给我一杯咖啡。' },
    { en: 'Could you tell me the way to the station?', translate: '你能告诉我去车站的路吗？' },
    { en: 'Nice to meet you.', translate: '很高兴见到你。' },
    { en: 'I have been learning English for 3 years.', translate: '我学英语已经三年了。' },
    { en: "Let's grab a bite to eat.", translate: '我们去吃点东西吧。' },
    { en: 'The weather has been so lovely lately.', translate: '最近天气一直很好。' },
    { en: 'Could you please repeat that?', translate: '你能重复一下吗？' },
  ],
  ja: [
    { en: 'お元気ですか？', translate: '你好吗？' },
    { en: 'コーヒーを一つください。', translate: '请给我一杯咖啡。' },
    { en: '駅への道を教えていただけますか？', translate: '你能告诉我去车站的路吗？' },
    { en: 'はじめまして、よろしくお願いします。', translate: '初次见面请多关照。' },
    { en: '日本語を三年間勉強しています。', translate: '我学日语已经三年了。' },
    { en: '一緒にご飯を食べましょう。', translate: '我们一起吃饭吧。' },
    { en: '最近、天気がとてもいいですね。', translate: '最近天气真好。' },
  ],
  ko: [
    { en: '오늘 기분이 어떠세요?', translate: '今天心情怎么样？' },
    { en: '커피 한 잔 주세요.', translate: '请给我一杯咖啡。' },
    { en: '역 가는 길을 알려주세요.', translate: '请告诉我去车站的路。' },
    { en: '만나서 반갑습니다.', translate: '很高兴见到你。' },
    { en: '한국어를 3년 공부했어요.', translate: '我学韩语已经三年了。' },
    { en: '같이 밥 먹어요.', translate: '我们一起吃饭吧。' },
    { en: '요즘 날씨가 정말 좋아요.', translate: '最近天气真好。' },
  ],
};

/**
 * 听力练习（基于合成语音，使用 speechSynthesis）
 */
const LISTENING = {
  en: [
    { id: 'en-l-1', text: 'Excuse me, could you tell me how to get to the nearest subway station?', question: 'What is the speaker asking for?', options: ['Restaurant directions', 'Subway directions', 'Hotel booking', 'Flight information'], answer: 1 },
    { id: 'en-l-2', text: "I've been working on this project for three months and finally we're launching it next week.", question: 'How long has the project been going on?', options: ['One month', 'Two months', 'Three months', 'Six months'], answer: 2 },
    { id: 'en-l-3', text: "The weather forecast says it will rain heavily tomorrow, so we'd better reschedule the outdoor activity.", question: 'What will happen tomorrow?', options: ['Sunny weather', 'Heavy rain', 'Snow storm', 'Foggy morning'], answer: 1 },
    { id: 'en-l-4', text: 'Could you speak a little more slowly? I am still learning English.', question: 'What does the speaker want?', options: ['Faster speech', 'Slower speech', 'Louder voice', 'Different language'], answer: 1 },
  ],
  ja: [
    { id: 'ja-l-1', text: 'すみません、最寄りの駅への道を教えていただけますか？', question: '話し手は何を尋ねていますか？', options: ['レストランの場所', '駅の道', 'ホテルの予約', '飛行機の情報'], answer: 1 },
    { id: 'ja-l-2', text: 'このプロジェクトを三ヶ月間取り組んできましたが、来週やっと公開できます。', question: 'プロジェクトにはどのくらいかかりましたか？', options: ['一ヶ月', '二ヶ月', '三ヶ月', '六ヶ月'], answer: 2 },
  ],
  ko: [
    { id: 'ko-l-1', text: '죄송한데, 가장 가까운 지하철역 가는 길을 알려주세요.', question: '화자가 무엇을 물어보고 있나요?', options: ['식당 위치', '지하철역 길', '호텔 예약', '비행기 정보'], answer: 1 },
    { id: 'ko-l-2', text: '이 프로젝트를 석 달 동안 준비해 왔는데, 다음 주에 드디어 런칭합니다.', question: '프로젝트 기간은 얼마인가요?', options: ['한 달', '두 달', '석 달', '여섯 달'], answer: 2 },
  ],
};

/**
 * 成就徽章
 */
const BADGES = [
  { id: 'first_login', icon: '🎯', name: '初次登录', desc: '欢迎来到 LanLearn', check: (u) => u.stats.logins >= 1 },
  { id: 'streak_3', icon: '🔥', name: '三日连击', desc: '连续学习3天', check: (u) => computeStreak(u) >= 3 },
  { id: 'streak_7', icon: '⚡', name: '七日连击', desc: '连续学习7天', check: (u) => computeStreak(u) >= 7 },
  { id: 'words_50', icon: '📖', name: '单词达人', desc: '掌握50个单词', check: (u) => u.stats.wordsLearned >= 50 },
  { id: 'words_200', icon: '📚', name: '词汇大师', desc: '掌握200个单词', check: (u) => u.stats.wordsLearned >= 200 },
  { id: 'lesson_5', icon: '🎓', name: '勤奋学子', desc: '完成5节课', check: (u) => u.stats.lessonsCompleted >= 5 },
  { id: 'lesson_20', icon: '🏅', name: '学习标兵', desc: '完成20节课', check: (u) => u.stats.lessonsCompleted >= 20 },
  { id: 'score_90', icon: '⭐', name: '口语新星', desc: '口语得分超过90', check: (u) => u.maxSpeakingScore >= 90 },
  { id: 'listen_10', icon: '👂', name: '听力专家', desc: '完成10次听力训练', check: (u) => u.stats.listenDone >= 10 },
  { id: 'polyglot', icon: '🌍', name: '多语达人', desc: '学习3种语言', check: (u) => (u.stats.languagesStudied || []).length >= 3 },
  { id: 'post_1', icon: '✍️', name: '社区新人', desc: '发布第一个动态', check: (u) => u.stats.posts >= 1 },
  { id: 'post_10', icon: '💬', name: '活跃贡献者', desc: '发布10个动态', check: (u) => u.stats.posts >= 10 },
];

function computeStreak(user) {
  const dates = Object.keys(user.dailyXP || {});
  if (!dates.length) return 0;
  dates.sort().reverse();
  const today = new Date();
  today.setHours(0,0,0,0);
  let streak = 0;
  for (let i = 0; i < dates.length; i++) {
    const d = new Date(dates[i]);
    d.setHours(0,0,0,0);
    const expected = new Date(today);
    expected.setDate(expected.getDate() - i);
    if (d.getTime() === expected.getTime()) streak++;
    else break;
  }
  return streak;
}

const XP_CONFIG = {
  word: 5,
  grammar: 8,
  speaking: 15,
  listening: 10,
  lesson: 30,
  post: 3,
};
