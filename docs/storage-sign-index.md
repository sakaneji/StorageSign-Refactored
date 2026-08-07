# StorageSign位置索引・検索・warp・近接表示

この文書では、StorageSignの位置索引、永続化、管理者検索、`/sswarp`、プレイヤー向け近接表示を説明します。

## 機能の関係

位置索引は、各StorageSignについて次の最終確認済み情報を保持します。

- World UUID
- ブロック座標（X、Y、Z）
- 完全な格納識別子
- 保管数量
- 前面方向（解決できた場合）
- 最終確認時刻

管理者検索、`/sswarp`、近接表示はこの索引を利用します。`storage-index.enabled: false`の場合、索引の読込・更新・保存を行わず、管理者検索、`/sswarp`、近接表示も無効になります。索引だけを有効にして近接表示を無効にすることはできます。

設定値のうち、`storage-index.rebuild-chunks-per-tick`、`storage-index.chunk-rescan-queue-cap`、`nearby-display.idle-delay-ticks`、`nearby-display.monitor-interval-ticks`、`nearby-display.max-per-player`、`nearby-display.max-searches-per-tick`、`nearby-display.global-label-limit`、`admin-search.page-size`、`admin-search.max-concurrent` は 0 以下なら既定値へ戻します。`nearby-display.field-of-view-degrees` は 1～360 度に丸めます。距離は 0 以下なら既定値へ戻します。

## 起動から終了までの動作

1. 起動時に`plugins/StorageSign-Refactored/storage-sign-index.bin`を読み込みます。
2. 現在ロード済みのチャンクを設定件数ずつ走査し、保存内容を実ブロックと照合します。
3. 設置、数量変更、登録変更、破壊、爆発、チャンク読込に応じてメモリ索引を更新します。
4. ChunkUnload後も最終確認済み情報を保持します。管理者検索のためにチャンクをロードすることはありません。
5. 正常終了時に索引全体を同期保存します。

索引ファイルはバージョン付きバイナリ形式で、CRC32による破損検出を行います。直接編集はできません。破損または未対応バージョンを検出した場合、元ファイルを`.corrupt-<timestamp>`へ退避し、ロード済みチャンクから再構築します。

異常終了時は、前回の正常終了または直近の索引保存以降の数量が古い場合があります。手動再構築の完了後も、
非同期保存がすでに進行中ならその保存完了へ委ねるため、再構築結果がただちに永続化されるとは限りません。
該当チャンクが次回ロードされると、実ブロックから更新されます。

## 索引管理コマンド

権限は`storagesign.index.admin`、デフォルトはOPです。

```text
/storagesignindex
/storagesignindex status
/storagesignindex rebuild
/storagesignindex rebuild all
/storagesignindex rebuild <world>
```

短縮形は`/ssindex`です。

- 引数なし: `status` と同じ内容を表示します。
- `status`: 有効状態、登録件数、再構築進捗、読込結果、形式バージョン、最終保存件数・サイズを表示します。
- `rebuild`: 実行プレイヤーの現在Worldにあるロード済みチャンクを再走査します。
- `rebuild all`: 全Worldのロード済みチャンクを再走査します。
- `rebuild <world>`: 指定Worldのロード済みチャンクを再走査します。

コンソールから`rebuild`を使う場合は`all`またはWorld名が必要です。再構築の完了後は
索引全体の非同期保存を試み、すでに保存中なら完了直後に最新スナップショットを1回追加保存します。未ロードチャンクは
ロードも削除もされません。

## オフライン region 再構築

サーバー未起動でも、ワールドディレクトリの `uid.dat` または `level.dat` と `region/*.mca` から索引を再構築できます。現行の `block_entities` と旧形式の `TileEntities` の両方を読みます。これはプラグイン本体の `/ssindex` とは別の standalone ツールです。

```text
python3 tools/storage_sign_region_cli.py /path/to/world
python3 tools/storage_sign_region_cli.py /path/to/world /path/to/another-world
python3 tools/storage_sign_region_cli.py /path/to/world --output /path/to/storage-sign-index.bin
```

`rebuild` も互換 alias として受け付けます。`--output` を省略した場合は `plugins/StorageSign-Refactored/storage-sign-index.bin` を優先し、`.bin` が無く `.bin.tmp` だけ存在する場合はその `.bin.tmp` を既定出力先として使います。入力ワールドごとに `uid.dat` を優先し、必要に応じて `level.dat` も参照して UUID を読み、`region` 配下の `.mca` を走査します。完全識別子は看板の PersistentDataContainer を優先し、PDCを持たない旧データだけ表示行へフォールバックします。存在しない world や壊れた region/chunk は警告して続行し、警告が出た場合は標準エラーへ警告内容と `warning: rebuild completed with warnings` を出し、終了コード 1 になります。出力は既存の `storage-sign-index.bin` と同じ形式です。

索引は同じディレクトリの `.tmp` へ全内容とCRCを書き終えてから置換します。
書込または置換に失敗した場合は既存の `.bin` を変更せず、今回生成した `.tmp` を
削除して非0終了します。プロセスの強制終了などで過去の `.tmp` だけが残った場合に
限り、上記の既定入力・出力先fallbackを復旧用として利用できます。

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

`--contains`を付けると大文字小文字を無視した部分一致になります。部分一致は現在の位置索引スナップショット全体を対象とするため非同期実行され、同時実行数は設定で制限されます。

### Skript向け座標出力

```text
/sssearch item STONE --coords
/sssearch item STONE --front
```

`--coords` は `world|x|y|z` 形式で返します。`--front` は前面方向を解決できたとき
`world|frontX|frontY|frontZ`、解決できないときは看板本体の `world|x|y|z` を返します。
1行1結果で、区切り文字は `|` です。`world` には現在サーバー上で解決できる場合はワールド名、解決できない場合は UUID 文字列を返します。
`--coords` と `--front` は同時に指定できません。

検索結果は `World UUID -> X -> Y -> Z` の昇順で並べます。`--page` は 1 始まりで、範囲外ページはエラーです。`--world` は現在ロードされている World 名のみ受け付けます。

### 一般プレイヤー向けワープ

```text
/sswarp STONE
/storagesignwarp POTION:HEAL:0
/sswarp --hand
/sswarp \--hand
```

`/sswarp` はプレイヤー専用で、実行プレイヤーの現在Worldだけを対象に、完全識別子が一致する最寄りのStorageSignを探し、
その前面ブロック中央へワープします。`--hand` を指定するとメインハンドのアイテムを入力にし、登録済みStorageSignアイテムを持っている場合はそのStorageSignに登録されているアイテムの識別子で検索します。identifier 文字列そのものとして `--hand` を指定したい場合は `\--hand` のように先頭を `\` でエスケープします。追加権限は不要です。候補は実ブロック確認を行いますが、1回のコマンドで新たにロードする候補チャンクは最大1個です。ロード済み候補の検証は続行し、古い索引位置は除去します。前面方向が不明な候補へはワープしません。ワープ先は前面ブロックと同じX/Zで下3ブロックまで足場を探し、足元と頭上が空気で直下が solid の高さへ移動します。安全な高さが見つからない場合はワープしません。`storage-index.enabled: false` のときは `/sswarp` も利用できません。

Skriptで関数化する場合は、対象プレイヤーに `/sswarp` を実行させます。

```vb
function warp_to_ss_item(p: player, identifier: text):
    make {_p} execute command "sswarp %{_identifier}%"

function warp_to_held_ss_item(p: player):
    make {_p} execute command "sswarp --hand"

function warp_to_literal_hand_identifier(p: player):
    make {_p} execute command "sswarp \\--hand"
```

### Worldとページの指定

```text
/sssearch item STONE --world world
/sssearch item STONE --page 2
/sssearch item STONE --world world_nether --page 3
```

デフォルトでは全Worldを検索し、`admin-search.page-size` 件ずつ表示します（既定値10件）。結果の先頭には一致件数、全StorageSignの数量合計、現在ページを表示します。数量合計が `Long.MAX_VALUE` に達した場合は `>9223372036854775807` のように、上限以上であることを示します。

表示例:

```text
StorageSign search 'STONE': matches=12, totalAmount=98765, page=1/2
1. world 120 64 -35 — 3456 [loaded]
2. world_nether -18 70 240 — 64000 [cached]
```

- `loaded`: 現在チャンクがロードされています。
- `cached`: 未ロードチャンクにある最終確認済み情報です。検索によってチャンクはロードされません。

存在しないWorldの保存エントリはWorld UUIDで表示されます。`--world`には現在ロードされているWorld名を指定してください。

## 近接表示

プレイヤーの位置が`idle-delay-ticks`の間変化しなかった場合に検索します。視点変更は再検索対象にしますが、位置が十分止まっていれば次の監視周期で再検索できます。標準設定では、3ブロック以内、前方90度、遮蔽物のないStorageSignから最寄り3件を候補にし、その看板本文だけでは完全識別子を表示しきれない場合にだけ近接 TextDisplay を出します。

`max-per-player` を超える候補は選択対象外です。`max-searches-per-tick` による検索待ちと、`global-label-limit` により確保できなかった選択済み候補は、余力ができた際に再試行します。上限に達しても既存の表示は維持します。

表示内容:

```text
NETHERITE_UPGRADE_SMITHING_TEMPLATE
```

短い識別子がすでに看板本文へ収まる場合は、近接 TextDisplay を追加表示しません。

同じStorageSignを複数人が表示する場合、TextDisplayは共有され、対象プレイヤーにだけ送信されます。移動や視点変更だけでは消さず、検索距離外への移動、World移動、退出、StorageSign消失時に表示を解除します。移動中の距離確認は各プレイヤーの表示中項目だけを対象にし、索引全体の再検索は停止後まで行いません。

## 設定

```yaml
storage-index:
  enabled: true
  rebuild-chunks-per-tick: 8
  chunk-rescan-queue-cap: 512

nearby-display:
  enabled: true
  distance: 3.0
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
- チャンク読込時の再スキャンは遅延キューに積み、同時に保持する再スキャン待ちチャンク数は`chunk-rescan-queue-cap`で制限します。上限超過分はその場では破棄されます。
- 通常の数量変更ではメモリ索引だけを更新し、毎回ファイルへは保存しません。
- 保存件数が多いほど、起動時読込と終了時保存の時間・ファイルサイズが増えます。250msを超えた読込・保存は警告ログになります。
- `/sswarp` が同期ロードする新規候補チャンクは1実行につき最大1個です。
- TextDisplayはサーバー全体で`global-label-limit`を超えません。標準上限は512体です。
- `max-per-player` が過大でも候補選択は `global-label-limit` 以下に抑え、表示チャンクがアンロードされた場合はTextDisplayだけを除去して永続索引は保持します。
- 数百人環境での実際のTPS影響は、サーバー構成とStorageSign密度を含む実負荷試験が必要です。

索引ファイルをバックアップ・復元する場合はサーバーを停止してから操作してください。Worldデータと同じ時点の索引を使用するのが安全です。索引ファイルを削除してもStorageSign本体は失われず、ロード済みチャンクから索引が再構築されます。

## 外部CLI・ビューア

プラグイン本体とは別に、保存済み索引を読むPythonツールを使えます。検索、集計、エクスポートはCLIが担当し、Web画面は独立したviewerが担当します。
これらのツールは `python3` 3.10 以上を前提にしています。

```text
python3 tools/storage_sign_index_cli.py inspect
```

既定では`plugins/StorageSign-Refactored/storage-sign-index.bin`を読みます。`.bin` が無く `.bin.tmp` だけ存在する場合は、その `.bin.tmp` を既定入力として使います。`--file`で任意のパスを指定できます。
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
外部 CLI / viewer は完全識別子をそのまま表示・出力します。28文字ごとの内部折り返しはゲーム内の近接 TextDisplay だけに適用され、外部 viewer では行いません。

注意:

- この保存データには World 名は入っていないため、外部ビューアでは基本的に World UUID を表示します。
- World 名を出したい場合は `--world-map` を使って UUID と表示名を紐付けてください。JSON は `{ "uuid": "world" }` のような object、または `[{ "uuid": "...", "name": "world" }]` のような object 配列を受け付けます。配列要素のキーは `uuid` / `world_id` / `id` と `name` / `world_name` の互換名にも対応します。CSV は `uuid,name` を基本に、`world_id,world_name` や `id,name` の列名も受け付けます。
- 破損した `.bin` はプラグイン側と同様に CRC で弾きます。壊れたファイルは先に修復または再構築してください。
- CSVの識別子とWorld名が`=`, `+`, `-`, `@`で始まる場合、表計算ソフトの数式として実行されないよう先頭にアポストロフィを付けます。
