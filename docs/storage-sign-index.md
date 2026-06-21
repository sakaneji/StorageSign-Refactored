# StorageSign位置索引・検索・近接表示

この文書では、StorageSignの位置索引、永続化、管理者検索、プレイヤー向け近接表示を説明します。

## 機能の関係

位置索引は、各StorageSignについて次の最終確認済み情報を保持します。

- World UUID
- ブロック座標（X、Y、Z）
- 完全な格納識別子
- 保管数量
- 最終確認時刻

管理者検索と近接表示はこの索引を利用します。`storage-index.enabled: false`の場合、索引の読込・更新・保存を行わず、近接表示と管理者検索も無効になります。索引だけを有効にして近接表示を無効にすることはできます。

設定値のうち、`storage-index.rebuild-chunks-per-tick`、`nearby-display.idle-delay-ticks`、`nearby-display.monitor-interval-ticks`、`nearby-display.max-per-player`、`nearby-display.max-searches-per-tick`、`nearby-display.global-label-limit`、`admin-search.page-size`、`admin-search.max-concurrent` は 0 以下なら既定値へ戻します。`nearby-display.field-of-view-degrees` は 1～360 度に丸めます。距離は 0 以下なら既定値へ戻します。

## 起動から終了までの動作

1. 起動時に`plugins/StorageSign-Refactored/storage-sign-index.bin`を読み込みます。
2. 現在ロード済みのチャンクを設定件数ずつ走査し、保存内容を実ブロックと照合します。
3. 設置、数量変更、登録変更、破壊、爆発、チャンク読込に応じてメモリ索引を更新します。
4. ChunkUnload後も最終確認済み情報を保持します。検索のためにチャンクをロードすることはありません。
5. 正常終了時に索引全体を同期保存します。

索引ファイルはバージョン付きバイナリ形式で、CRC32による破損検出を行います。直接編集はできません。破損または未対応バージョンを検出した場合、元ファイルを`.corrupt-<timestamp>`へ退避し、ロード済みチャンクから再構築します。

異常終了時は、前回の正常終了または手動再構築以降の数量が古い場合があります。該当チャンクが次回ロードされると、実ブロックから更新されます。

## 索引管理コマンド

権限は`storagesign.index.admin`、デフォルトはOPです。

```text
/storagesignindex status
/storagesignindex rebuild
/storagesignindex rebuild all
/storagesignindex rebuild <world>
```

短縮形は`/ssindex`です。

- `status`: 有効状態、登録件数、再構築進捗、読込結果、形式バージョン、最終保存件数・サイズを表示します。
- `rebuild`: 実行プレイヤーの現在Worldにあるロード済みチャンクを再走査します。
- `rebuild all`: 全Worldのロード済みチャンクを再走査します。
- `rebuild <world>`: 指定Worldのロード済みチャンクを再走査します。

コンソールから`rebuild`を使う場合は`all`またはWorld名が必要です。再構築の完了後、索引全体を非同期保存します。未ロードチャンクはロードも削除もされません。

## アイテム名検索コマンド

権限は`storagesign.search.admin`、デフォルトはOPです。短縮形は`/sssearch`です。

### 完全一致

```text
/sssearch item STONE
/sssearch item POTION:HEAL:0
/sssearch item NETHERITE_UPGRADE_SMITHING_TEMPLATE
```

大文字小文字を無視して完全識別子が一致するStorageSignを検索します。完全一致は識別子別の副索引を使うため、全件走査しません。

### 部分一致

```text
/sssearch item POTION --contains
/sssearch item SMITHING_TEMPLATE --contains
```

`--contains`を付けると大文字小文字を無視した部分一致になります。部分一致は保存済み索引全体を対象とするため非同期実行され、同時実行数は設定で制限されます。

検索結果は `World UUID -> X -> Y -> Z` の昇順で並べます。`--page` は 1 始まりで、範囲外ページはエラーです。`--world` は現在ロードされている World 名のみ受け付けます。

### Worldとページの指定

```text
/sssearch item STONE --world world
/sssearch item STONE --page 2
/sssearch item STONE --world world_nether --page 3
```

デフォルトでは全Worldを検索し、10件ずつ表示します。結果の先頭には一致件数、全StorageSignの数量合計、現在ページを表示します。

表示例:

```text
StorageSign search 'STONE': matches=12, totalAmount=98765, page=1/2
1. world 120 64 -35 — 3456 [loaded]
2. world_nether -18 70 240 — 64000 [cached]
```

- `loaded`: 現在チャンクがロードされています。
- `cached`: 未ロードチャンクにある最終確認済み情報です。検索によってチャンクはロードされません。

存在しないWorldの保存エントリはWorld UUIDで表示されます。`--world`には現在ロードされているWorld名を指定してください。

## 近接全文表示

プレイヤーの位置と視点が`idle-delay-ticks`の間変化しなかった場合だけ検索します。標準設定では、6ブロック以内、前方90度、遮蔽物のないStorageSignから最寄り3件を表示します。

上限制御では、表示件数が `max-per-player` を超えた分は次回以降に回し、`max-searches-per-tick` と `global-label-limit` に達しても既存の表示は維持します。負荷が落ち着くと未処理分を再試行します。

表示内容:

```text
NETHERITE_UPGRADE_SMITHING_TEMPLATE
× 12345
```

同じStorageSignを複数人が表示する場合、TextDisplayは共有され、対象プレイヤーにだけ送信されます。移動、視点変更、World移動、退出、StorageSign消失時に表示を解除します。

## 設定

```yaml
storage-index:
  enabled: true
  rebuild-chunks-per-tick: 8

nearby-display:
  enabled: true
  distance: 6.0
  field-of-view-degrees: 90.0
  idle-delay-ticks: 10
  monitor-interval-ticks: 5
  max-per-player: 3
  max-searches-per-tick: 25
  global-label-limit: 512

admin-search:
  page-size: 10
  max-concurrent: 2
```

## 負荷と運用上の注意

- 完全一致検索は副索引を使用します。部分一致は全件走査ですが非同期で実行します。
- 手動再構築は`rebuild-chunks-per-tick`単位で分散し、未ロードチャンクを読み込みません。
- 通常の数量変更ではメモリ索引だけを更新し、毎回ファイルへは保存しません。
- 保存件数が多いほど、起動時読込と終了時保存の時間・ファイルサイズが増えます。250msを超えた読込・保存は警告ログになります。
- TextDisplayはサーバー全体で`global-label-limit`を超えません。標準上限は512体です。
- 数百人環境での実際のTPS影響は、サーバー構成とStorageSign密度を含む実負荷試験が必要です。

索引ファイルをバックアップ・復元する場合はサーバーを停止してから操作してください。Worldデータと同じ時点の索引を使用するのが安全です。索引ファイルを削除してもStorageSign本体は失われず、ロード済みチャンクから索引が再構築されます。

## 外部CLI・ビューア

プラグイン本体とは別に、保存済み索引を読むPythonツールを使えます。検索、集計、エクスポートはCLIが担当し、Web画面は独立したviewerが担当します。

```text
python3 tools/storage_sign_index_cli.py inspect
```

既定では`plugins/StorageSign-Refactored/storage-sign-index.bin`を読みます。`--file`で任意のパスを指定できます。
`--world-map`には、World UUIDから表示名へ変換するJSONまたはCSVを渡せます。

### CLI

```text
python3 tools/storage_sign_index_cli.py inspect --file /path/to/storage-sign-index.bin
python3 tools/storage_sign_index_cli.py search STONE
python3 tools/storage_sign_index_cli.py search POTION --contains
python3 tools/storage_sign_index_cli.py search STONE --world 12345678-1234-5678-9abc-def012345678 --limit 20
python3 tools/storage_sign_index_cli.py search STONE --format json
python3 tools/storage_sign_index_cli.py export --format csv --output storage-sign-index.csv
python3 tools/storage_sign_index_cli.py export --identifier STONE --world-map worlds.json
```

`inspect`は索引全体の集計、`search`はアイテム検索、`export`はCSVまたはJSON出力を行います。`search`の既定出力はテキストで、該当なしも終了コード0です。ファイル欠落、CRC不一致、不正なWorldマップは終了コード1になります。

### Webページ

次のコマンドで、`http://127.0.0.1:8765/` に検索UIを起動します。

```text
python3 tools/storage_sign_index_viewer.py
python3 tools/storage_sign_index_viewer.py --file /path/to/storage-sign-index.bin --world-map worlds.json
```

Webページ側でも `identifier` / `contains` / `world UUID` を指定できます。表示対象ファイルは起動時の`--file`で固定され、Webリクエストから別のファイルへ変更できません。APIは既定50件、最大200件ずつページングします。変更されていない索引はメモリへキャッシュし、同時アクセスのたびに再読込しません。旧形式の`--serve`も互換性のため受け付けます。
Web UI には CSV ダウンロードボタンもあり、同じフィルタ条件で `/api/export.csv` を返します。

表示文字列は vanilla sign の見やすさを優先して内部で 28 文字ごとに折り返します。外部から見たときに枠外へはみ出さないことを意図した実装です。

注意:

- この保存データには World 名は入っていないため、外部ビューアでは基本的に World UUID を表示します。
- World 名を出したい場合は `--world-map` を使って UUID と表示名を紐付けてください。JSON は `{ "uuid": "world" }` 形式、CSV は `uuid,name` 形式を想定しています。
- 破損した `.bin` はプラグイン側と同様に CRC で弾きます。壊れたファイルは先に修復または再構築してください。
- CSVの識別子とWorld名が`=`, `+`, `-`, `@`で始まる場合、表計算ソフトの数式として実行されないよう先頭にアポストロフィを付けます。
