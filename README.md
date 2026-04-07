# PSage

> Burp Suite AI 辅助安全分析插件

基于 Montoya API 开发的 Burp Suite 插件，结合 AI 大模型对 HTTP 数据包进行**漏洞风险分析**和**参数语义分析**，辅助渗透测试与业务逻辑漏洞挖掘。

---

## 功能

### 漏洞风险分析
- 将请求包发送至 AI，覆盖 **15 个攻击面**并行分析（SQLi / SSRF / IDOR / XSS / 越权 / JWT 等）
- 输出漏洞类型、风险等级、证据字段、Payload 建议
- 多轮提示词工程 + Java 多级过滤降低误报

### 参数智能分析
- 支持**请求包 / 返回包 / 报错 / 综合**四种分析模式
- 自动翻译参数名、解读业务含义，对报错响应做安全解读
- 辅助业务逻辑漏洞挖掘

---

## 架构

```
Burp Suite（右键 Send to PSage）
        ↓
   PSage 插件（本项目）
        ↓
   PSage-backend 后端（SpringBoot + 通义千问）
```

插件为**主动模式**，只有用户手动右键发送的包才进入分析流水线，不被动监听流量。

---

## 环境要求

| 组件 | 版本 |
|------|------|
| Burp Suite Professional | v2024.10.3 |
| Montoya API | 2024.10.1 |
| JDK | 17+ |
| PSage-backend 后端 | 本地运行，端口 9090 |

---

## 构建

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home gradle jar
```

产物：`build/libs/P-Sage.jar`

---

## 安装

1. 启动 PSage-backend 后端（确保 `localhost:9090` 可访问）
2. 打开 Burp Suite → `Extensions` → `Add`
3. 类型选 `Java`，选择 `P-Sage.jar`
4. 插件加载后在顶栏出现 `P-Sage` Tab

---

## 使用

1. 在 Proxy / Repeater 等任意位置右键请求包 → `Send to P-Sage`
2. 切换到 P-Sage Tab，在右侧登录区输入账号密码登录
3. 左侧选中数据包，点击**漏洞分析**或**参数分析**对应按钮
4. 分析结果实时展示在右侧面板，每个数据包独立缓存

---

## 后端接口

| 功能 | 接口 |
|------|------|
| 登录 | `POST /api/auth/login` |
| 漏洞分析 | `POST /api/ai/chat` |
| 参数分析 | `POST /api/ai/param-analyze` |

后端项目：[PSage-backend](https://github.com/chunliunai/PSage-backend)
