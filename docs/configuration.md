# 設定

`plugins/StorageSign-Refactored/config.yml` で変更できます。実際の既定値は `src/main/resources/config.default.yml` を参照してください。

初回起動時に新版の `config.yml` がなく、`plugins/StorageSign/config.yml` が存在する場合は、
元版の設定を新版フォルダへ自動コピーします。両方存在する場合は新版側を使用し、
旧ファイルを上書き・削除しません。

## 主要キー

| キー | 説明 |
|---|---|
| `no-permisson` | 権限不足時メッセージ |
| `log-level` | ログレベル |
| `manual-import` | 手動インポートの有効化 |
| `manual-export` | 手動エクスポートの有効化 |
| `auto-import` | 自動インポートの有効化 |
| `auto-export` | 自動エクスポートの有効化 |
| `autocollect` | ドロップアイテム自動収集の有効化 |
| `hardrecipe` | 難易度の高いクラフトレシピを使うか |
| `divide-limit` | 分割時の 1 枚あたり上限 |
| `sneak-divide-limit` | スニーク時の分割上限 |
| `max-stack-size` | StorageSign アイテムのスタック上限 |
| `unregister-on-empty` | 0 個になったときに登録解除するか |
| `no-bud` | BUD パルスによる破壊防止 |
| `falling-block-itemSS` | 落下ブロック着地時のアイテム化 |
| `banner-debug` | TRACE 時の ItemMeta 追加ログ |

## 索引・近接表示

索引と近接表示の詳細は [docs/storage-sign-index.md](docs/storage-sign-index.md) を参照してください。

## 互換設定

| キー | 説明 |
|---|---|
| `item-identifier-aliases` | 旧識別子や別名を現行 Material 名に寄せる |
| `potion-key-aliases` | 旧 Potion Registry キーの移行 |
| `brewing-ingredient-identifiers` | 追加の醸造材料識別子 |
| `virtual-item-identifiers` | 実在しない識別子のマッピング |

## 注意

- `storage-index.enabled: false` なら近接表示も無効になります。
- `storage-index.chunk-rescan-queue-cap` は、大量チャンク読込環境向けの逃げ道です。
- 0 以下で既定値へ戻るキーがあります。詳細な丸め規則は `docs/storage-sign-index.md` を参照してください。
