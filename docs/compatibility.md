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

## 廃止・変更

- `storagesign.create` 権限は廃止
- `WorldGuard` の softdepend は廃止
- `FarmNBT` 依存は廃止
- `project.properties` + `maven-resources-plugin` のコピー運用は廃止

## 注意点

- 外部 Logger は任意です。未導入でも標準ロガーへフォールバックします。
- 新しい Minecraft 版で保存・更新したワールドを古い版で開くダウングレードは非対応です。
- 詳細な機能説明は、該当する個別ドキュメントを参照してください。

