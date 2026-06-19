# StorageSign-Refactored

Spigot/Paper 向け Minecraft プラグインです。  
看板（Sign）に保管するアイテムの種類と数量を記録することで、ラージチェストやシュルカーボックスを「品目専用倉庫」として管理できます。ホッパーや投げ捨てによる自動入出庫、右クリックによる手動ピックアップなど、倉庫整理を自動化・効率化する機能を提供します。

---

## 目次

- [ベース版との差分（snowpegeon/StorageSign 比較）](#ベース版との差分snowpegeonstoragesign-比較)
  - [追加された機能](#追加された機能)
  - [削除・廃止された機能](#削除廃止された機能)
- [ユーザー向けガイド](#ユーザー向けガイド)
  - [動作環境](#動作環境)
  - [インストール](#インストール)
  - [StorageSign の作り方](#storagesign-の作り方)
  - [基本的な使い方](#基本的な使い方)
  - [自動入出庫](#自動入出庫)
  - [コマンド](#コマンド)
  - [権限一覧](#権限一覧)
  - [主な設定項目](#主な設定項目)
- [開発者向けガイド](#開発者向けガイド)
  - [プロジェクト構成](#プロジェクト構成)
  - [ビルド手順](#ビルド手順)
  - [テスト](#テスト)
  - [データモデル](#データモデル)
  - [設定の拡張](#設定の拡張)

---

## ベース版との差分（snowpegeon/StorageSign 比較）

この章は、ベース版 [snowpegeon/StorageSign](https://github.com/snowpegeon/StorageSign) と本リポジトリを比較し、機能差分を整理したものです。

### 追加された機能

- `config.yml` に `unregister-on-empty` を追加
  - 保管数が 0 になった際に登録を解除するかを切り替え可能。
- `config.yml` に `item-identifier-aliases` を追加
  - 旧識別子や別名を現在の Material 名にマッピングでき、リネーム耐性を向上。
- `config.yml` に `virtual-item-identifiers` を追加
  - 実在しない識別子を `MATERIAL[:damage]` にマッピング可能。
- 吊り看板系（`_HANGING_SIGN` / `_WALL_HANGING_SIGN`）の取り扱いを実装
  - 隣接判定や向き判定を含め、通常看板と同様に対象化。
- 互換性・可搬性向上のためのレジストリ層を追加
  - `MaterialRegistry` / `LegacyNameRegistry` / `DyeRegistry` により、素材解決と旧名互換を整理。
- テストスイートを追加
  - `StorageSign` 本体とレジストリ・ポーション補助ロジックのユニットテストを整備。
- `/storagesigngive`（エイリアス: `/ssgive`）コマンドを追加
  - クリエイティブモードのプレイヤーが任意の識別子・数量・看板種類で StorageSign アイテムを直接取得できる。
- ホッパー付きトロッコ・チェスト付きトロッコ・チェスト付きボートへの自動入出庫対応を追加
  - `InventoryMoveItemEvent` の送信元・受信先としてこれらのエンティティインベントリを認識し、隣接 SS との自動インポート/エクスポートが機能する。

### 削除・廃止された機能

- 外部プラグイン依存を任意化
  - `Logger` は `softdepend` とし、`FarmNBT` と `WorldGuard` の依存宣言は削除。
  - [`teruteru128/logger`](https://github.com/teruteru128/logger) がサーバーに導入済みなら優先して使用し、未導入または初期化失敗時は Bukkit/JDK 標準ロガーへ自動的にフォールバック。
  - 旧版で `FarmNBT` が供給していたバージョン別 SNBT は使用せず、不吉な旗を Bukkit API から構築する。API 構築に失敗した場合も白旗へ変換せず、1 ティック後から 5 秒間隔でメタの復旧を自動的に再試行する。復旧までは搬出せず、保管数も変更しない。
  - `WorldGuard` は未使用だったため削除。
- 外部 Logger 連携と標準ロガーのフォールバックを共通ロギング層で統一。
- ビルド時の `project.properties` + `maven-resources-plugin` コピー運用を廃止
  - 現行は `maven-shade-plugin` によるパッケージング中心へ移行。

注: ここでの「削除・廃止」は、主に公開設定・依存宣言・実装構成として確認できる差分を示します。
内部実装の微細な最適化差分は随時更新されるため、必要に応じてコード比較を再実施してください。

---

## ユーザー向けガイド

### 動作環境

| 項目 | 要件 |
|---|---|
| サーバーソフト | Spigot 1.21.4 以降 / Paper 1.21.4 以降 |
| Java | 21 以降 |

### インストール

1. [Releases](../../releases) から最新の `.jar` ファイルをダウンロードします。
2. サーバーの `plugins/` フォルダに配置します。
3. サーバーを起動すると `plugins/StorageSign-Refactored/config.yml` が生成されます。

外部 Logger 連携を使う場合は、[`teruteru128/logger`](https://github.com/teruteru128/logger) の Logger プラグインも `plugins/` に配置してください。Logger がなくても StorageSign は起動し、標準ロガーを使用します。

### StorageSign の作り方

デフォルトのクラフトレシピ:

```
[チェスト]       [チェスト]         [チェスト]
[チェスト]       [看板]             [チェスト]
[チェスト]       [チェスト]         [チェスト]
```

`config.yml` の `hardrecipe: true` にすると下段中央のチェストがエンダーチェストに変わります。

### 基本的な使い方

#### StorageSign を看板に設置する

1. StorageSign アイテムを手に持ちます。
2. 任意のブロックに看板として設置します。アイテムに保持されていた登録情報がそのまま書き込まれます。
3. **空の StorageSign** の場合は、登録したいアイテムを手に持って看板を右クリックするとアイテム種別が記録されます。
4. 以降は StorageSign に隣接するコンテナを対象に、ホッパー等の搬送ブロックによる自動入出庫が機能します。

#### 手動インポート（右クリック）

- 登録されたアイテムを手に持って StorageSign を右クリックすると、インベントリ内の合致するアイテムをすべて格納します。
- スニーク（Shift）＋右クリックすると、手持ちスロットのアイテムのみを格納します。

#### 手動エクスポート（右クリック）

- 空手（または登録内容と異なるアイテム）で StorageSign を右クリックすると、なるべく 1 スタック分のアイテムを足元にドロップします。
- スニーク（Shift）＋右クリックすると、アイテムを 1 個のみドロップします。

### 自動入出庫

| 機能 | 説明 | 設定キー |
|---|---|---|
| 自動インポート | 搬送ブロック（ホッパー/ドロッパー/ディスペンサー/クラフター/ホッパー付きトロッコ/チェスト付きトロッコ/チェスト付きボート）がアイテムをコンテナへ押し込む際、コンテナがすでに満杯なら超過分を隣接 StorageSign が吸収 | `auto-import` |
| 自動エクスポート | 搬送ブロック（ホッパー/ドロッパー/ディスペンサー/クラフター/ホッパー付きトロッコ/チェスト付きトロッコ/チェスト付きボート）がコンテナからアイテムを引き出すと、隣接 StorageSign が保管数からコンテナを補充 | `auto-export` |
| 自動収集 | 登録済みの StorageSign アイテムをメインハンドまたはオフハンドに持った状態でドロップアイテムに触れると、保管数に自動加算 | `autocollect` |

### コマンド

#### /storagesigngive（/ssgive）

クリエイティブモードのプレイヤーに StorageSign アイテムを付与します。

**使い方**

```
/storagesigngive <itemIdentifier> <amount> [signType]
/ssgive <itemIdentifier> <amount> [signType]
```

| 引数 | 必須 | 説明 |
|---|---|---|
| `itemIdentifier` | ○ | アイテム識別子（例: `STONE`、`POTION:HEAL:0`、`ENCHBOOK:sharp:5`） |
| `amount` | ○ | 保管数量（0 以上の整数） |
| `signType` | - | 看板の素材（省略時: `OAK_SIGN`。例: `SPRUCE_SIGN`、`BIRCH_SIGN`） |

**実行条件**

- プレイヤーがクリエイティブモードであること
- 権限 `storagesign.give`（デフォルト: 全員）

**使用例**

```
/ssgive STONE 128
/ssgive ENCHBOOK:sharp:5 10 OAK_SIGN
```

### 権限一覧

| 権限ノード | デフォルト | 説明 |
|---|---|---|
| `storagesign.*` | OP | すべての権限を付与 |
| `storagesign.use` | 全員 | StorageSign のインタラクション |
| `storagesign.craft` | 全員 | クラフトの許可 |
| `storagesign.place` | 全員 | 看板としての設置 |
| `storagesign.break` | 全員 | StorageSign ブロックの破壊 |
| `storagesign.give` | 全員 | /storagesigngive（/ssgive）コマンドの使用 |
| `storagesign.autocollect` | 全員 | 自動収集 |

### 主な設定項目

`plugins/StorageSign-Refactored/config.yml` で変更できます。

| キー | デフォルト | 説明 |
|---|---|---|
| `log-level` | `INFO` | ログレベル（`OFF / ERROR / WARN / INFO / DEBUG / TRACE / ALL`、旧JUL名にも対応） |
| `manual-import` | `true` | 手動インポートの有効化 |
| `manual-export` | `true` | 手動エクスポートの有効化 |
| `auto-import` | `true` | 自動インポート（搬送ブロック対応）の有効化 |
| `auto-export` | `true` | 自動エクスポート（搬送ブロック対応）の有効化 |
| `autocollect` | `true` | ドロップアイテム自動収集の有効化 |
| `hardrecipe` | `false` | 難易度の高いクラフトレシピを使用 |
| `divide-limit` | `345600` | StorageSign アイテム分割時に 1 枚の空 SS に割り当てる最大数量 |
| `sneak-divide-limit` | `34560` | スニーク時の StorageSign 分割時に 1 枚の空 SS に割り当てる最大数量 |
| `max-stack-size` | `16` | StorageSign アイテムのスタック上限 |
| `unregister-on-empty` | `false` | 残数が 0 になったときに登録を解除するか |
| `no-bud` | `false` | BUD パルスによる看板破壊を防止する |
| `falling-block-itemSS` | `false` | 落下ブロック着地時に隣接する StorageSign をアイテム化してドロップするか |
| `banner-debug` | `false` | TRACE時にメインハンドで右クリックしたアイテムの生ItemMetaを出力する |

ログメッセージには `[Class#operation]` 形式で発生箇所が付きます。通常運用では `INFO`、
問題調査ではまず `DEBUG` を使用してください。`TRACE` は自動搬送の詳細や
`banner-debug` の生ItemMetaを出力するため、必要な期間だけ有効にしてください。

---

## 開発者向けガイド

### プロジェクト構成

```
src/
├── main/java/storagesign/
│   ├── StorageSign.java          # データモデル（イミュータブル）
│   ├── StorageSignPlugin.java    # プラグインメインクラス
│   ├── ConfigLoader.java         # config.yml のロード
│   ├── adjacency/                # 看板とコンテナの隣接判定ルール群
│   ├── command/
│   │   └── SsGiveCommand.java         # /storagesigngive コマンド処理
│   ├── item/
│   │   ├── EnchantHelper.java         # エンチャント本の識別子処理
│   │   ├── OminousBottleHelper.java   # 不吉なビンの識別子処理
│   │   ├── PotionHelper.java          # ポーション系の識別子処理
│   │   └── SpecialCaseItemSupport.java # 特殊アイテムの分岐ロジック
│   ├── listener/                 # イベントリスナー群
│   ├── registry/
│   │   ├── DyeRegistry.java          # 染料の互換名マッピング
│   │   ├── LegacyNameRegistry.java   # レガシー識別子の解決
│   │   └── MaterialRegistry.java     # Material の検索・解決
│   └── task/
│       └── ExportSignTask.java        # 1-tick 遅延エクスポートタスク（同期）
└── test/java/storagesign/        # 単体テスト
```

### ビルド手順

**必要なもの**

- JDK 21 以降
- Maven 3.8 以降

```bash
# 依存関係の解決とビルド
mvn package

# 生成される jar（shade 済み）
target/StorageSign-Refactored-<version>.jar
```

### テスト

```bash
mvn test
```

テストは `src/test/java/storagesign/` にあります。サーバー API に依存しないユニットテストのみ含まれており、Spigot/Paper のモックは不要です。

### データモデル

`StorageSign` クラスは看板ブロックおよびインベントリアイテム両方の表現を持つイミュータブルな値オブジェクトです。

**看板ブロックのテキスト形式（4 行）**

```
行 0: "StorageSign"
行 1: アイテム識別子（例: STONE, POTION:HEAL:0）
行 2: 保管数量（数値文字列）
行 3: サマリー（例: "1LC 2s 3"、LC=ラージチェスト換算・s=スタック・残は個数）
```

**アイテム識別子の形式**

| 種別 | 形式例 |
|---|---|
| 通常アイテム | `STONE` / `STONE:0` |
| ポーション | `POTION:HEAL:0` |
| スプラッシュポーション | `SPOTION:REGEN:1` |
| 残留ポーション | `LPOTION:HEAL:2` |
| エンチャント本 | `ENCHBOOK:sharp:5` |
| 不吉なビン | `OMINOUS_BOTTLE:2` |

### 設定の拡張

`config.yml` の以下のテーブルをコードを変更せずに拡張できます。

**`item-identifier-aliases`**  
識別子の別名を現行の Material 名にマッピングします。MC のリネームや旧データの移行に使用します。

```yaml
item-identifier-aliases:
  SIGN: OAK_SIGN
  MY_OLD_NAME: NEW_MATERIAL_NAME
```

**`virtual-item-identifiers`**  
実在しない識別子をバックエンドの `MATERIAL[:damage]` にマッピングします。  
サーバー独自の仮想アイテムや旧マーカーの移行に使用します。

```yaml
virtual-item-identifiers:
  EmptySign: OAK_SIGN:1
  HorseEgg: END_PORTAL:1
```
