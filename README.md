# StorageSign-Refactored

Paper 向け Minecraft プラグインです。
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
- [位置索引・検索・近接表示の詳細](docs/storage-sign-index.md)
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
| サーバーソフト | Paper 1.21.4 / 1.21.8 / 1.21.11（26.xは検証環境未対応のため保留） |
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
- 同じ内容を持つ登録済みStorageSignアイテムを右クリックすると、看板一枚単位で内容をマージします。
  上限まで一枚分が収まらない場合、そのStorageSignは変更せずマージ元に残します。部分マージでは
  マージできた枚数だけ空StorageSignとして返し、インベントリ満杯時は空StorageSignだけを足元へドロップします。
  未マージの中身を通常アイテムとしてドロップすることはありません。

#### 手動エクスポート（右クリック）

- 空手（または登録内容と異なるアイテム）で StorageSign を右クリックすると、なるべく 1 スタック分のアイテムを足元にドロップします。
- スニーク（Shift）＋右クリックすると、アイテムを 1 個のみドロップします。

#### StorageSign アイテムへの分割

登録済み StorageSign ブロックを、同じ看板素材の空 StorageSign アイテムを持って
右クリックすると、保管内容を手持ちの各空 StorageSign へ均等に分割します。

- ブロック保管数を `B`、手持ちの空 StorageSign 数を `N` とすると、各アイテムへ
  `floor(B / (N + 1))` 個を割り当て、端数はブロック側へ残します。
- 1枚あたりの割当数は通常 `divide-limit`、スニーク時は
  `sneak-divide-limit` が上限です。設定値が `0` 以下なら上限はありません。
- 例: `B=100, N=2` では各アイテムが33個、ブロックに34個残ります。
- `manual-export: false`、保管数が空 StorageSign 数以下、異なる看板素材では分割しません。
- StorageSign アイテム自体を保管しているブロックでは、空 StorageSign は分割ではなく
  sign-in-sign の手動インポートとして扱います。

保管数量の上限は `Integer.MAX_VALUE`（2,147,483,647）です。上限に達する搬入では
空き容量分だけを受け入れ、余剰アイテムは手元・コンテナ・ドロップ側へ残します。

### 保管できるItemMeta

StorageSign は、識別子から完全に復元できる ItemMeta だけを受け入れます。基本Potion、
単一エンチャント本、耐久値付きアイテム、空のシュルカーボックス、飛翔時間だけを持つ花火、
不吉な旗は保管できます。カスタム名・Lore・独自Enchant、複数エンチャント本、
中身入りシュルカーボックス、蜂入りの巣箱、効果付き花火などは、メタデータ消失を防ぐため登録を拒否します。

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

#### /storagesignindex（/ssindex）

ロード済みチャンクのStorageSign位置索引を確認・再構築します。未ロードチャンクはロードしません。
永続化、障害時復旧、負荷特性を含む詳細は[位置索引・検索・近接表示](docs/storage-sign-index.md)を参照してください。

```text
/storagesignindex status
/storagesignindex rebuild
/storagesignindex rebuild all
/storagesignindex rebuild <world>
```

`rebuild`だけの場合はプレイヤーの現在Worldを対象にします。コンソールでは`all`または
World名を指定してください。0以下の設定値は既定値へ戻し、`nearby-display.field-of-view-degrees`
だけは1～360度に丸めます。

索引は`storage-sign-index.bin`へ、サーバー正常終了時と手動再構築完了時に保存されます。
起動時に読み込んだ後、ロード済みチャンクだけを実ワールドから再検証します。

#### /storagesignsearch（/sssearch）

未ロードチャンクを含む保存済み索引から、格納アイテムの完全識別子を検索します。

```text
/storagesignsearch item STONE
/storagesignsearch item POTION --contains
/storagesignsearch item STONE --world world --page 2
```

通常は大文字小文字を無視した完全一致、`--contains`指定時は部分一致です。結果は10件ずつ、
World、座標、数量、ロード状態とともに表示します。結果順は `World UUID -> X -> Y -> Z` の昇順で、`--page` は 1 始まりです。`--world` は現在ロード中のWorld名のみ受け付けます。

#### 保存済み索引CLI・ビューア

保存済みの `storage-sign-index.bin` を読む外部CLIがあります。検索、集計、CSV/JSON出力はCLIで行い、Web画面は独立したviewerで起動します。

```text
python3 tools/storage_sign_index_cli.py inspect
python3 tools/storage_sign_index_cli.py search STONE
python3 tools/storage_sign_index_cli.py search POTION --contains
python3 tools/storage_sign_index_cli.py export --format csv --output storage-sign-index.csv
python3 tools/storage_sign_index_viewer.py --world-map worlds.json
```

詳細は [位置索引・検索・近接表示](docs/storage-sign-index.md) の「外部CLI・ビューア」を参照してください。

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
| `storagesign.index.admin` | OP | 位置索引の状態確認・手動再構築 |
| `storagesign.search.admin` | OP | 保存済み索引のアイテム検索 |

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
| `storage-index.enabled` | `true` | ロード済みチャンクのStorageSign位置索引を維持する |
| `storage-index.rebuild-chunks-per-tick` | `8` | 初回・手動再構築で1tickに走査するチャンク数。0以下なら既定値へ戻す |
| `nearby-display.enabled` | `true` | 停止時に前方のStorageSignへ完全な格納識別子を浮遊表示する |
| `nearby-display.distance` | `6.0` | 前方検索距離。0以下なら既定値へ戻す |
| `nearby-display.field-of-view-degrees` | `90.0` | 前方検索の視野角。1～360度に丸める |
| `nearby-display.idle-delay-ticks` | `10` | 移動と視点変更が止まってから検索するまでのtick数。0以下なら既定値へ戻す |
| `nearby-display.monitor-interval-ticks` | `5` | 位置と視点を比較する間隔。0以下なら既定値へ戻す |
| `nearby-display.max-per-player` | `3` | 1人へ同時表示する最大件数。0以下なら既定値へ戻す |
| `nearby-display.max-searches-per-tick` | `25` | 1tickに処理する停止プレイヤー検索の上限。0以下なら既定値へ戻す |
| `nearby-display.global-label-limit` | `512` | サーバー全体の同時TextDisplay上限。0以下なら既定値へ戻す |
| `admin-search.page-size` | `10` | 管理者検索で1ページに表示する件数。0以下なら既定値へ戻す |
| `admin-search.max-concurrent` | `2` | 同時に実行できる管理者検索数。0以下なら既定値へ戻す |

`nearby-display.enabled: true`でも`storage-index.enabled: false`の場合、近接表示は自動的に
無効になります。索引だけを有効にして近接表示を無効にすることは可能です。同じStorageSignを
複数人が見る場合、TextDisplayは共有され、対象プレイヤーだけに表示されます。表示上限に達したときは、既存表示を維持したまま残件を次回以降に再試行します。

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

ホストに Java、Maven、Node.js を導入せず、Docker だけで実行できます。

```bash
# サーバー API に依存しない単体テスト
./scripts/test.sh unit

# MockBukkit でプラグイン全体をロードする統合テスト
./scripts/test.sh integration

# 全JUnitテストとJaCoCo行・分岐カバレッジ（数値ゲートなし）
./scripts/test.sh coverage

# Paper 1.21.4 / 1.21.8 / 1.21.11 / 26.1.2 / 26.2 を Logger なし・ありで E2E
./scripts/test.sh e2e

# 指定したバージョン・Logger 構成だけ E2E を実行
./scripts/test.sh e2e 1.21.8 with-logger
./scripts/test.sh e2e 1.21.8 without-logger

# 対応済みの同一ワールド更新でPotion PDCと不吉な旗を検証
./scripts/test.sh banner-compat 1.21.11

# 保留中の26.xも含む定義済み経路（26.x環境が利用可能になった後に実行）
./scripts/test.sh banner-compat all

# 上記をすべて実行
./scripts/test.sh all
```

E2E は Paper と Mineflayer クライアントを Docker Compose で起動し、外部 Logger の
未導入時フォールバックと導入時の登録状態、右クリック、
スニーク、ホッパー搬送、ホッパー付きトロッコ、自動収集、特殊アイテム、
サーバー再起動後の永続性を実際のゲーム tick とパケット経路で検証します。
不吉な旗は実物の BannerMeta を搬出・再取込し、8 模様の色と種類、名前、ツールチップ
フラグを各バージョンで検証します。`banner-compat 1.21.11` は旧版で保存した旗、Potionの
NamespacedKey PDC、短縮表示を同一ワールドの次バージョンで読み込み、再取込・再搬出できることを確認します。
StorageSign アイテムの設置は、Mineflayer がカスタム Lore 付き看板の設置応答を扱えない場合、
テストハーネスから実際の `BlockPlaceEvent` を発火して設置リスナーを検証します。
テストサーバーは localhost 限定のオフラインモードで、実 Minecraft アカウントは不要です。
26.1以降はJava 25、それ以前はJava 21のサーバーコンテナを使います。26.2は正式版Minecraftに
対するPaperのexperimental buildを固定して検証します。

通常E2Eのログは `e2e/artifacts/<version>/<logger-mode>/`、アップグレードテストは
`e2e/artifacts/banner-upgrade/<version>/` に保存されます。
テストランナーは成功時のトークン消費を抑えるため、既定では件数、各構成の `PASS`、待機用の
`WAIT_HINT` だけを表示します。成功したE2Eの所要時間は `target/test-artifacts/e2e-timings.tsv` に
構成別の移動平均として保存されます。開始時は保存済み推定時間に30秒を加えて待機し、未完了なら
残件の推定時間だけ待機します。履歴のない構成が含まれる場合、未知部分全体を180秒と見積もります。
Minecraftサーバーの初回起動に入った後だけは1分間隔で確認します。短間隔の空ポーリング結果は
コンテキストへ追加しません。キャッシュを削除すると初回推定へ戻ります。
Maven の詳細は `target/test-artifacts/`、Docker の起動・停止ログは各成果物ディレクトリの
`runner.log` に保存され、失敗したケースだけ診断に使用します。詳細を端末にも表示する場合は
`STORAGESIGN_TEST_VERBOSE=1 ./scripts/test.sh <scope>` を実行してください。
失敗時に端末へ出すログは既定で末尾40行に制限されます。追加情報が必要な場合のみ
`STORAGESIGN_FAILURE_TAIL_LINES=80 ./scripts/test.sh <scope>` のように拡張できます。
JaCoCoのHTMLレポートは `target/site/jacoco/index.html` に生成されます。これはUnit/Integrationで
実行される製品コードを可視化するもので、別プロセスのPaper E2Eはシナリオ単位で管理します。
E2Eには破壊権限とドロップ、看板編集保護、StorageSignアイテムの搬出・再取込も含まれます。
アップグレード時にMinecraftのデータ更新が旧旗のツールチップ非表示フラグを削除する場合は、
8模様と名前による互換性、StorageSignへの再取込、現行版で再搬出した旗へのフラグ再付与を検証します。
テスト用ハーネスは別 JAR であり、本番の StorageSign JAR には含まれません。

Spigot は製品保証およびリリース試験の対象外です。また、新しいMinecraft版で保存・更新した
ワールドを古い版で開くダウングレードは非対応です。更新前に必ずバックアップしてください。

外部 Logger の通常書込みはE2Eで確認します。ディスク枯渇、権限エラー、ローテーション失敗は
外部 Logger 側の障害であるため、`docs/runtime-validation-checklist.md` の障害注入手順で確認します。

### データモデル

`StorageSign` クラスは看板ブロックおよびインベントリアイテム両方の表現を持つイミュータブルな値オブジェクトです。

**看板ブロックのテキスト形式（4 行）**

```
行 0: "StorageSign"
行 1: 表示用識別子（Vanilla標準幅90px以内）
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

看板の行1は、全Material・特殊種別・設定追加識別子をVanilla標準幅90px以内に短縮します。
完全な識別子は看板のPersistentDataContainerに保存されるため、短縮表示からデータを推測せず復元できます。
Potionは従来の`potion_identifier`も維持し、StorageSignアイテムには
`POTION:minecraft:healing`のようなRegistryキー形式を保存します。PDCを持たない旧データも従来形式から読み込み、
次回の看板更新・アイテム化・再設置時に正規キーを付与します。

### 設定の拡張

`config.yml` の以下のテーブルをコードを変更せずに拡張できます。

**`item-identifier-aliases`**  
識別子の別名を現行の Material 名にマッピングします。MC のリネームや旧データの移行に使用します。

```yaml
item-identifier-aliases:
  SIGN: OAK_SIGN
  MY_OLD_NAME: NEW_MATERIAL_NAME
```

**`potion-key-aliases`**
Minecraft側でPotionのRegistryキーが変更・削除された場合に、旧キーを現行キーへ移行します。
元のキーが実行中Registryに存在する場合は別名を適用しません。

```yaml
potion-key-aliases:
  "minecraft:old_potion": "minecraft:replacement_potion"
```

**`brewing-ingredient-identifiers`**
将来版やサーバー拡張で追加された醸造材料を、製品コード変更なしで追加します。

```yaml
brewing-ingredient-identifiers:
  - MODDED_BREWING_INGREDIENT
```

**`virtual-item-identifiers`**  
実在しない識別子をバックエンドの `MATERIAL[:damage]` にマッピングします。  
サーバー独自の仮想アイテムや旧マーカーの移行に使用します。

```yaml
virtual-item-identifiers:
  EmptySign: OAK_SIGN:1
  HorseEgg: END_PORTAL:1
```
