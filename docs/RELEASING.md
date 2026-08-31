# すぐのる Release手順

この文書は、検証用CIと正式Releaseを区別し、既存の署名鍵・タグ・Releaseを壊さずにAPKを公開するための手順です。通常の開発・起動方法は [README.md](../README.md) を参照してください。

## 検証用CI

`main` へのpushと `main` を対象とするPull Requestでは、GitHub Actionsが次を実行します。

- JDK 21、Android SDK platform 35、build-tools 36.0.0でのテスト、Lint、デバッグAPK生成
- APK署名、application ID `jp.sugunoru.app`、許可されたAndroid権限の検証
- `sugunoru-debug-<commit SHA>` というWorkflow Artifactの14日間保存

このartifactはデバッグ署名の検証用APKです。GitHub Releasesの正式ファイルは作成・更新しません。Pull RequestではRelease署名Secretsを参照しません。

## 正式Releaseの条件

正式Releaseは、`vMAJOR.MINOR.PATCH` 形式の新しいタグをpushした場合にだけ作成されます。次の条件をすべて満たす必要があります。

- タグと完成APKの `versionName` が一致する。
- `versionCode` が正の整数である。
- 署名、application ID、権限の検証が成功する。
- 同名タグのGitHub Releaseがまだ存在しない。

成功すると次のファイルをReleaseへ添付します。

- `sugunoru.apk`
- `sugunoru.apk.sha256`

既存の `v2.1.0`、`v2.1.1` タグとReleaseは移動、上書き、削除しないでください。同じタグのReleaseが既に存在する場合、ワークフローは上書きせず失敗します。

## GitHub Actions Secrets

固定した同一のRelease署名鍵について、リポジトリのActions Secretsへ次を登録します。

- `ANDROID_KEYSTORE_BASE64` — keystoreファイル全体をBase64化した値
- `ANDROID_KEYSTORE_PASSWORD` — keystoreのパスワード
- `ANDROID_KEY_ALIAS` — 署名鍵のエイリアス
- `ANDROID_KEY_PASSWORD` — 署名鍵のパスワード

keystoreはリポジトリへコミットせず、安全なオフライン媒体へ複数バックアップしてください。keystoreまたはパスワードを失うと、同じ署名を必要とする既存APKの更新を配布できなくなります。Release作成にはGitHub標準の `GITHUB_TOKEN` を使うため、個人アクセストークンは不要です。

## ローカルReleaseビルド

必要環境はJDK 17以上、Android SDK platform 35、および `apksigner` を含むAndroid SDK build-toolsです。`build.ps1` はインストール済みbuild-toolsのうち最新の版を選びます。

ローカルではBase64値ではなく、復元済みkeystoreのリポジトリ外パスを `ANDROID_KEYSTORE_PATH` または `-KeystorePath` で指定します。加えて次の環境変数を設定します。

- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

```powershell
./build.ps1 -Configuration Release -KeystorePath 'D:\secure\sugunoru-release.jks'
```

署名設定が不足している場合は、デバッグ鍵を生成・流用せず明示的に失敗します。成功時は既定で `dist/sugunoru.apk` と `dist/sugunoru.apk.sha256` を出力し、`apksigner` で署名を検証します。

## 次版を公開する例

以下は `2.1.2` を公開する場合の例です。

1. [`app/build.gradle`](../app/build.gradle) の `versionName` を `2.1.2`、`versionCode` を現在より大きい正の整数へ更新します。
2. 変更をコミットして `main` へpushし、検証用CIの成功を確認します。
3. 新しい注釈付きタグを作成し、そのタグだけをpushします。

```powershell
git add app/build.gradle
git commit -m "chore: prepare v2.1.2"
git push origin main
git tag -a v2.1.2 -m "v2.1.2"
git push origin v2.1.2
```

タグをpushするとReleaseワークフローが起動します。既存タグの付け替え、force push、既存Releaseの削除は行わないでください。

## 公開後の確認

- GitHub Releaseのタグ、APKの `versionName`、ファイル名が一致している。
- `sugunoru.apk` と `.sha256` が両方添付されている。
- 公開APKがデバッグ証明書ではなく、保管済みRelease鍵で署名されている。
- 新規端末でインストール・起動でき、既存版から更新できる。
