# MoodMuse AI

一个“情绪音乐生成器”MVP：  
Android 输入情绪文案 -> 后端增强 Prompt -> 调用 MiniMax 生成音乐 -> 试听免费 -> 导出前付费（当前为 mock 支付）。

## 项目结构

```text
app/                  Android 客户端（Kotlin + Compose + MVVM）
backend/              FastAPI 后端
deploy/               云端部署模板（systemd）
```

## 功能现状

- 情绪快捷按钮 + 文本输入
- 生成任务创建（立即返回 `task_id`）
- 客户端轮询任务状态（2.5 秒一次）
- 完成后播放 `preview_url`
- 导出前付费拦截（当前 mock-pay）
- 已支持真实 MiniMax 接口（可通过 `.env` 开关 mock/real）

## 本地运行（后端）

```bash
cd backend
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
uvicorn main:app --host 0.0.0.0 --port 8000 --log-level debug
```

健康检查：

- `http://127.0.0.1:8000/health`

## 云端运行（阿里云）

后端建议固定端口 `8010`：

```bash
cd ~/moodmuse-backend
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8010 --log-level debug
```

确保：

- 安全组放行 TCP `8010`
- 手机可访问 `http://<你的公网IP>:8010/health`

## Android 配置

`local.properties`：

```properties
MOODMUSE_BASE_URL=http://8.130.136.67:8010/
```

说明：

- 模拟器访问本机后端：`http://10.0.2.2:8000/`
- 真机访问云端：用公网 IP + 端口

## MiniMax 配置

`backend/.env` 核心变量：

```env
MINIMAX_API_KEY=your_key
MINIMAX_USE_MOCK=false
MINIMAX_BASE_URL=https://api.minimaxi.com
MINIMAX_MODEL=music-2.6
MINIMAX_TIMEOUT_S=120
MINIMAX_POLL_INTERVAL_S=3
MINIMAX_HTTP_TIMEOUT_S=300
MINIMAX_OUTPUT_FORMAT=url
MINIMAX_AUDIO_SAMPLE_RATE=44100
MINIMAX_AUDIO_BITRATE=256000
MINIMAX_AUDIO_FORMAT=mp3
```

> 如果只想本地联调流程，可将 `MINIMAX_USE_MOCK=true`。

## MiniMax 自测脚本

```bash
cd backend
source .venv/bin/activate
python3 scripts/minimax_selftest.py --env-file /root/moodmuse-backend/.env --http-timeout-s 600
```

## 常见问题

- `CLEARTEXT communication not permitted`  
  已通过 `network_security_config` 放行目标域名；确认安装的是新包。

- `timeout`  
  Android 已将 `readTimeout` 提升到 180s；后端 MiniMax HTTP 超时可通过 `MINIMAX_HTTP_TIMEOUT_S` 调大。

- `invalid api key (2049)`  
  检查 key 是否有效、是否泄露后已轮换、`MINIMAX_BASE_URL` 是否与 key 所属平台一致（`api.minimaxi.com`）。

## 下一步建议

- 接入真实支付：订单、回调验签、幂等状态机
- 下载鉴权升级：短时签名 URL + 用户归属校验
- 部署升级：systemd + nginx + HTTPS
