# 清风传奇三端版 - 构建指南

## 📋 系统要求

### 开发环境
- **操作系统**: Windows 10/11 (必须，因为使用了Windows Forms和DirectX)
- **.NET SDK**: 8.0 或更高版本
- **Visual Studio 2022** (推荐，包含完整的Windows Forms支持) 或 VS Code

### 运行时环境
- 服务端: Windows 10/11 或 Windows Server
- 客户端: Windows 10/11
- Android客户端: Android 5.0+
- iOS客户端: iOS 14+

---

## 🚀 在Windows上构建项目

### 1. 安装.NET SDK
从 [https://dotnet.microsoft.com/download](https://dotnet.microsoft.com/download) 下载并安装 .NET 8.0 SDK

### 2. 克隆或获取项目
```bash
cd 你的项目目录
```

### 3. 恢复项目依赖
```bash
dotnet restore
```

### 4. 构建方案一：使用Visual Studio (推荐)
1. 打开 `Legend of Mir.sln` 解决方案
2. 选择 `Release` 配置
3. 右键点击解决方案 -> "生成解决方案"

### 5. 构建方案二：使用命令行

#### 构建服务端
```bash
cd Server.MirForms
dotnet build --configuration Release
```

#### 构建Windows客户端
```bash
cd Client_VorticeDX11
dotnet build --configuration Release
```

#### 构建Shared库
```bash
cd Shared
dotnet build --configuration Release
```

---

## 📦 输出目录

构建成功后，exe文件会在以下位置：

- **服务端**: `Build/Server/Server.exe`
- **Windows客户端**: `Build/Client_VorticeDX11/Client.exe`
- **Shared库**: `Shared/bin/Release/Shared.dll`

---

## 🔧 项目配置

### 修改global.json (如果需要)
项目中的 [global.json](file:///workspace/global.json) 已设置为使用.NET 8.0，无需修改。

### 修改应用标题
- Windows客户端标题: 在 [Client_VorticeDX11/Forms/CMain.Designer.cs](file:///workspace/Client_VorticeDX11/Forms/CMain.Designer.cs#L43) 中设置
- Android应用名称: 在 [Client_MonoGame.Android/Resources/Values/Strings.xml](file:///workspace/Client_MonoGame.Android/Resources/Values/Strings.xml) 中设置

---

## 📱 移动端构建

### Android客户端
需要在Visual Studio中安装Android开发工具，然后：
```bash
cd Client_MonoGame.Android
dotnet build --configuration Release
```

### iOS客户端
需要Mac电脑和Xcode：
```bash
cd Client_MonoGame.iOS
dotnet build --configuration Release
```

---

## 🐛 常见问题

### 1. 找不到项目文件
解决方案中引用了一些工具项目（Tools/），如果缺失不影响主项目构建，可在解决方案管理器中移除。

### 2. 组件引用缺失
确保 [Components](file:///workspace/Components) 目录中的所有DLL文件都存在。

### 3. DirectX相关错误
Windows客户端需要支持DirectX 11的显卡和最新的DirectX运行时。

---

## 📝 当前进度

✅ Shared库已成功编译 (跨平台)
✅ Server.Library已成功编译 (跨平台)
⚠️ Server.MirForms (服务端UI) - 需要Windows构建
⚠️ Client_VorticeDX11 (Windows客户端) - 需要Windows构建
⚠️ 移动端项目 - 需要对应平台构建

---

## 💡 建议

对于完整的项目开发和构建，建议在Windows 10/11系统上使用Visual Studio 2022，这样可以：
1. 获得完整的设计器支持
2. 轻松调试和测试
3. 可以发布所有平台的版本
