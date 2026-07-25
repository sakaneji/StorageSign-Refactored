# コマンドと権限

## コマンド

### `/storagesigngive` / `/ssgive`

クリエイティブモードのプレイヤーに StorageSign アイテムを付与します。

```text
/storagesigngive <itemIdentifier> <amount> [signType]
/ssgive <itemIdentifier> <amount> [signType]
```

| 引数 | 必須 | 説明 |
|---|---|---|
| `itemIdentifier` | ○ | アイテム識別子 |
| `amount` | ○ | 保管数量 |
| `signType` | - | 看板素材。省略時は `OAK_SIGN` |

### `/storagesignindex` / `/ssindex`

ロード済みチャンクの StorageSign 位置索引を確認・再構築します。詳細は [docs/storage-sign-index.md](docs/storage-sign-index.md) を参照してください。

### `/storagesignsearch` / `/sssearch`

位置索引スナップショットから完全識別子を検索します。詳細は [docs/storage-sign-index.md](docs/storage-sign-index.md) を参照してください。

### `/storagesignwarp` / `/sswarp`

最寄りの StorageSign の前面へワープします。詳細は [docs/storage-sign-index.md](docs/storage-sign-index.md) を参照してください。

## 権限

| 権限ノード | デフォルト | 説明 |
|---|---|---|
| `storagesign.*` | OP | すべての権限を付与 |
| `storagesign.use` | 全員 | StorageSign のインタラクション |
| `storagesign.craft` | 全員 | クラフトの許可 |
| `storagesign.place` | 全員 | 看板としての設置 |
| `storagesign.break` | 全員 | StorageSign本体、および取り付け先の支持ブロックの破壊 |
| `storagesign.give` | 全員 | `/storagesigngive` の使用 |
| `storagesign.autocollect` | 全員 | 自動収集 |
| `storagesign.index.admin` | OP | 位置索引の状態確認・手動再構築 |
| `storagesign.search.admin` | OP | 位置索引スナップショットの検索 |
