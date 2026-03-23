# Remote Camera MCP Server

[English](README.md)

TP-Link Tapo等のWi-Fiカメラを MCP (Streamable HTTP) 経由で制御して、AIに「目・首」を与えるサーバー。

Scala 3 製。外部コマンド依存なし（JDK のみで動作）。

## 対応カメラ

- TP-Link Tapo C210 (3MP)
- TP-Link Tapo C220 (4MP)
- その他 ONVIF 対応の Tapo パン・チルトカメラ

## できること

| ツール | 説明 |
|--------|------|
| `see` | 今見えてる景色を撮影 |
| `look_left` | 左を向く（度数指定可） |
| `look_right` | 右を向く |
| `look_up` | 上を向く |
| `look_down` | 下を向く |
| `look_around` | 部屋を見渡す（4方向撮影） |
| `camera_info` | カメラ情報取得 |
| `camera_presets` | プリセット位置一覧 |
| `camera_go_to_preset` | プリセット位置に移動 |

## クイックスタート

### 必要なもの

- **JDK 21+**
- **sbt**（ビルドツール）

### 1. 設定

```bash
cp .env.example .env
```

`.env` にカメラの認証情報を設定：

```
CAMERA_HOST=192.168.1.100
CAMERA_USERNAME=your-name
CAMERA_PASSWORD=your-password
```

### 2. 起動

```bash
# Linux / macOS
./start.sh

# Windows
start.bat

# または sbt で直接
sbt run
sbt "run --port 3001"    # ポート指定
```

サーバーが `http://0.0.0.0:8000/mcp` で起動します。

## MCP クライアントから接続する

Streamable HTTP トランスポートのみ対応。エンドポイントは `http://<host>:8000/mcp`。

### Claude Desktop

`claude_desktop_config.json` に追加：

```json
{
  "mcpServers": {
    "remote-camera": {
      "type": "streamable-http",
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

### Claude Code

`.mcp.json` に追加：

```json
{
  "mcpServers": {
    "remote-camera": {
      "type": "streamable-http",
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

## 環境変数一覧

| 変数名 | 必須 | デフォルト | 説明 |
|--------|------|-----------|------|
| `CAMERA_HOST` | Yes | - | カメラのIPアドレス |
| `CAMERA_USERNAME` | Yes | - | カメラアカウントのユーザー名 |
| `CAMERA_PASSWORD` | Yes | - | カメラアカウントのパスワード |
| `CAMERA_ONVIF_PORT` | No | `2020` | ONVIFポート |
| `CAMERA_MOUNT_MODE` | No | `normal` | `normal` or `ceiling`（天吊り時はパン反転） |
| `CAPTURE_DIR` | No | `/tmp/remote-camera-mcp` | 画像保存ディレクトリ |

## 技術スタック

| ライブラリ | 用途 |
|-----------|------|
| **http4s-ember** | HTTP サーバー & クライアント |
| **circe** | JSON（MCP JSON-RPC プロトコル） |
| **scala-xml** | ONVIF SOAP 通信 |
| **cats-effect** | 非同期 IO |

## カメラの初期設定

### 1. Tapoアプリでカメラを追加

1. スマホに「TP-Link Tapo」アプリをインストール
2. Tapoアカウントを作成（メールアドレスとパスワード）
3. アプリから「デバイスを追加」→ カメラを選択
4. カメラの電源を入れ、アプリの指示に従ってWiFi接続

### 2. カメラのIPアドレスを調べる

| 方法 | 手順 |
|------|------|
| **Tapoアプリ** | カメラ設定 → デバイス情報 → IPアドレス |
| **ルーター管理画面** | 接続機器一覧から「Tapo_C210」等を探す |
| **nmapコマンド** | `nmap -sn 192.168.1.0/24` |

> **Tips**: ルーターでDHCP予約（IP固定）を設定しておくと、カメラ再起動後もIPアドレスが変わらず便利です

### 3. カメラのアカウントを作る

1. Tapoアプリ → ホーム → カメラを選択 → 右上の歯車 → 「高度な設定」
2. 「カメラのアカウント」をオン → 「アカウント情報」
3. ユーザー名とパスワードを設定（TP-Linkアカウントとは別のローカルアカウント）

## トラブルシューティング

- **カメラに接続できない**: カメラとPCが同じネットワーク上にあるか確認。ファイアウォールもチェック
- **認証エラー**: TP-Linkクラウドアカウントではなく、カメラ固有のローカルアカウントを使う
- **画像が取得できない**: カメラのファームウェアを最新に更新。カメラを再起動

## 注意事項

- カメラはローカルネットワーク内からのみアクセス可能です
- 認証情報（`.env`ファイル）は絶対にGitにコミットしないでください

## ライセンス

MIT License
