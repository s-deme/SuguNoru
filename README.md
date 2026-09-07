# すぐのる

「出かける」「帰る」それぞれで、よく使う都営バスの次の便をすぐ確認する個人向け Android アプリです。現在の製品版は `2.1.1` です。

## できること

- 出かける／帰るをタブで切り替え、それぞれに路線を登録
- 都営バスの路線・系統、停留所、行き先・方面を順に選択
- 入力なしで現在時刻からの次の便を発車時刻順に表示
- 次の3便と、平日／土日の駅・停留所時刻表を表示
- 交通事業者の公式時刻表ページ（HTTPS）から平日／土日／祝日の時刻を取得
- 取得済みの時刻表を端末に保存し、オフライン時は最終取得データを使用
- 祝日・臨時休日ダイヤ、出発通知、ホーム画面ウィジェット
- JSONバックアップ／復元、1世代の自動バックアップ
- 時刻表用の等間隔ダイヤ生成
- 登録内容を端末内に保存し、通信なしで利用
- `07:35`、`0735`、全角コロン、空白・改行・カンマ区切りの時刻入力に対応

## 初版の判断

交通事業者ごとに公開形式や利用規約が異なるため、公式時刻表は登録したHTTPSページから取得します。一般的なHTMLの時刻表（`07:35`形式、または時間と分が分かれた表）と簡易JSONを読み取ります。ログインが必要なページやJavaScriptだけで表示されるページは対象外です。運休・遅延、位置情報、乗換案内は対象外です。

登録画面には、都営バス32系統の候補ディレクトリを同梱しています。路線・系統、停留所、行き先・方面はすべて候補から選び、手入力はしません。この候補は時刻表・運行情報を含まないオフライン用の一覧です。路線改編時はアプリ更新で候補を更新し、保存済みの登録はそのまま利用できます。

今後の時刻表・停留所データは [公共交通オープンデータセンター（ODPT）](https://developer.odpt.org/) のAPIを利用する予定です。ODPTは利用者登録後に発行されるAPIキーが必要なため、現時点のアプリはODPTへ接続せず、キーもリポジトリやAPKへ含めません。接続時は、データの取得日時・出典表示などODPTの利用条件に従います。

ホームでは、現在時刻以降で最も早い登録便を各路線から取り出し、発車時刻が早い順に並べます。乗換・遅延は含みません。

## 開発・実行

必要環境はAndroid Studio、JDK 17以上、Android SDK platform 35とAndroid SDK build-toolsです。`build.ps1` はインストール済みbuild-toolsのうち最新の版に含まれる `apksigner` を使って、完成APKの署名を検証します。

```powershell
./build.ps1 -Configuration Debug
```

`build.ps1` は `JAVA_HOME` と `ANDROID_SDK_ROOT` を優先し、SDK環境変数がない場合は `ANDROID_HOME`、`local.properties` の順に参照します。生成APKは既定で `dist/sugunoru-debug.apk` へコピーされます。出力先は `-OutputDirectory` または `SUGUNORU_OUTPUT_DIR` で変更できます。

スクリプトを介さず検証する場合は、次のコマンドも利用できます。

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Android Studioでは、このフォルダーを開いて `app` 構成を実行してください。

## プラットフォームと権限

- application ID: `jp.sugunoru.app`
- minimum SDK: 26
- compile / target SDK: 35
- 使用権限: 公式時刻表の取得に必要な `INTERNET` / `ACCESS_NETWORK_STATE`、通知のための `POST_NOTIFICATIONS`、振動のための `VIBRATE`
- 位置情報権限、アカウント、分析SDKは使用しません。

## CIと正式Release

`main` へのpushとPull Requestでは、GitHub Actionsがテスト、Lint、デバッグAPK生成、署名・パッケージID・権限検証を行い、検証用artifactを14日間保存します。正式Releaseは `vMAJOR.MINOR.PATCH` 形式の新しいタグでのみ作成します。

Release署名、Secrets、ローカルReleaseビルド、タグ作成、既存Releaseを保護する手順は [docs/RELEASING.md](docs/RELEASING.md) を参照してください。

## データについて

登録内容と最終取得した時刻表は Android のアプリ専用 `SharedPreferences` に JSON として保存されます。公式ページを設定した路線は、前回取得から24時間以上経過している場合にアプリ起動時に更新します。このとき送るのは設定したページへの通常の取得リクエストだけで、他の登録内容は送信しません。更新できない場合は時刻表を上書きせず、保存済みの最終取得データを使います。OSクラウドバックアップは無効です。必要なときは設定画面からJSONを書き出してください。アプリをアンインストールすると端末内データは削除されます。

全改善項目と実装対応は [`docs/PRODUCT_AUDIT.md`](docs/PRODUCT_AUDIT.md) を参照してください。
色、文字サイズ、入力枠、大きな文字への対応結果は [`docs/READABILITY_AUDIT.md`](docs/READABILITY_AUDIT.md) に記録しています。
