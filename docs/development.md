# 開発とテスト

## プロジェクト構成

- `src/main/java/storagesign/` に本体実装があります。
- `src/test/java/storagesign/` に Unit / Integration / policy / E2E 補助テストがあります。
- `tools/` に保存済み索引を読む CLI / viewer / offline 再構築ツールがあります。

## ビルド

必要なもの:

- JDK 21 以降
- Maven 3.8 以降

```bash
mvn package
```

生成物は `target/StorageSign-Refactored-<version>.jar` です。

## テスト

実行は `./scripts/test.sh` を使います。

```bash
./scripts/test.sh unit
./scripts/test.sh integration
./scripts/test.sh coverage
./scripts/test.sh e2e
./scripts/test.sh all
```

Java 系と Paper E2E は Docker を使い、Python ツールテストだけはホストの `python3` で実行します。Python ツール群は `python3` 3.10 以上が必要です。

## データモデル

`StorageSign` は看板ブロックとインベントリアイテムの両方を表すデータモデルです。数量と登録状態は入出庫に応じて更新されます。

看板ブロックの 4 行は次の通りです。

```text
行 0: "StorageSign"
行 1: 表示用識別子
行 2: 保管数量
行 3: サマリー
```

## 設定の拡張

`item-identifier-aliases`、`potion-key-aliases`、`brewing-ingredient-identifiers`、`virtual-item-identifiers` はコード変更なしで拡張できます。詳細は `docs/configuration.md` を参照してください。

