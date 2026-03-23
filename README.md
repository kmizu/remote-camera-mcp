# Remote Camera MCP Server

TP-Link Tapo等のWi-Fiカメラを MCP (Streamable HTTP) 経由で制御して、AIに「目・首・耳」を与えるサーバー。

Scala 3 製。JVM でも Scala Native でも動く。

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
| `listen` | マイクで周囲の音を聞く（Whisper文字起こし対応） |
| `camera_info` | カメラ情報取得 |
| `camera_presets` | プリセット位置一覧 |
| `camera_go_to_preset` | プリセット位置に移動 |

## セットアップ

### 1. カメラの初期設定（Tapoアプリ）

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

### 4. 環境変数の設定

```bash
cp .env.example .env
```

`.env` を編集（必須項目のみ）：

```
TAPO_CAMERA_HOST=192.168.1.100    # カメラのIPアドレス
TAPO_USERNAME=your-name            # カメラアカウントのユーザー名
TAPO_PASSWORD=your-password        # カメラアカウントのパスワード
```

その他のオプションは `.env.example` を参照。

### 5. ビルドと実行

#### 必要なもの

- **JDK 11+**（JVM実行時）
- **sbt**（ビルドツール）
- **ffmpeg**（画像キャプチャ・音声録音）
- **whisper CLI**（音声文字起こし、オプション）

#### JVM で実行

```bash
sbt appJVM/run                         # サーバー起動（http://0.0.0.0:8000/mcp）
sbt "appJVM/run -- --port 3001"        # ポート指定
```

#### Scala Native でビルド・実行

```bash
# 追加で必要: clang, libunwind-dev
sbt appNative/nativeLink               # ネイティブバイナリ生成
./appNative/target/scala-3.3.4/remote-camera-mcp  # 直接実行
```

#### コンパイルのみ

```bash
sbt appJVM/compile
sbt appNative/compile
```

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
| `TAPO_CAMERA_HOST` | Yes | - | カメラのIPアドレス |
| `TAPO_USERNAME` | Yes | - | カメラアカウントのユーザー名 |
| `TAPO_PASSWORD` | Yes | - | カメラアカウントのパスワード |
| `TAPO_ONVIF_PORT` | No | `2020` | ONVIFポート |
| `TAPO_MOUNT_MODE` | No | `normal` | `normal` or `ceiling`（天吊り時は映像180°回転＋パン反転） |
| `TAPO_STREAM_URL` | No | 自動検出 | RTSPストリームURL |
| `CAPTURE_DIR` | No | `/tmp/remote-camera-mcp` | 画像保存ディレクトリ |
| `MIC_SOURCE` | No | `camera` | マイク音源 `camera`（RTSP）or `local`（PC内蔵マイク） |

## 技術スタック

| ライブラリ | 用途 |
|-----------|------|
| **http4s-ember** | HTTP サーバー & クライアント（JVM/Native両対応） |
| **circe** | JSON（MCP JSON-RPC プロトコル） |
| **scala-xml** | ONVIF SOAP 通信 |
| **cats-effect** | 非同期 IO |

## 外部依存

- **ffmpeg** — RTSP経由のキャプチャ・音声録音に使用。システムにインストールが必要
- **JDK 11+**（JVM実行時）
- **clang + libunwind-dev**（Scala Native ビルド時）

## トラブルシューティング

### カメラに接続できない

- カメラとPCが同じネットワーク上にあるか確認
- IPアドレスが正しいか確認（Tapoアプリで再確認）
- ファイアウォールが通信をブロックしていないか確認

### 認証エラー

- カメラアカウントのユーザー名とパスワードが正しいか確認
- TP-Linkクラウドアカウントではなく、カメラ固有のローカルアカウントを使っているか確認

### 画像が取得できない

- カメラのファームウェアを最新に更新
- カメラを再起動

## 注意事項

- カメラはローカルネットワーク内からのみアクセス可能です
- 認証情報（`.env`ファイル）は絶対にGitにコミットしないでください

## ライセンス

MIT License
