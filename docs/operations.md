# 運用と注意点

## ログ

- ログメッセージには `[Class#operation]` 形式で発生箇所が付きます。
- 通常運用では `INFO` を使います。
- 問題調査では `DEBUG` を使います。
- `TRACE` は自動搬送や `banner-debug` の生 ItemMeta を出力するため、必要な期間だけ有効にしてください。

## バックアップ

- `storage-sign-index.bin` が壊れても本体データは失われません。
- 破損や未対応バージョンを検出した場合は `.corrupt-<timestamp>` へ退避し、ロード済みチャンクから再構築します。
- 索引を削除しても StorageSign 本体は失われませんが、ワールドと同じ時点の索引を使うのが安全です。

## 制約

- Spigot は製品保証およびリリース試験の対象外です。
- 新しい Minecraft 版で保存・更新したワールドを古い版で開くダウングレードは非対応です。
- `storage-index.enabled: false` のときは近接表示も自動で無効になります。

## 手動確認

- 手動実動作確認は `docker/manual/compose.yml` を使います。
- `docker/manual/local/ops.json` はローカル用の op 一覧です。
- 詳細な手順は [runtime-validation-checklist.md](runtime-validation-checklist.md) を参照してください。
