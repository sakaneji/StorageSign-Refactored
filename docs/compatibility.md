# 互換性と差分

この文書は、ベース版 `snowpegeon/StorageSign` からの主な差分と、互換性上の注意点をまとめます。

## 主な差分

- `config.yml` の拡張
  - `unregister-on-empty`
  - `item-identifier-aliases`
  - `virtual-item-identifiers`
  - `potion-key-aliases`
  - `brewing-ingredient-identifiers`
- 位置索引、管理者検索、`/sswarp`、近接表示の追加
- `/storagesigngive`（`/ssgive`）の追加
- 吊り看板系の対応
- 互換性・可搬性向上のためのレジストリ層の追加
- 外部 Logger 連携の追加
- テストスイートの拡充
- ホッパー付きトロッコ、チェスト付きトロッコ、チェスト付きボートへの自動入出庫対応

## 元版からの移行

- `plugins/StorageSign-Refactored/config.yml` がまだ存在せず、
  `plugins/StorageSign/config.yml` がある場合、初回起動時に旧設定を新フォルダへコピーします。
  旧ファイルは削除・変更しません。両方ある場合は新版側を優先します。
- 元版のレシピキー `storagesign:ssr<看板素材>` も登録するため、既に発見済みの
  レシピブック状態を維持します。吊り看板は元版非対応なので新版キーだけを使用します。
- 元版が保存した次の識別子を専用互換レイヤーで読み取ります。
  - bare `SIGN`: 通常Oak Signではなく、元版どおり空StorageSignアイテム
  - bare `STONE_SLAB`: Smooth Stone Slab
  - `STONE_SLAB:1`: Stone Slab
  - `SPLASH_POTION:...` / `LINGERING_POTION:...` のStorageSignアイテムLore
  - `NIGHT_VISION`、`POISON`等の完全PotionType名を使ったStorageSignアイテムLore
- 読み取った旧形式は、次回の看板更新・アイテム化時に新版の正規形式へ変換されます。

## データ保護上の互換動作

- `storagesign.break` がないプレイヤーは、StorageSign自身だけでなく、その支持ブロックも
  破壊できません。StorageSignと無関係な一般ブロックにはこの権限を適用しません。
- 単一Enchant用StorageSignは追加Enchant付きの本を受け入れません。
- `HorseEgg` は表示名だけでなくLore `Empty` まで一致する場合だけ受け入れます。
- 不吉な旗はDataFixerで失われる名前・tooltip flagだけを補完して比較し、
  独自名・Lore・Enchant等を持つ旗は受け入れません。
  NBT文字列や外部プラグインには依存せず、8模様をBukkitのレジストリキーで検証・生成します。
  起動時のAPI生成が一時的に失敗した場合は5秒間隔で再試行し、先に実物の不吉な旗を
  読み込んだ場合は、その模様だけを現行サーバーの空BannerMetaへ移して自己復旧します。
  入力側の独自メタは復旧用キャッシュへ持ち込みません。
- 自動搬出は搬送イベント上のItemMetaを複製せず、StorageSignの保存内容から復元した
  正規Itemを補充します。

## 廃止・変更

- `storagesign.create` 権限は廃止
- `WorldGuard` の softdepend は廃止
- `FarmNBT` 依存は廃止
- `project.properties` + `maven-resources-plugin` のコピー運用は廃止

## 注意点

- 外部 Logger は任意です。未導入でも標準ロガーへフォールバックします。
- 新しい Minecraft 版で保存・更新したワールドを古い版で開くダウングレードは非対応です。
- 詳細な機能説明は、該当する個別ドキュメントを参照してください。
