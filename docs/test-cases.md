# StorageSign テストケース一覧

最終レビュー日: 2026-06-25

この文書は、実装済みテスト、手動確認項目、未カバー領域を同じ表で管理し、要件とテストの見落としを発見するための一覧である。

## ステータス

| 記号 | 意味 |
|---|---|
| ✅ | 自動テストがあり、直近の対象テストで成功 |
| 🟡 | 一部の分岐だけ自動テスト済み |
| ⏸️ | 自動テスト定義済みだが、対象環境では未完走 |
| 🧑 | 手動チェックリストのみ |
| ❌ | テスト未定義、または要件の確認が必要 |

## 現在の自動テスト結果

| 項目 | 結果 |
|---|---:|
| JUnit Unit | 613件成功、失敗0、エラー0、スキップ1 |
| JUnit Integration | 192件成功、失敗0、エラー0、スキップ29 |
| Pythonツール | 14件成功、失敗0、エラー0 |
| カバレッジ | 今回の追加後は未再計測 |
| Paper 1.21.4 / 1.21.8 / 1.21.11 | Loggerあり・なし、main・restart成功 |
| Paper 26.1.2 / 26.2 | テスト定義済み、実サーバー未完走 |

## 1. 起動・停止・設定

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| CFG-01 | デフォルト設定でプラグインを起動する | プラグインが有効化され、公開コマンドと設定デフォルトが登録される | Integration | ✅ |
| CFG-02 | 全スカラー設定を読み込む | 文字列、真偽値、上限値がキャッシュへ反映される | Unit | ✅ |
| CFG-03 | 識別子エイリアスと仮想識別子を読み込む | 空値を除外し、前後空白を除去した変更不能Mapになる | Unit | ✅ |
| CFG-04 | 互換Mapの設定セクションが存在しない | 空Mapとして安全に起動する | Unit | ✅ |
| CFG-05 | `manual-import` / `manual-export` を無効化する | 対応する右クリック処理だけが停止する | Unit | ✅ |
| CFG-06 | `auto-import` / `auto-export` を両方無効化する | InventoryMove処理を安全にスキップする | Unit | ✅ |
| CFG-07 | 自動搬入または自動搬出だけを無効化する | 無効化した方向だけ処理されない | Unit | ✅ |
| CFG-08 | `autocollect` を無効化する | 手持ちStorageSignへ自動収納されない | Unit | ✅ |
| CFG-09 | `unregister-on-empty` を切り替える | falseでは登録維持、trueではEmptyへ移行する | Unit | ✅ |
| CFG-10 | `no-bud` を有効化する | StorageSignの物理更新だけをキャンセルする | Unit | ✅ |
| CFG-11 | `falling-block-itemSS` を切り替える | 有効時だけ落下ブロックに付随するStorageSignをドロップする | Unit | ✅ |
| CFG-12 | `hardrecipe` を切り替える | 通常レシピと高難度レシピが正しく切り替わる | Integration | ✅ |
| CFG-13 | 不正なログレベルを指定する | INFOへフォールバックし警告する | Unit | ✅ |
| CFG-14 | `/reload`相当で再初期化する | 古いLogger登録、旗メタ、設定値を持ち越さない | Integration/E2E | ✅ |
| CFG-15 | プラグインを停止する | Logger登録と不吉な旗の保留タスクを解除する | Unit | ✅ |
| CFG-16 | 0以下や不正値の設定を読み込む | 一部は既定値へ戻し、FOVだけは1～360度へ丸める | Unit | ✅ |

## 2. コマンド・レシピ・権限

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| CMD-01 | Creativeプレイヤーが有効な`/ssgive`を実行する | 指定種類・識別子・数量のStorageSignを受け取る | Integration/E2E | ✅ |
| CMD-02 | 名前空間付き看板種類を指定する | `minecraft:spruce_sign`等を正規化する | Integration | ✅ |
| CMD-03 | Survivalプレイヤーが実行する | 拒否メッセージを表示し、アイテムを付与しない | Integration | ✅ |
| CMD-04 | 数量が数値でない | エラーメッセージを表示し、付与しない | Integration | ✅ |
| CMD-05 | 未知の識別子を指定する | エラーメッセージを表示し、付与しない | Integration | ✅ |
| CMD-06 | Consoleから実行する | プレイヤー専用メッセージを表示する | Integration | ✅ |
| CMD-07 | `storagesign.give`権限がない | 設定済み権限エラーを表示し、付与しない | Integration | ✅ |
| CMD-08 | 引数不足・過多 | 使用方法を表示し、付与しない | Integration | ✅ |
| CMD-09 | 負数を指定する | 数量エラーとして拒否する | Integration | ✅ |
| CMD-10 | 不正な看板種類を指定する | 看板種類エラーとして拒否する | Integration | ✅ |
| CMD-11 | インベントリに空きがない | 付与できないStorageSignを足元へドロップする | Paper E2E | ✅ |
| RCP-01 | 通常レシピを登録する | 全看板種類のStorageSignレシピが利用できる | Integration | ✅ |
| RCP-02 | クラフト権限がない | StorageSignのクラフトだけをキャンセルする | Unit | ✅ |
| RCP-03 | クラフト権限がある | クラフトをキャンセルしない | Unit | ✅ |
| RCP-04 | 通常アイテムをクラフトする | StorageSign権限判定が干渉しない | Unit | ✅ |

## 3. StorageSignデータ形式・数量

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| DAT-01 | null、0～2行、不正ヘッダーを解析する | StorageSignとして認識しない | Unit | ✅ |
| DAT-02 | 大文字小文字が異なるヘッダーを解析する | 厳密一致しない場合は認識しない | Unit | ✅ |
| DAT-03 | 識別子が空または`EmptySign` | 未登録StorageSignとして解析する | Unit | ✅ |
| DAT-04 | 通常Materialと数量を解析する | Material、damage、数量を保持する | Unit | ✅ |
| DAT-05 | 数量が空、非数値、負数、整数範囲外 | 壊れたStorageSignとして拒否する | Unit | ✅ |
| DAT-06 | 未知Material | nullを返し、誤ったStorageSignを生成しない | Unit | ✅ |
| DAT-07 | 3行だけのStorageSign | 有効なStorageSignとして解析する | Unit | ✅ |
| DAT-08 | 正数へ数量変更 | 登録を維持して数量を更新する | Unit | ✅ |
| DAT-09 | 0または負数へ数量変更 | 0に丸め、設定に従って登録状態を決める | Unit | ✅ |
| DAT-10 | 1LC、複数LC、スタック、端数の表示 | 4行目のサマリーが正しい | Unit | ✅ |
| DAT-11 | 看板行→識別子→看板行を往復する | Materialと特殊種別を失わない | Unit | ✅ |
| DAT-12 | StorageSignアイテムのLoreを生成する | 識別子と数量を1行で保持する | Unit | ✅ |
| DAT-13 | 壊れたLore、複数Lore、極端に長いLoreを読む | 非数値は数量0、追加Loreは先頭行、識別不能形式は拒否する | Integration | ✅ |
| DAT-14 | `Integer.MAX_VALUE`付近で搬入・搬出する | 空き容量まで搬入し、余剰を元の場所へ残す | Unit/Paper E2E | ✅ |
| DAT-15 | 遅延搬出前にチャンクがアンロードされる | 強制ロードせず安全に中止し、数量を変更しない | Integration | ✅ |
| DAT-16 | 全Material・長い設定識別子・最大数量を看板へ表示する | 表示は90px以内、完全識別子はPDCに保持して復元できる | Unit/Integration | ✅ |

## 4. 識別子・Material・バージョン非依存性

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| MAT-01 | 通常・壁・吊り看板を列挙する | 各集合が重複せず、壁看板からアイテム看板へ変換できる | Unit | ✅ |
| MAT-02 | 全16色の染料を判定する | DyeColorとの対応が一意である | Unit | ✅ |
| MAT-03 | 全シュルカーボックスを列挙する | 無色・色付きが含まれ、非対象を含まない | Unit | ✅ |
| MAT-04 | Potion、Splash、Lingeringを列挙する | 3種類だけをPotion対象とする | Unit | ✅ |
| MAT-05 | 旧看板名を往復変換する | `OakStorageSign`等を現行Materialへ解決する | Unit | ✅ |
| MAT-06 | 旧Material名を解決する | SIGN、ROSE_RED、STONE_SLAB等を互換変換する | Unit | ✅ |
| MAT-07 | 設定追加した任意エイリアスを解析する | ソフト変更なしで現行Materialへ解決する | Unit | ✅ |
| MAT-08 | 設定追加した仮想識別子を解析する | ソフト変更なしでバッキングMaterialへ解決する | Unit | ✅ |
| MAT-09 | 製品コードにPaperやMinecraft版別分岐がない | バージョン固有import・文字列分岐を持たない | Architecture | ✅ |
| MAT-10 | 将来追加Materialを保管する | 通常Materialは動的レジストリとBukkit解決だけで扱う | Architecture | ✅ |
| MAT-11 | Material名が削除・変更されたワールドを開く | 設定エイリアスで現行Materialへ移行できる | Unit | ✅ |
| MAT-12 | 実行時Registryの全PotionTypeを列挙する | NamespacedKey形式でMaterialとPotionTypeを全件往復する | Unit | ✅ |
| MAT-13 | Potionキーが改名・削除される | `potion-key-aliases`で現行Registryキーへ移行する | Unit | ✅ |

## 5. 手動搬入・搬出

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| MAN-01 | 空手で登録済みStorageSignを右クリックする | 通常上限まで搬出し、看板数量を減らす | Paper E2E | ✅ |
| MAN-02 | 対象アイテムを持って右クリックする | 対象アイテムを全量搬入し、看板数量を増やす | Paper E2E | ✅ |
| MAN-03 | スニークして対象アイテムを搬入する | スニーク上限だけ搬入する | Paper E2E | ✅ |
| MAN-04 | 残量1をスニーク搬出する | 数量0になっても設定どおり登録を維持する | Paper E2E | ✅ |
| MAN-05 | `storagesign.use`権限がない | 数量とプレイヤー所持品を変更しない | Paper E2E | ✅ |
| MAN-06 | 異なるアイテムを持って操作する | 手持ちを消費せず、登録内容を1スタック搬出する | Paper E2E | ✅ |
| MAN-07 | 未登録StorageSignへアイテムを登録する | 手持ちを消費せず対象種別を数量0で登録する | Paper E2E | ✅ |
| MAN-08 | StorageSignアイテムを搬出・再取込する | 個数とLoreを失わず往復する | Paper E2E | ✅ |
| MAN-09 | インベントリが満杯の状態で搬出する | 足元へドロップした数量だけ減算する | Paper E2E | ✅ |
| MAN-10 | 同一tickで同じStorageSignを連続操作する | 処理順に関係なく重複搬出や数量消失が発生しない | Paper E2E | ✅ |
| MAN-11 | Spectatorが操作する | 権限判定や数量変更を行わない | Unit | ✅ |
| MAN-12 | 左クリックする | StorageSign処理を行わない | Unit | ✅ |
| MAN-13 | スニーク中にオフハンドのStorageSignで操作する | 誤設置だけを拒否し、保管内容を変更しない | Unit | ✅ |
| MAN-14 | 通常アイテムでオフハンド操作する | Vanilla操作を妨げずStorageSign処理を行わない | Unit | ✅ |
| MAN-15 | クライアントが空中右クリックとして送信する | 3ブロック以内の対象StorageSignへ同じ権限・操作判定を適用する | Unit | ✅ |
| MAN-16 | 保管内容と異なる染料で操作する | 搬出せず、看板色変更をVanillaへ委譲する | Unit | ✅ |
| MAN-17 | スニークして同種の染料・発光インクを搬入する | 数量を加算し、前面の色・発光状態も更新する | Integration | ✅ |

### 5.1 StorageSignアイテムのマージ

登録済みStorageSignアイテムの一枚あたり保管数を単位としてマージする。最後の一枚は必要に応じて
一部だけ移動し、空StorageSignを先に返したうえで、残量が減ったStorageSignを後から返す。

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| MRG-01 | 全StorageSignが数量上限内に収まる | 全内容を加算し、同じ枚数の空SSを手持ちへ返す | Unit | ✅ |
| MRG-02 | 複数枚の一部だけが数量上限内に収まる | 入る分だけマージし、空SSと残量減少SSを順に返す | Unit/Paper E2E | ✅ |
| MRG-03 | 一枚分未満の空きしかない | 一部だけマージし、残量が減ったSSを手元に残す | Unit | ✅ |
| MRG-04 | 部分マージ時にインベントリが満杯 | 空SSを先に、残量減少SSを後に足元へドロップする | Unit/Paper E2E | ✅ |
| MRG-05 | `manual-import=false` | マージ元・先を変更しない | Unit | ✅ |
| MRG-06 | 保管内容が異なるSSをマージする | マージ元・先を変更しない | Unit | ✅ |
| MRG-07 | マージ前後の総量を比較する | 設置SSと登録済みSS内の合計数量およびSS枚数が一致する | Unit/Paper E2E | ✅ |

### 5.2 StorageSignアイテムの分割

分割前のブロック数量を `B`、手持ちの空SS数を `N`、設定上限を `L` とする。
一枚あたりの割当量は `min(floor(B / (N + 1)), L)` で、`L <= 0` は上限なしとする。

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| DIV-01 | 空SS 1枚で100個を分割する | 手持ち50、ブロック50になる | Unit | ✅ |
| DIV-02 | 空SS 2枚で100個を分割する | 各手持ち33、ブロック34になり端数を失わない | Unit/Paper E2E | ✅ |
| DIV-03 | 通常上限を超える数量を分割する | 各SSが`divide-limit`以下になる | Unit | ✅ |
| DIV-04 | スニーク上限を超える数量を分割する | 各SSが`sneak-divide-limit`、端数はブロックへ残る | Unit/Paper E2E | ✅ |
| DIV-05 | 上限と均等割当量が一致する | 境界値を上限超過として誤判定しない | Unit | ✅ |
| DIV-06 | 上限を0または負数にする | 上限なしで均等分割する | Unit | ✅ |
| DIV-07 | 保管数が空SS数以下 | 分割せず双方を変更しない | Unit | ✅ |
| DIV-08 | `manual-export=false` | 分割しない | Unit | ✅ |
| DIV-09 | 異なる看板素材または登録済みSSを持つ | 分割しない | Unit | ✅ |
| DIV-10 | sign-in-sign対象へ同種の空SSを持つ | 分割ではなく手動インポートする | Paper E2E | ✅ |
| DIV-11 | `Integer.MAX_VALUE`付近と最大スタック数で計算する | 乗算オーバーフローせず数量を保存する | Unit | ✅ |
| DIV-12 | 分割前後の総量を比較する | `ブロック + 各SS`の総量が一致する | Unit/Paper E2E | ✅ |

## 6. 設置・破壊・編集・隣接判定

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| BLK-01 | Lore付きStorageSignアイテムを設置する | 看板4行へ識別子と数量を復元する | Paper E2E | ✅ |
| BLK-02 | `storagesign.place`権限がない | 設置をキャンセルする | Unit | ✅ |
| BLK-03 | StorageSignを権限ありで破壊する | Vanillaドロップを止め、Lore付きアイテムを1個ドロップする | Paper E2E | ✅ |
| BLK-04 | `storagesign.break`権限がない | 破壊をキャンセルし、看板と数量を維持する | Unit/Paper E2E | ✅ |
| BLK-05 | 一般ブロックを破壊する | StorageSignの破壊権限が干渉しない | Unit | ✅ |
| BLK-06 | 登録済みStorageSignを編集する | 保存済み4行を維持し、改変を反映しない | Paper E2E | ✅ |
| BLK-07 | Survivalで通常看板からStorageSignを手入力する | 権限エラーで拒否する | Unit | ✅ |
| BLK-08 | Creativeで通常看板からStorageSignを手入力する | 正規ヘッダーへ変換して許可する | Unit | ✅ |
| BLK-09 | Standing Signをコンテナ上に配置する | 隣接StorageSignとして認識する | Unit | ✅ |
| BLK-10 | Wall Signを側面に配置する | 向きが接続先を向く場合だけ認識する | Unit | ✅ |
| BLK-11 | Hanging SignとWall Hanging Signを配置する | 天井・左右接続規則で認識する | Unit | ✅ |
| BLK-12 | 複数規則が同じ看板を返す | 同じ看板を重複処理しない | Unit | ✅ |
| BLK-13 | 支持ブロックを破壊する | 取り付けStorageSignだけを正しくアイテム化する | Paper E2E | ✅ |
| BLK-14 | BUD物理更新が発生する | `no-bud`有効時にStorageSignだけを保護する | Unit | ✅ |
| BLK-15 | 数量0の壁看板StorageSignを破壊する | 対応する素材の空StorageSignアイテムを落とす | Unit | ✅ |
| BLK-16 | Wall Hanging StorageSignを破壊する | 対応するHanging Signアイテムへ変換して落とす | Unit | ✅ |

## 7. 自動搬送・コンテナ

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| INV-01 | Hopperから隣接コンテナへ連続搬送する | 複数満杯スタックをStorageSignへ自動搬入する | Paper E2E | ✅ |
| INV-02 | 隣接コンテナからHopperへ連続搬送する | StorageSignから複数満杯スタックを補充する | Paper E2E | ✅ |
| INV-03 | Hopper Minecartから搬入する | 固定Hopperと同様に認識し、数量を同期する | Paper E2E | ✅ |
| INV-04 | Hopper Minecartへ搬出する | StorageSignからMinecart側へ補充する | Paper E2E | ✅ |
| INV-05 | 通常Inventoryの空きが一部だけ | 実際に追加できた数量だけStorageSignから減算する | Integration | ✅ |
| INV-06 | Brewing Standへ燃料を補充する | Blaze Powderを燃料スロットへ入れる | Integration | ✅ |
| INV-07 | Brewing StandへPotionを補充する | 3つのPotionスロットへ入れる | Integration | ✅ |
| INV-08 | Brewing Standへ材料を補充する | 有効材料だけ材料スロットへ入れ、非対応品を拒否する | Integration | ✅ |
| INV-09 | Furnace系へ燃料・入力を補充する | Material種別に応じて専用スロットへ入れる | Integration | ✅ |
| INV-10 | Double Chestで搬送する | 両側を同一Inventoryとして扱い、重複や漏れがない | Paper E2E | ✅ |
| INV-11 | Dropperで搬入・搬出する | InventoryMoveとワールド排出後補充で数量同期する | Paper E2E | ✅ |
| INV-12 | Dispenserで搬入・搬出する | InventoryMoveとワールド排出後補充で数量同期する | Paper E2E | ✅ |
| INV-13 | Crafterを連続駆動する | InventoryMoveとワールド排出後補充で数量同期する | Paper E2E | ✅ |
| INV-14 | Dropper等がワールドへ吐き出す | 排出後に実在庫を補充し、その分だけStorageSignを減算する | Paper E2E | ✅ |
| INV-15 | Chest Boatを搬送元・搬送先にする | Entity Inventoryとして正しく認識する | Paper E2E | ✅ |
| INV-16 | 対象Inventoryが満杯 | アイテムとStorageSign数量を変更しない | Integration | ✅ |
| INV-17 | 既に満杯スタックが存在する | 不要な補充を行わない | Integration | ✅ |
| INV-18 | 搬送イベントがキャンセル済み | StorageSign処理を行わない | Unit | ✅ |
| INV-19 | 同一tickに複数搬送イベントが発生する | 補充タスクを重複登録せず、数量を二重減算しない | Unit | ✅ |
| INV-20 | 自動搬入先SSが上限直前 | 空き容量だけ吸収し、残りをコンテナ側へ残す | Integration | ✅ |
| INV-21 | 自動搬入先SSが上限到達済み | コンテナとSSを変更しない | Integration | ✅ |
| INV-22 | Chest Minecartから搬入する | Entity Inventoryとして認識し、数量を同期する | Paper E2E | ✅ |
| INV-23 | InventoryPickupがキャンセル済み・無効設定・満杯スタック未満 | 隣接探索やInventory変更を行わない | Unit | ✅ |
| INV-24 | 遅延搬出時にチャンクまたは看板が無効になる | 数量を変更せず、次回搬出用の予約を解除する | Integration | ✅ |

## 8. Entity・自動収集・物理イベント

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| ENT-01 | 登録済みStorageSignをメインハンドに持って対象品を拾う | ドロップを消し、Lore数量を増やす | Paper E2E | ✅ |
| ENT-02 | メインハンドで収納できずオフハンドに対象StorageSignがある | オフハンド側へ収納する | Unit | ✅ |
| ENT-03 | `storagesign.autocollect`権限がない | 自動収納せず通常取得する | Unit | ✅ |
| ENT-04 | StorageSignアイテムを複数スタックして持つ | 自動収納対象にしない | Unit | ✅ |
| ENT-05 | プレイヤーInventoryに必要な満杯スタックがない | 自動収納せず通常取得する | Unit | ✅ |
| ENT-06 | プレイヤー以外がStorageSignアイテムを拾う | 取得をキャンセルし、pickup delayを設定する | Unit | ✅ |
| ENT-07 | FallingBlockが支持ブロックへ変化する | 設定有効時に取り付けStorageSignをドロップする | Unit | ✅ |
| ENT-08 | FallingBlock以外のEntityChangeBlockEvent | StorageSign処理を行わない | Unit | ✅ |
| ENT-09 | 自動収集先SSが上限直前 | 空き容量だけ収納し、余剰をItem Entityへ残す | Unit | ✅ |
| ENT-10 | 自動収集先SSが上限到達済み | 通常の取得処理へ委譲し、SSとEntityを変更しない | Unit | ✅ |

## 9. 特殊アイテム

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| SPC-01 | 通常・Splash・Lingering Potionを解析する | 種類、延長、強化を識別子へ往復変換する | Unit/Paper E2E | ✅ |
| SPC-02 | 特殊Potion名と旧NBT名を解析する | 現行PotionTypeへ正規化する | Unit | ✅ |
| SPC-03 | Ominous Bottleの増幅値0～最大を解析する | 増幅値を失わず識別子へ往復する | Unit | ✅ |
| SPC-04 | Ominous Bottleを実物アイテムで搬入・搬出する | ItemMetaの増幅値を維持する | Paper E2E | ✅ |
| SPC-05 | Enchanted Bookを搬入・搬出する | 単一Enchantとレベルを維持する | Integration | ✅ |
| SPC-06 | 複数Enchantを持つ本を登録する | 保管不可として拒否する | Integration | ✅ |
| SPC-07 | Firework Rocketを搬入・搬出する | 飛翔時間を維持し、効果付きは拒否する | Integration | ✅ |
| SPC-08 | 耐久値付きアイテムを搬入・搬出する | damage値を維持し、異なるdamageを混同しない | Integration | ✅ |
| SPC-09 | シュルカーボックスを搬入・搬出する | 空箱だけを許可し、中身入りは拒否する | Integration | ✅ |
| SPC-10 | 蜂の巣・養蜂箱を登録する | 空だけを許可し、蜂Entity入りは拒否する | Integration | ✅ |
| SPC-11 | 復元不能な個別ItemMetaを登録する | カスタム効果、名前、Lore、Enchant、ItemFlagを拒否する | Integration | ✅ |
| SPC-12 | Potionの短縮表示とPDC正規キーを保存する | 表示は旧形式のまま、復元はNamespacedKeyを優先する | Integration/Paper E2E | ✅ |
| SPC-13 | 全Potion表示文字列のVanilla幅を計算する | 90px以内かつ16文字以内で枠外へはみ出さない | Unit/Paper E2E | ✅ |
| SPC-14 | PDCと表示行が異なるPotionを示す | PDCを正としてMaterial・PotionTypeを復元する | Integration | ✅ |
| SPC-15 | 1.21.4→1.21.8→1.21.11でPotion看板を更新する | PDC、短縮表示、数量、PotionTypeを維持する | Upgrade E2E | ✅ |
| SPC-16 | 実行時Registryの全Enchantを短縮キーで往復する | キー衝突がなく、種類とレベルを完全復元する | Integration | ✅ |
| SPC-17 | 空・未知・不正レベルのEnchant識別子を読む | 復元不能データとして安全に拒否する | Unit/Integration | ✅ |
| SPC-18 | 汎用識別子PDCが欠落・破損している | 欠落時は旧表示を読み、破損値は誤復元せず拒否する | Integration | ✅ |

## 10. 不吉な旗

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| BNR-01 | Vanilla不吉な旗の8模様を判定する | 色と模様種別が完全一致した場合だけ受理する | Unit | ✅ |
| BNR-02 | 任意の8模様白旗を判定する | 不吉な旗として誤認しない | Unit | ✅ |
| BNR-03 | 模様数が8未満・超過 | 不吉な旗として認識しない | Unit | ✅ |
| BNR-04 | 名前・ツールチップAPIが利用可能 | 名前と非表示フラグを付与する | Unit/E2E | ✅ |
| BNR-05 | 名前・ツールチップAPIが欠落 | 8模様本体を維持し、装飾機能だけ劣化する | Unit | ✅ |
| BNR-06 | 起動時に旗メタ生成が一時失敗 | 1 tick後に再試行し、以後は5秒間隔で復旧を継続する | Unit | ✅ |
| BNR-07 | 再試行も失敗 | 無限ループではなく、5秒間隔の再試行を維持する | Unit | ✅ |
| BNR-08 | 再試行前に実物旗から復旧 | 取得済みメタを維持し、不要な生成をしない | Unit | ✅ |
| BNR-09 | サーバー停止時に再試行保留 | タスクをキャンセルする | Unit | ✅ |
| BNR-10 | 不吉な旗を搬出・再取込する | 8模様、名前、数量、ツールチップを維持する | Paper E2E | ✅ |
| BNR-11 | 1.21.4→1.21.8→1.21.11でワールド更新する | 旧旗を取込・再搬出し、現行装飾を復元する | Upgrade E2E | ✅ |
| BNR-12 | 1.21.11→26.1.2→26.2でワールド更新する | 同一処理で旗互換性を維持し、再試行挙動も崩れない | Upgrade E2E | ⏸️ |

## 11. Logger・診断ログ

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| LOG-01 | 外部Loggerなしで起動する | JULバックエンドを選択して全機能を継続する | Unit/Paper E2E | ✅ |
| LOG-02 | 外部Loggerありで起動する | LoggerプラグインへStorageSignを登録する | Unit/Paper E2E | ✅ |
| LOG-03 | 外部Logger初期化が失敗する | 警告後にJULへフォールバックする | Unit | ✅ |
| LOG-04 | プラグイン停止・再起動 | 外部Logger登録を解除し、再起動後に再登録する | Unit/Paper E2E | ✅ |
| LOG-05 | Loggerあり・なしで全ゲームシナリオを実行する | Logger構成によってゲーム動作が変化しない | Paper E2E | ✅ |
| LOG-06 | TRACE未満で遅延メッセージを渡す | SupplierやItemMetaを不要に評価しない | Unit | ✅ |
| LOG-07 | TRACEかつ`banner-debug`有効 | ItemMetaと呼出元を診断ログへ出す | Unit | ✅ |
| LOG-08 | 例外をログ出力する | メッセージにスタックトレースを付加する | Unit | ✅ |
| LOG-09 | 実際の外部Logger保存先を確認する | 登録後のINFO本文がPaperログsinkへ到達する | Paper E2E | ✅ |
| LOG-10 | ログローテーション・書込失敗 | ゲーム処理を停止せず、診断可能な状態を維持する | Manual | 🧑 |

## 12. 再起動・バージョン互換性

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| VER-01 | Paper 1.21.4、Loggerなし・あり | mainとrestartの全シナリオが成功する | Paper E2E | ✅ |
| VER-02 | Paper 1.21.8、Loggerなし・あり | mainとrestartの全シナリオが成功する | Paper E2E | ✅ |
| VER-03 | Paper 1.21.11、Loggerなし・あり | mainとrestartの全シナリオが成功する | Paper E2E | ✅ |
| VER-04 | Paper 26.1.2、Loggerなし・あり | mainとrestartの全シナリオが成功する | Paper E2E | ⏸️ |
| VER-05 | Paper 26.2、Loggerなし・あり | mainとrestartの全シナリオが成功する | Paper E2E | ⏸️ |
| VER-06 | 26.xをJava 25で起動する | Java要件を満たし、プラグインが有効化される | Paper E2E | ⏸️ |
| VER-07 | 1.21.4→1.21.8→1.21.11でワールド更新する | StorageSign、Potion PDC、旗データを失わない | Upgrade E2E | ✅ |
| VER-08 | 新版ワールドを旧版で開く | 非対応と事前バックアップ必須が文書化されている | Documentation | ✅ |
| VER-09 | Spigotで実行する | 製品保証・リリース試験の対象外である | Documentation | ✅ |

## 13. テストランナー・成果物

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| TST-01 | Unit / Integration / Coverageを個別実行する | 対象スコープだけをDockerで実行し、件数を要約する | Runner | ✅ |
| TST-02 | E2EのLogger構成を切り替える | Logger JARの有無を物理的に切り替え、独立環境で検証する | Runner | ✅ |
| TST-03 | 成功したE2E時間を保存する | 構成別平均をキャッシュし、次回推定に使用する | Runner self-test | ✅ |
| TST-04 | 履歴なしでE2Eを開始する | 未知部分全体を180秒、初回待機を210秒と提示する | Runner self-test | ✅ |
| TST-05 | E2E構成が完了する | 残件だけで推定時間を再計算する | Runner self-test | ✅ |
| TST-06 | Minecraft初回起動へ進む | 1分の固定待機ヒントを提示する | Runner | ✅ |
| TST-07 | 時間キャッシュが壊れている | 壊れたファイルを退避し、初回推定へ戻る | Runner self-test | ✅ |
| TST-08 | E2Eが失敗・中断する | 失敗時間を平均へ混入させず、対象ログを案内する | Runner self-test | ✅ |
| TST-09 | 成功ログを扱う | PASS要約だけを表示し、詳細成果物を読まない | Runner | ✅ |
| TST-10 | 失敗ログを扱う | 既定40行と必要な成果物だけを表示する | Runner | ✅ |

## 14. 位置索引・近接表示

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| IDX-01 | SSを登録・破棄・再検索する | チャンク単位の位置索引が自動更新される | Integration | ✅ |
| IDX-02 | 索引を無効化する | 走査・登録・検索を行わず、近接表示も無効になる | Unit/Integration | ✅ |
| IDX-03 | 索引だけを有効化する | 検索と手動再構築は利用でき、TextDisplayは生成しない | Integration | ✅ |
| IDX-04 | 古い索引位置を検索する | 実ブロックを検証して古い位置を除去する | Integration | ✅ |
| IDX-05 | 停止後に前方90度を検索する | 移動中は検索せず、距離・角度・遮蔽物で候補を絞る | Unit/Paper | 🟡 |
| IDX-06 | 長い完全識別子を表示する | 省略せず改行し、数量とともに表示する | Unit | ✅ |
| IDX-07 | 多人数が別々のSSを表示する | 25検索/tick、512 TextDisplayの上限を超えない | Load | ⏳ |
| IDX-08 | 管理コマンドで再構築する | 未ロードチャンクをロードせず、進捗と結果を通知する | Integration | ⏳ |
| IDX-09 | 500人で停止・移動を繰り返す | TPSを維持し、実測負荷と表示待ち時間が許容範囲内 | Load | ⏳ |
| IDX-10 | 複数World・負座標・最大数量を保存して再読込する | バージョン付きバイナリから完全復元する | Unit | ✅ |
| IDX-11 | 索引ファイルが切断・改変されている | CRC不一致を検出し、破損データを採用しない | Unit | ✅ |
| IDX-12 | 数量だけを更新する | アイテム検索結果は更新し、位置検索の構造世代は変えない | Integration | ✅ |
| IDX-13 | アイテム名を完全一致・部分一致で検索する | 大文字小文字、World、数量条件を適用して位置と数量を返す | Unit | ✅ |
| IDX-14 | 多数の一致結果を検索する | 10件単位でページングし、数量合計をlongで保持する | Unit/Integration | ✅ |
| IDX-15 | サーバー停止・再起動する | 索引を保存・読込後、ロード済みチャンクで再検証する | Integration/Paper E2E | ✅ |
| IDX-16 | `/sssearch`へ不正な権限・World・ページ・オプションを渡す | 検索を開始せず、原因を示すメッセージを返す | Integration | ✅ |
| IDX-17 | 保存ファイルのmagic、version、件数、UTF-8、末尾データが不正 | 不正ファイルを拒否し、部分データを採用しない | Unit | ✅ |
| IDX-18 | JavaとPythonで同じ索引プロトコルを読む | UUID、負座標、数量、識別子、時刻を同じ値へ復元する | Unit | ✅ |
| IDX-19 | 検索結果の並び順を確認する | `World UUID -> X -> Y -> Z` の昇順で安定表示する | Unit/Integration | ✅ |
| IDX-20 | `--page` が範囲外の検索を行う | 結果を出さず、明示的にページ範囲エラーを返す | Integration | ✅ |
| IDX-21 | `--world` に未定義のWorld指定や許可外の入力を渡す | 検索を開始せず、入力エラーを返す | Integration | ✅ |
| IDX-22 | 近接表示が上限に達する | 既存表示を維持し、残件を次回以降に再試行する | Integration/Paper | 🟡 |
| IDX-23 | 停止中のプレイヤー検索が上限を超える | `max-searches-per-tick` を超えず、未処理分を繰り越す | Integration/Paper | 🟡 |
| IDX-24 | 長い識別子が表示枠を超えそうになる | 28文字折り返しで枠外表示を避ける | Unit | ✅ |

## 15. 外部CLI・Webビューア

| ID | テストケース | 期待結果 | レベル | 状態 |
|---|---|---|---|---|
| EXT-01 | CLIでinspect/search/exportを実行する | Text/JSON/CSVを生成し、検索上限でも全一致件数を保持する | Python Unit | ✅ |
| EXT-02 | CRC、magic、version、件数、UTF-8、末尾データが不正 | 処理を中止し、巨大件数による無制限ループを開始しない | Python Unit | ✅ |
| EXT-03 | CSVへ数式開始文字を含む識別子・World名を出力する | 表計算ソフトで数式として評価されない形式へ無害化する | Python Unit | ✅ |
| EXT-04 | Viewerで検索・ページング・CSV取得を行う | 全一致集計を維持し、1応答の件数を上限内に制限する | Python Unit | ✅ |
| EXT-05 | Viewerへ別ファイルパス・不正mode・不正pageを渡す | HTTP 400を返し、起動時指定以外のファイルを読まない | Python Unit | ✅ |
| EXT-06 | 存在しないViewer URLへアクセスする | HTTP 404を返す | Python Unit | ✅ |
| EXT-07 | 同じ索引へ連続アクセスする | ファイルが変わるまで解析結果を共有し、変更後は再読込する | Python Unit | ✅ |

## 確定した要件と残作業

| 観点 | 確定内容 |
|---|---|
| 26.x | 検証環境が利用可能になるまで保留する |
| 数量整合性 | 処理順は規定せず、成功した移動の総数量保存と重複タスク防止を保証する |
| 数量上限 | `Integer.MAX_VALUE`まで部分搬入し、余剰を元の場所へ残す |
| 壊れたデータ | 例外を出さず、安全値へ変換できない形式は拒否する |
| 看板表示 | 完全識別子はPDCへ保存し、全識別子をVanilla標準幅90px以内の短縮表示にする |
| Potion追加 | 実行時Registryから自動認識し、キー変更時だけ設定エイリアスを使用する |
| 醸造材料追加 | 公開APIに列挙機能がないため、既定材料に設定リストを追加して対応する |
| 特殊ItemMeta | 識別子から完全復元できる形式だけを受け入れる |
| 自動搬送 | Hopper、Hopper Minecart、Chest Minecart、Chest Boat、Double Chest、Dropper、Dispenser、Crafterを対象とする |
| Logger | 通常sink到達は自動E2E、ローテーション・書込障害は手動障害注入で確認する |
| Spigot | 製品保証とリリース試験の対象外とする |
| チャンク境界 | アンロード中の遅延処理は強制ロードせず中止する |
| ダウングレード | 非対応とし、更新前バックアップを必須とする |
| 検索順序 | `World UUID -> X -> Y -> Z` の昇順で固定する |
| 近接表示上限 | 既存表示を優先し、残件は後続tickで再試行する |
| World指定 | `--world` は現在ロード中のWorld名のみ受け付ける |
| 折り返し | 表示文面は内部28文字折り返しで枠外表示を避ける |

自動化の残作業は、26.x環境の準備後に行うVER-04～06とBNR-12に加え、IDX-07/09の多人数負荷試験、IDX-08の管理コマンド全分岐、IDX-05の遮蔽物境界検証、近接表示の上限制御検証である。
LOG-10は意図的に手動障害注入として維持する。
