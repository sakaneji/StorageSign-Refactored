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

JUnit の `skipped` は成功件数へ含めません。`scripts/test.sh` に理由と
`classname#testname` が明示された既知ケースだけを許可し、未登録の skip、
Surefire XML の集計不一致、XML を生成しない実行は失敗します。

E2E は Docker 内部ネットワークだけを使い、ホストの `25565` は公開しません。
各構成の開始前後に Compose project を削除し、前回中断時のサーバーやロード済み
JAR を再利用しないことを前提にします。`E2E_CASE_FILTER` を指定した場合は、対象
phase で1件以上が選択・完了しない限り失敗します。

bot phase の `E2E PASS` 要約には `selected` / `executed` /
`synthetic_fallbacks` / `observation` を含め、runnerの構成別要約にもfallback合計と
観測区分を伝播します。Mineflayer の操作が成立せず、実 Paper 上でテストハーネスが
Bukkit event を発火した場合は `observation=mixed-client-and-synthetic` と表示し、
純粋なクライアント操作だけの結果と区別します。

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
