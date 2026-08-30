# すぐのる

「出かける」「帰る」それぞれで、よく使う電車・バスの次発と到着見込みを素早く比較する個人向け Android アプリです。現在の製品版は `2.1.1` です。

## できること

- 行き／帰り別に路線、駅・停留所、行き先を登録
- 現在地から乗り場までの徒歩時間を考慮して、実際に乗れる次の便を表示
- 乗車時間を含む到着見込みが早い順に候補を比較
- 次の3便と、平日／土日の駅・停留所時刻表を表示
- 祝日・臨時休日ダイヤ、到着後の徒歩、時刻表の有効期限に対応
- 任意日時での事前シミュレーション、出発通知、ホーム画面ウィジェット
- JSONバックアップ／復元、1世代の自動バックアップ
- 等間隔ダイヤ生成、登録の複製、一時無効化、乗り場メモ
- 登録内容を端末内に保存し、通信なしで利用
- `07:35`、`0735`、全角コロン、空白・改行・カンマ区切りの時刻入力に対応

## 初版の判断

事業者横断のリアルタイム時刻表は、地域・交通事業者ごとにAPI、利用規約、データ形式が異なります。そのため現版は、個人が日常の数路線を確実に使える手入力方式です。運休・遅延、現在地からの動的な徒歩時間、乗換案内は対象外です。

「最速」は、`現在時刻 + 徒歩時間` 以降に乗れる最初の便を探し、`発車時刻 + 乗車時間` が最も早い候補として判定します。乗換・遅延は含みません。

## 開発・実行

必要環境は Android Studio、JDK 17 以上、Android SDK 35 です。

```powershell
./build.ps1 -Configuration Debug
```

`build.ps1` は `JAVA_HOME` と `ANDROID_SDK_ROOT` を優先し、SDK環境変数がない場合は `ANDROID_HOME`、`local.properties` の順に参照します。生成APKは既定で `dist/sugunoru-debug.apk` へコピーされます。出力先は `-OutputDirectory` または `SUGUNORU_OUTPUT_DIR` で変更できます。

スクリプトを介さず検証する場合は、次のコマンドも利用できます。

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Android Studioでは、このフォルダーを開いて `app` 構成を実行してください。

## CIと正式Release

`main` へのpushと `main` を対象とするPull Requestでは、GitHub ActionsがJDK 21、Android SDK platform 35、build-tools 36.0.0でテスト・Lint・デバッグAPK生成を行います。APK署名、パッケージID、許可されたAndroid権限も検証し、`sugunoru-debug-<commit SHA>` というWorkflow Artifactを14日間保存します。これは検証用のデバッグ署名APKであり、GitHub Releasesの正式ファイルは作成・更新しません。Pull RequestではRelease署名Secretsを参照しません。

正式Releaseは、`vMAJOR.MINOR.PATCH` 形式の新しいタグをpushした場合にだけ作成されます。タグと完成APKの `versionName` が一致し、`versionCode` が正の整数で、署名・パッケージID・権限検証がすべて成功した場合に限り、次のファイルを最新の正式Releaseへ添付します。

- `sugunoru.apk`
- `sugunoru.apk.sha256`

同じタグのReleaseが既に存在する場合、ワークフローは上書きせず失敗します。既存の `v2.1.0`、`v2.1.1` タグやReleaseは移動・変更しないでください。

### GitHub Secrets

リポジトリのActions Secretsへ、固定した同一のRelease署名鍵について以下を登録します。

- `ANDROID_KEYSTORE_BASE64` — keystoreファイル全体をBase64化した値
- `ANDROID_KEYSTORE_PASSWORD` — keystoreのパスワード
- `ANDROID_KEY_ALIAS` — 署名鍵のエイリアス
- `ANDROID_KEY_PASSWORD` — 署名鍵のパスワード

keystoreはリポジトリへコミットせず、安全なオフライン媒体へ複数バックアップしてください。keystoreまたはパスワードを失うと、同じ署名を必要とする既存APKの更新を配布できなくなります。個人アクセストークンは不要で、Release作成にはGitHub標準の `GITHUB_TOKEN` だけを使用します。

ローカルReleaseビルドでも同じ4つの署名値を環境から渡します。ただしBase64値ではなく、復元済みkeystoreのリポジトリ外パスを `ANDROID_KEYSTORE_PATH` または `-KeystorePath` で指定します。署名設定が不足している場合は、デバッグ鍵を生成・流用せず明示的に失敗します。

### v2.1.2を公開する例

1. [`app/build.gradle`](app/build.gradle) の `versionName` を `2.1.2`、`versionCode` を現在より大きい正の整数へ更新します。ここで指定した値が統合後のAndroidManifestへ反映されます。
2. 変更をコミットして `main` へpushし、CIの成功を確認します。
3. 新しいタグを作成して、そのタグだけをpushします。

```powershell
git add app/build.gradle
git commit -m "chore: prepare v2.1.2"
git push origin main
git tag -a v2.1.2 -m "v2.1.2"
git push origin v2.1.2
```

タグをpushするとReleaseワークフローが起動します。既存タグの付け替え、force push、既存Releaseの削除は行わないでください。

## データについて

登録内容は Android のアプリ専用 `SharedPreferences` に JSON として保存され、外部へ送信されません。OSクラウドバックアップは無効です。必要なときは設定画面からJSONを書き出してください。アプリをアンインストールすると端末内データは削除されます。

全改善項目と実装対応は [`docs/PRODUCT_AUDIT.md`](docs/PRODUCT_AUDIT.md) を参照してください。
色、文字サイズ、入力枠、大きな文字への対応結果は [`docs/READABILITY_AUDIT.md`](docs/READABILITY_AUDIT.md) に記録しています。
