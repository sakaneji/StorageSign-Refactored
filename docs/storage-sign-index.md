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
