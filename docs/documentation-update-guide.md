# 変更時に確認するドキュメント

変更時の更新先だけをまとめた一覧です。詳細な作業手順は `AGENTS.md` と各 `docs/workflow-*.md` を見てください。

## まず見るもの

- ユーザー向け変更: `README.md`
- 実装変更: `docs/workflow-implementation.md`
- テスト変更: `docs/workflow-testing.md`
- ドキュメント変更: `docs/workflow-documentation.md`
- 仕様差分整理: `docs/test-gap-audit.md`

## 変更タイプ別の更新先

- 機能追加・変更: `README.md` + `docs/test-cases.md`
- 索引・検索・近接表示: `README.md` + `docs/storage-sign-index.md`
- 設定変更: `README.md` + `src/main/resources/config.default.yml`
- コマンド・権限: `README.md` + `src/main/resources/plugin.yml`
- 互換性・アップグレード: `README.md` + `docs/runtime-validation-checklist.md`
- Logger / 診断: `README.md` + `docs/runtime-validation-checklist.md`
- テスト追加・失敗条件変更: `docs/test-cases.md` + `docs/test-gap-audit.md`

## ルール

- 公開挙動が変わるなら README を先に直す
- テストが増えたら `docs/test-cases.md` を更新する
- 手動/E2E 手順が変わったら `docs/runtime-validation-checklist.md` を更新する
- 設定や権限が変わったらテンプレートと README の両方を更新する
