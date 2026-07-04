# 変更時に確認するドキュメント

変更時の更新先だけをまとめた一覧です。詳細な作業手順は `AGENTS.md` と各 `docs/workflow-*.md` を見てください。

## まず見るもの

- ランディングページ: `README.md`
- 概要・互換性: `docs/compatibility.md`
- 導入と使い方: `docs/getting-started.md`
- コマンド・権限: `docs/commands.md`
- 設定: `docs/configuration.md`
- 運用・注意点: `docs/operations.md`
- 開発・テスト: `docs/development.md`
- 実装変更: `docs/workflow-implementation.md`
- テスト変更: `docs/workflow-testing.md`
- ドキュメント変更: `docs/workflow-documentation.md`
- カバレッジ差分整理: `docs/test-gap-audit.md`

## 変更タイプ別の更新先

- 機能追加・変更: 該当する機能ドキュメント + `docs/test-cases.md`
- 概要・互換性: `docs/compatibility.md` + `docs/test-cases.md`
- 導入・使い方: `docs/getting-started.md`
- 索引・検索・`/sswarp`・近接表示・外部CLI/viewer: `docs/storage-sign-index.md`
- 設定変更: `docs/configuration.md` + `src/main/resources/config.default.yml`
- コマンド・権限: `docs/commands.md` + `src/main/resources/plugin.yml`
- 運用・Logger・診断: `docs/operations.md` + `docs/runtime-validation-checklist.md`
- テスト追加・失敗条件変更: `docs/test-cases.md` + `docs/test-gap-audit.md`
- 開発メモや実装詳細: `docs/development.md`

## ルール

- README は短い案内ページとして維持する
- 公開挙動が変わるなら該当ドキュメントを先に直す
- テストが増えたら `docs/test-cases.md` を更新する
- 保存済みのテスト結果要約を書き換えるなら、根拠になる成果物や実行ログに合わせる
- 手動/E2E 手順が変わったら `docs/runtime-validation-checklist.md` を更新する
- `coverage` を再実行して差分が出たら `docs/test-gap-audit.md` を更新する
- `docs/test-gap-audit.md` は保存済み coverage artifact の要約として扱い、ソース変更後に coverage 未再実行の間は current 状態と混同しない
- 設定や権限が変わったらテンプレートと `docs/configuration.md` / `docs/commands.md` を更新する
