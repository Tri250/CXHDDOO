/**
 * 登录 / 注册
 */
const Auth = {
  init() {
    // 切换 tab
    document.querySelectorAll('[data-auth-tab]').forEach(btn => {
      btn.addEventListener('click', () => this.switchTab(btn.dataset.authTab));
    });
    // 表单提交
    document.getElementById('login-form').addEventListener('submit', (e) => {
      e.preventDefault();
      this.login();
    });
    document.getElementById('register-form').addEventListener('submit', (e) => {
      e.preventDefault();
      this.register();
    });
    // 登出
    document.getElementById('logout-btn').addEventListener('click', () => {
      State.logout();
      App.navigate('auth');
      App.render();
    });
  },
  switchTab(tab) {
    document.querySelectorAll('[data-auth-tab]').forEach(b => b.classList.toggle('active', b.dataset.authTab === tab));
    document.getElementById('login-form').classList.toggle('active', tab === 'login');
    document.getElementById('register-form').classList.toggle('active', tab === 'register');
    document.getElementById('auth-msg').textContent = '';
    document.getElementById('auth-msg').className = 'auth-msg';
  },
  showMsg(msg, type) {
    const el = document.getElementById('auth-msg');
    el.textContent = msg;
    el.className = 'auth-msg ' + (type || '');
  },
  login() {
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value;
    if (!username || !password) {
      this.showMsg('请输入用户名和密码', 'error');
      return;
    }
    const result = State.loginUser(username, password);
    if (!result.success) {
      this.showMsg(result.message, 'error');
      return;
    }
    this.showMsg('登录成功，正在进入…', 'success');
    setTimeout(() => {
      App.navigate('home');
      App.render();
    }, 400);
  },
  register() {
    const username = document.getElementById('reg-username').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;
    const native = document.getElementById('reg-native').value;
    if (username.length < 3) { this.showMsg('用户名至少3位', 'error'); return; }
    if (password.length < 6) { this.showMsg('密码至少6位', 'error'); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { this.showMsg('请输入有效邮箱', 'error'); return; }
    const result = State.registerUser(username, email, password, native);
    if (!result.success) {
      this.showMsg(result.message, 'error');
      return;
    }
    this.showMsg('注册成功，正在登录…', 'success');
    const loginRes = State.loginUser(username, password);
    setTimeout(() => {
      if (loginRes.success) {
        App.navigate('home');
        App.render();
      }
    }, 500);
  },
};
