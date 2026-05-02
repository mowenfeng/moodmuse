# 部署（systemd）

## 1) 复制 service 文件

```bash
sudo cp deploy/moodmuse-backend.service /etc/systemd/system/
```

## 2) 重载并启用

```bash
sudo systemctl daemon-reload
sudo systemctl enable moodmuse-backend
sudo systemctl restart moodmuse-backend
```

## 3) 查看状态与日志

```bash
sudo systemctl status moodmuse-backend
sudo journalctl -u moodmuse-backend -f
```

## 4) 验证

```bash
curl -s http://127.0.0.1:8010/health
```
