package jp.sugunoru.app;

import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;

import jp.sugunoru.app.data.RouteRepository;
import jp.sugunoru.app.data.AppPreferences;
import jp.sugunoru.app.data.OfficialTimetableFetcher;
import jp.sugunoru.app.data.OfficialTimetableParser;
import jp.sugunoru.app.data.TransitCatalog;
import jp.sugunoru.app.model.RoutePlan;
import jp.sugunoru.app.model.ScheduleEngine;
import jp.sugunoru.app.notification.DepartureAlarmReceiver;
import jp.sugunoru.app.widget.NextDepartureWidget;
import jp.sugunoru.app.ui.StyledActivity;
import jp.sugunoru.app.ui.SystemBarInsetsApplier;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static jp.sugunoru.app.ui.ScheduleDisplayFormatter.clock;
import static jp.sugunoru.app.ui.ScheduleDisplayFormatter.date;
import static jp.sugunoru.app.ui.ScheduleDisplayFormatter.time;
import static jp.sugunoru.app.ui.ScheduleDisplayFormatter.times;
import static jp.sugunoru.app.ui.UiPalette.*;

public final class MainActivity extends StyledActivity {
    private enum Screen { DASHBOARD, FORM, TIMETABLE, SETTINGS }
    private static final int REQUEST_EXPORT = 301;
    private static final int REQUEST_IMPORT = 302;
    private static final int REQUEST_NOTIFICATIONS = 303;
    private static final long OFFICIAL_TIMETABLE_REFRESH_INTERVAL_MILLIS = 24L * 60 * 60 * 1_000;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final List<RoutePlan> plans = new ArrayList<>();
    private final ExecutorService officialTimetableExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> refreshingOfficialTimetableIds = new HashSet<>();
    private RouteRepository repository;
    private AppPreferences appPreferences;
    private RoutePlan.Direction direction = RoutePlan.Direction.OUTBOUND;
    private Screen screen = Screen.DASHBOARD;
    private RoutePlan selectedPlan;
    private TextView liveClock;
    private TextView liveDate;
    private LocalDateTime previewTime;
    private RoutePlan pendingReminderPlan;
    private ScheduleEngine.Departure pendingReminderDeparture;
    private int lastRenderedMinute = -1;
    private int timetableScheduleType;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (screen == Screen.DASHBOARD && liveClock != null) {
                LocalDateTime now = LocalDateTime.now();
                if (previewTime == null && lastRenderedMinute != now.getMinute()) {
                    showDashboard();
                }
            }
            clockHandler.postDelayed(this, 15_000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        applyFor(this);
        configureSystemBars();
        repository = new RouteRepository(this);
        appPreferences = new AppPreferences(this);
        plans.addAll(repository.load());
        direction = appPreferences.direction();
        applyLaunchDirection(getIntent());
        if (state == null) {
            showDashboard();
        } else {
            String preview = state.getString("previewTime");
            if (preview != null) previewTime = LocalDateTime.parse(preview);
            String selectedId = state.getString("selectedPlanId");
            if (selectedId != null) {
                for (RoutePlan plan : plans) if (plan.id().equals(selectedId)) selectedPlan = plan;
            }
            String restoredScreen = state.getString("screen", Screen.DASHBOARD.name());
            try { screen = Screen.valueOf(restoredScreen); } catch (IllegalArgumentException ignored) {}
            timetableScheduleType = state.getInt("scheduleType", 0);
            if (screen == Screen.FORM) showForm(selectedPlan);
            else if (screen == Screen.TIMETABLE && selectedPlan != null) showTimetableFor(selectedPlan, timetableScheduleType);
            else if (screen == Screen.SETTINGS) showSettings();
            else showDashboard();
        }
        refreshStaleOfficialTimetables();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLaunchDirection(intent);
        showDashboard();
        refreshStaleOfficialTimetables();
    }

    private void applyLaunchDirection(Intent intent) {
        if (intent == null) return;
        String value = intent.getStringExtra("direction");
        if (value == null) return;
        try {
            direction = RoutePlan.Direction.valueOf(value);
            appPreferences.setDirection(direction);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("screen", screen.name());
        if (selectedPlan != null) outState.putString("selectedPlanId", selectedPlan.id());
        if (previewTime != null) outState.putString("previewTime", previewTime.toString());
        outState.putInt("scheduleType", timetableScheduleType);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        configureSystemBarIconAppearance();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureSystemBarIconAppearance();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(CANVAS);
        getWindow().setNavigationBarColor(CANVAS);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(true);
            getWindow().setStatusBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        configureSystemBarIconAppearance();
    }

    private void configureSystemBarIconAppearance() {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : appearance, appearance);
            }
        } else {
            int appearance = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                appearance |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : appearance);
        }
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        officialTimetableExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (screen == Screen.DASHBOARD) {
            super.onBackPressed();
        } else {
            showDashboard();
        }
    }

    private void showDashboard() {
        hideKeyboard();
        screen = Screen.DASHBOARD;
        selectedPlan = null;
        LinearLayout root = vertical(CANVAS);
        root.setPadding(dp(18), dp(12), dp(18), dp(16));

        root.addView(dashboardHeader());
        root.addView(space(16));
        root.addView(nowCard());
        root.addView(space(14));
        root.addView(directionSwitch());
        root.addView(space(18));

        LinearLayout headingRow = horizontal(Gravity.CENTER_VERTICAL);
        TextView heading = text(direction == RoutePlan.Direction.OUTBOUND ? "出かける便" : "帰る便",
                19, INK, Typeface.BOLD);
        markAsHeading(heading);
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int activeCount = 0;
        for (RoutePlan plan : plans) if (plan.enabled() && plan.direction() == direction) activeCount++;
        headingRow.addView(pill(activeCount + "件", MUTED, SURFACE_VARIANT));
        root.addView(headingRow);
        TextView help = text("到着が早い順。徒歩と乗車時間を含めて比較しています", 14, MUTED, Typeface.NORMAL);
        help.setPadding(0, dp(4), 0, dp(11));
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = vertical(Color.TRANSPARENT);
        list.setPadding(0, 0, 0, dp(10));

        LocalDateTime reference = referenceTime();
        lastRenderedMinute = reference.getMinute();
        List<ScheduleEngine.RouteOption> options = ScheduleEngine.compare(
                plans, direction, reference, appPreferences.holidays());
        if (options.isEmpty()) {
            list.addView(emptyState());
        } else {
            for (int i = 0; i < options.size(); i++) {
                list.addView(routeCard(options.get(i), i == 0, reference));
                list.addView(space(10));
            }
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = primaryButton("＋  路線を追加");
        add.setOnClickListener(v -> showForm(null));
        root.addView(add);
        setScreenContent(root);
    }

    private View dashboardHeader() {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        TextView mark = text("す", 19, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundGradient(HERO_START, HERO_END, 15));
        mark.setElevation(dp(2));
        row.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout names = vertical(Color.TRANSPARENT);
        names.setPadding(dp(12), 0, 0, 0);
        names.addView(text("すぐのる", 21, INK, Typeface.BOLD));
        names.addView(text("次に乗れる便を、ひと目で", 13, MUTED, Typeface.NORMAL));
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button settingsButton = smallButton("設定");
        settingsButton.setContentDescription("設定とバックアップを開く");
        settingsButton.setOnClickListener(v -> showSettings());
        row.addView(settingsButton);
        return row;
    }

    private View nowCard() {
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setPadding(dp(20), dp(17), dp(20), dp(12));
        card.setBackground(roundGradient(HERO_START, HERO_END, 24));
        card.setElevation(dp(2));

        TextView context = text(previewTime == null ? "いまの時刻" : "指定日時で試算中", 13, WHITE, Typeface.BOLD);
        context.setLetterSpacing(0.04f);
        card.addView(context);

        LinearLayout top = horizontal(Gravity.BOTTOM);
        if (isConstrainedContent()) top.setOrientation(LinearLayout.VERTICAL);
        LocalDateTime shown = referenceTime();
        liveClock = text(clock(shown), 44, Color.WHITE, Typeface.BOLD);
        liveClock.setFontFeatureSettings("tnum");
        liveClock.setLetterSpacing(-0.03f);
        top.addView(liveClock, isConstrainedContent()
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        liveDate = text(date(shown.toLocalDate()), 14, WHITE, Typeface.NORMAL);
        liveDate.setPadding(isConstrainedContent() ? 0 : dp(10), 0, 0, dp(8));
        top.addView(liveDate);
        card.addView(top);

        TextView simulate = text(previewTime == null ? "別の日時で調べる  ›" : "現在時刻に戻す  ×",
                14, WHITE, Typeface.BOLD);
        simulate.setPadding(dp(12), 0, dp(12), 0);
        simulate.setMinHeight(dp(48));
        simulate.setGravity(Gravity.CENTER_VERTICAL);
        simulate.setFocusable(true);
        simulate.setBackground(interactiveBackground(0x1FFFFFFF, 13, 0x55FFFFFF, 1));
        simulate.setOnClickListener(v -> {
            if (previewTime != null) {
                previewTime = null;
                showDashboard();
            } else {
                choosePreviewDateTime();
            }
        });
        card.addView(simulate);
        return card;
    }

    private View directionSwitch() {
        LinearLayout shell = horizontal(Gravity.CENTER);
        shell.setPadding(dp(4), dp(4), dp(4), dp(4));
        shell.setBackground(roundRect(SEGMENT, 18, LINE, 1));
        Button outbound = segmentButton("→  出かける", direction == RoutePlan.Direction.OUTBOUND);
        Button returning = segmentButton("←  帰る", direction == RoutePlan.Direction.RETURN);
        outbound.setOnClickListener(v -> {
            if (direction != RoutePlan.Direction.OUTBOUND) {
                direction = RoutePlan.Direction.OUTBOUND;
                appPreferences.setDirection(direction);
                NextDepartureWidget.updateAll(this);
                showDashboard();
            }
        });
        returning.setOnClickListener(v -> {
            if (direction != RoutePlan.Direction.RETURN) {
                direction = RoutePlan.Direction.RETURN;
                appPreferences.setDirection(direction);
                NextDepartureWidget.updateAll(this);
                showDashboard();
            }
        });
        shell.addView(outbound, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        shell.addView(returning, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return shell;
    }

    private View emptyState() {
        boolean hasThisDirection = false;
        boolean hasActiveRoute = false;
        List<String> missingOfficialTimetableIds = new ArrayList<>();
        for (RoutePlan plan : plans) {
            if (plan.direction() != direction) continue;
            hasThisDirection = true;
            if (plan.enabled()) hasActiveRoute = true;
            if (plan.enabled() && plan.hasOfficialTimetableSource() && !plan.hasCachedTimetable()) {
                missingOfficialTimetableIds.add(plan.id());
            }
        }
        final boolean hasRoutesInDirection = hasThisDirection;
        boolean waitingForOfficialTimetable = !missingOfficialTimetableIds.isEmpty();
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(26));
        card.setBackground(roundRect(BRAND_SURFACE, 22, OUTLINE, 1));
        TextView icon = text(direction == RoutePlan.Direction.OUTBOUND ? "→  出発" : "←  帰宅", 14, BRAND_DARK, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(BRAND_SOFT, 18, 0, 0));
        icon.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.addView(icon);
        card.addView(space(14));
        card.addView(centerText(waitingForOfficialTimetable ? "時刻表を取得待ちです"
                        : hasRoutesInDirection ? (hasActiveRoute ? "次の便が見つかりません" : "比較対象がありません")
                        : "まだ路線がありません",
                19, INK, Typeface.BOLD));
        TextView body = centerText(waitingForOfficialTimetable
                        ? "公式サイトから時刻表を取得すると\nすぐに次の便を比較できます。"
                        : hasRoutesInDirection ? (hasActiveRoute
                                ? "時刻表の確認や公式サイトからの更新を\n行ってください。"
                                : "この場面の登録はすべて無効です。\n設定から比較対象に戻せます。")
                        : "よく使う駅・停留所と時刻表を登録すると\n次に乗れる便をすぐ比較できます。",
                14, MUTED, Typeface.NORMAL);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);
        Button sample = smallButton(waitingForOfficialTimetable ? "公式時刻表を取得"
                : hasRoutesInDirection ? "設定で登録を管理" : "サンプルで試す");
        sample.setMinHeight(dp(48));
        sample.setOnClickListener(v -> {
            if (waitingForOfficialTimetable) refreshOfficialTimetables(missingOfficialTimetableIds, true);
            else if (hasRoutesInDirection) showSettings();
            else installSampleRoutes();
        });
        card.addView(sample);
        return card;
    }

    private View routeCard(ScheduleEngine.RouteOption option, boolean fastest, LocalDateTime reference) {
        RoutePlan plan = option.plan();
        List<ScheduleEngine.Departure> next = ScheduleEngine.nextDepartures(
                plan, reference, 3, appPreferences.holidays());
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setPadding(dp(17), dp(15), dp(17), dp(13));
        card.setBackground(roundRect(fastest ? BRAND_SURFACE : SURFACE, 22,
                fastest ? BRAND : OUTLINE, fastest ? 2 : 1));
        card.setForeground(new RippleDrawable(ColorStateList.valueOf(withAlpha(BRAND, 0x1F)), null, null));
        card.setElevation(fastest ? dp(2) : dp(1));
        card.setOnClickListener(v -> showTimetable(plan));
        card.setFocusable(true);
        card.setContentDescription(plan.routeName() + "、" + plan.stopName() + "から"
                + plan.destination() + "、" + time(option.departure().at()) + "発、"
                + ScheduleEngine.formatMinutes(option.departure().waitMinutes()) + "。タップして時刻表を開く");

        LinearLayout meta = horizontal(Gravity.CENTER_VERTICAL);
        meta.addView(pill(plan.mode() == RoutePlan.Mode.TRAIN ? "電車" : "バス",
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_DARK : AMBER,
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_SOFT : AMBER_SOFT));
        TextView route = text(plan.routeName(), 14, MUTED, Typeface.BOLD);
        route.setPadding(dp(8), 0, 0, 0);
        meta.addView(route, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (fastest) meta.addView(pill("最速", Color.WHITE, BRAND));
        card.addView(meta);

        TextView title = text(plan.stopName() + "  →  " + plan.destination(), 18, INK, Typeface.BOLD);
        title.setPadding(0, dp(11), 0, dp(8));
        card.addView(title);

        LinearLayout timing = vertical(Color.TRANSPARENT);
        LinearLayout departureRow = horizontal(Gravity.BOTTOM);
        TextView departure = text(time(option.departure().at()), 34, INK, Typeface.BOLD);
        departure.setFontFeatureSettings("tnum");
        departureRow.addView(departure);
        TextView suffix = text(option.departure().nextDay() ? "  翌日の便" : "  発", 14, MUTED, Typeface.BOLD);
        suffix.setPadding(0, 0, 0, dp(5));
        departureRow.addView(suffix);
        timing.addView(departureRow);
        TextView wait = pill(ScheduleEngine.formatMinutes(option.departure().waitMinutes()), BRAND_DARK, BRAND_SOFT);
        LinearLayout.LayoutParams waitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        waitParams.setMargins(0, dp(5), 0, 0);
        timing.addView(wait, waitParams);
        card.addView(timing);

        String detail = "徒歩 " + plan.walkMinutes() + "分  ・  乗車 " + plan.rideMinutes()
                + "分" + (plan.finalWalkMinutes() > 0 ? "  ・  降車後 " + plan.finalWalkMinutes() + "分" : "");
        TextView details = text(detail, 14, MUTED, Typeface.NORMAL);
        details.setPadding(0, dp(8), 0, dp(10));
        card.addView(details);

        LinearLayout arrival = horizontal(Gravity.CENTER_VERTICAL);
        arrival.setPadding(dp(13), dp(10), dp(13), dp(10));
        arrival.setBackground(roundRect(BRAND_SOFT, 15, 0, 0));
        arrival.addView(text("到着見込み", 14, BRAND_DARK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrivalTime = text(time(option.estimatedArrival()), 22, BRAND_DARK, Typeface.BOLD);
        arrivalTime.setFontFeatureSettings("tnum");
        arrival.addView(arrivalTime);
        card.addView(arrival);
        card.addView(space(10));
        if (!plan.notes().isEmpty()) {
            TextView notes = text("メモ  " + plan.notes(), 14, INK, Typeface.NORMAL);
            notes.setPadding(0, 0, 0, dp(9));
            card.addView(notes);
        }
        if (plan.validUntil() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(reference.toLocalDate(), plan.validUntil());
            if (days <= 30) {
                String label = days < 0 ? "時刻表の期限切れ" : "時刻表期限まで " + days + "日";
                TextView expiry = pill(label, days < 0 ? DANGER : AMBER,
                        days < 0 ? DANGER_SOFT : AMBER_SOFT);
                LinearLayout.LayoutParams expiryParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                expiryParams.setMargins(0, 0, 0, dp(9));
                card.addView(expiry, expiryParams);
            }
        }

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        card.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout footer = vertical(Color.TRANSPARENT);
        footer.setPadding(0, dp(10), 0, 0);
        StringBuilder following = new StringBuilder("このあと  ");
        for (int i = 1; i < next.size(); i++) {
            if (i > 1) following.append("  ·  ");
            following.append(time(next.get(i).at()));
        }
        if (next.size() <= 1) following.append("登録便なし");
        LinearLayout nextRow = horizontal(Gravity.CENTER_VERTICAL);
        if (isConstrainedContent()) nextRow.setOrientation(LinearLayout.VERTICAL);
        nextRow.addView(text(following.toString(), 14, MUTED, Typeface.NORMAL),
                isConstrainedContent()
                        ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView timetableLink = text("時刻表を見る  ›", 14, BRAND_DARK, Typeface.BOLD);
        if (isConstrainedContent()) timetableLink.setPadding(0, dp(6), 0, 0);
        nextRow.addView(timetableLink);
        footer.addView(nextRow);
        LinearLayout actions = horizontal(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(6), 0, 0);
        Button alert = smallButton("通知");
        alert.setContentDescription("この便に間に合う出発時刻を通知");
        if (!next.isEmpty()) alert.setOnClickListener(v -> requestReminder(plan, next.get(0)));
        if (previewTime == null) actions.addView(alert);
        Button edit = smallButton("編集");
        edit.setOnClickListener(v -> showForm(plan));
        actions.addView(edit);
        footer.addView(actions);
        card.addView(footer);
        return card;
    }

    private void showForm(RoutePlan existing) {
        hideKeyboard();
        screen = Screen.FORM;
        selectedPlan = existing;

        LinearLayout root = vertical(CANVAS);
        root.addView(pageHeader(existing == null ? "路線を登録" : "登録を編集", this::showDashboard,
                existing == null ? null : () -> confirmDelete(existing)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = vertical(Color.TRANSPARENT);
        form.setPadding(dp(18), dp(10), dp(18), dp(28));

        TextView intro = text(existing == null
                        ? "いつもの乗り場と時刻表を登録すると、乗れる便を自動で比較します。"
                        : "登録内容を更新すると、ホームの比較結果へすぐ反映されます。",
                14, BRAND_DARK, Typeface.NORMAL);
        intro.setPadding(dp(15), dp(13), dp(15), dp(13));
        intro.setBackground(roundRect(BRAND_SOFT, 16, 0, 0));
        form.addView(intro);
        form.addView(space(22));
        form.addView(formSectionTitle("1", "基本情報", "使う場面と乗車区間"));
        form.addView(space(12));

        Spinner directionInput = spinner(new String[]{"出かけるとき", "帰るとき"});
        directionInput.setId(R.id.form_direction);
        directionInput.setSelection((existing == null ? direction : existing.direction()) == RoutePlan.Direction.OUTBOUND ? 0 : 1);
        addField(form, "使う場面", directionInput, "ホーム画面で行き・帰りを切り替えられます");

        Spinner modeInput = spinner(new String[]{"電車", "バス"});
        modeInput.setId(R.id.form_mode);
        modeInput.setSelection(existing != null && existing.mode() == RoutePlan.Mode.BUS ? 1 : 0);
        addField(form, "交通手段", modeInput, null);

        CheckBox enabledInput = new CheckBox(this);
        enabledInput.setId(R.id.form_enabled);
        enabledInput.setText("ホームの比較対象にする");
        enabledInput.setTextColor(INK);
        enabledInput.setTextSize(16);
        enabledInput.setMinHeight(dp(52));
        enabledInput.setChecked(existing == null || existing.enabled());
        form.addView(enabledInput);
        form.addView(space(12));

        EditText routeInput = input("例：中央線 快速", InputType.TYPE_CLASS_TEXT, false);
        routeInput.setId(R.id.form_route);
        EditText stopInput = input("例：中野駅", InputType.TYPE_CLASS_TEXT, false);
        stopInput.setId(R.id.form_stop);
        EditText destinationInput = input("例：東京方面", InputType.TYPE_CLASS_TEXT, false);
        destinationInput.setId(R.id.form_destination);
        EditText walkInput = input("例：8", InputType.TYPE_CLASS_NUMBER, false);
        walkInput.setId(R.id.form_walk);
        EditText rideInput = input("例：22", InputType.TYPE_CLASS_NUMBER, false);
        rideInput.setId(R.id.form_ride);
        EditText finalWalkInput = input("例：5", InputType.TYPE_CLASS_NUMBER, false);
        finalWalkInput.setId(R.id.form_final_walk);

        LinearLayout catalogCard = vertical(SURFACE_VARIANT);
        catalogCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        catalogCard.setBackground(roundRect(SURFACE_VARIANT, 16, BRAND_DARK, 1));
        TextView catalogTitle = text("JR東日本・都営バスから選ぶ", 16, INK, Typeface.BOLD);
        markAsHeading(catalogTitle);
        catalogCard.addView(catalogTitle);
        TextView catalogHelp = text("路線・系統、駅・停留所、方面の順に選ぶと、下の入力欄へ反映します。"
                        + "候補外や通過駅は手入力で登録できます。",
                13, MUTED, Typeface.NORMAL);
        catalogHelp.setPadding(0, dp(4), 0, dp(10));
        catalogCard.addView(catalogHelp);
        LinearLayout catalogActions = horizontal(Gravity.CENTER_VERTICAL);
        Button chooseJr = secondaryButton("JR東日本の駅を選ぶ");
        Button chooseToei = secondaryButton("都営バスの停留所を選ぶ");
        if (isConstrainedContent()) {
            catalogActions.setOrientation(LinearLayout.VERTICAL);
            catalogActions.addView(chooseJr);
            catalogActions.addView(space(8));
            catalogActions.addView(chooseToei);
        } else {
            LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            first.setMarginEnd(dp(4));
            catalogActions.addView(chooseJr, first);
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            second.setMarginStart(dp(4));
            catalogActions.addView(chooseToei, second);
        }
        catalogCard.addView(catalogActions);
        TextView catalogStatus = text("選択後も路線名・乗り場・行き先を自由に編集できます。",
                13, BRAND_DARK, Typeface.NORMAL);
        catalogStatus.setPadding(0, dp(10), 0, 0);
        catalogCard.addView(catalogStatus);
        chooseJr.setContentDescription("JR東日本の路線、駅、方面を選ぶ");
        chooseToei.setContentDescription("都営バスの系統、停留所、方面を選ぶ");
        chooseJr.setOnClickListener(v -> showTransitServicePicker(TransitCatalog.Provider.JR_EAST,
                modeInput, routeInput, stopInput, destinationInput, catalogStatus));
        chooseToei.setOnClickListener(v -> showTransitServicePicker(TransitCatalog.Provider.TOEI_BUS,
                modeInput, routeInput, stopInput, destinationInput, catalogStatus));
        form.addView(catalogCard);
        form.addView(space(16));

        addField(form, "路線名", routeInput, null);
        addField(form, "乗る駅・停留所", stopInput, null);
        addField(form, "行き先", destinationInput, null);

        form.addView(formSectionTitle("2", "所要時間", "乗れる便と到着時刻の計算に使います"));
        form.addView(space(12));

        LinearLayout durations = horizontal(Gravity.TOP);
        LinearLayout walkBox = vertical(Color.TRANSPARENT);
        LinearLayout rideBox = vertical(Color.TRANSPARENT);
        addField(walkBox, "ここまで徒歩（分）", walkInput, null);
        addField(rideBox, "乗車時間（分）", rideInput, null);
        if (isConstrainedContent()) {
            durations.setOrientation(LinearLayout.VERTICAL);
            durations.addView(walkBox);
            durations.addView(rideBox);
        } else {
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            half.setMarginEnd(dp(6));
            durations.addView(walkBox, half);
            LinearLayout.LayoutParams otherHalf = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            otherHalf.setMarginStart(dp(6));
            durations.addView(rideBox, otherHalf);
        }
        form.addView(durations);
        addField(form, "降りてから目的地まで（分）", finalWalkInput,
                "最終的な到着時刻の比較に含めます。なければ 0");

        form.addView(formSectionTitle("3", "時刻表", "公式ページから取得し、通信できないときは保存済みのデータを使います"));
        form.addView(space(12));

        EditText officialTimetableUrlInput = input("https://…",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false);
        officialTimetableUrlInput.setId(R.id.form_official_timetable_url);
        EditText weekdayInput = input("07:05  07:18  07:34\n08:02  08:20", InputType.TYPE_CLASS_TEXT, true);
        weekdayInput.setId(R.id.form_weekday);
        EditText weekendInput = input("08:10  08:40  09:10", InputType.TYPE_CLASS_TEXT, true);
        weekendInput.setId(R.id.form_weekend);
        EditText holidayInput = input("08:10  08:40  09:10", InputType.TYPE_CLASS_TEXT, true);
        holidayInput.setId(R.id.form_holiday);
        addField(form, "公式時刻表ページ（任意）", officialTimetableUrlInput,
                "交通事業者の https:// 時刻表ページを指定します。ログインやJavaScriptだけで表示するページは対象外です");
        final long[] draftFetchedAt = {existing == null ? 0 : existing.officialTimetableFetchedAtEpochMillis()};
        final long[] draftAttemptedAt = {existing == null ? 0 : existing.officialTimetableAttemptedAtEpochMillis()};
        final String[] draftLastError = {existing == null ? "" : existing.officialTimetableLastError()};
        final String[] draftFetchedUrl = {existing == null ? "" : existing.officialTimetableUrl()};
        final boolean[] draftFetchedInThisSession = {false};
        Button fetchOfficial = secondaryButton("公式サイトから時刻表を取得");
        fetchOfficial.setContentDescription("公式時刻表ページから平日、土日、祝日の時刻を取得する");
        fetchOfficial.setOnClickListener(v -> {
            String sourceUrl;
            try {
                sourceUrl = RoutePlan.normalizeOfficialTimetableUrl(
                        officialTimetableUrlInput.getText().toString());
            } catch (IllegalArgumentException error) {
                officialTimetableUrlInput.setError(error.getMessage());
                officialTimetableUrlInput.requestFocus();
                return;
            }
            if (sourceUrl.isEmpty()) {
                officialTimetableUrlInput.setError("公式時刻表ページを入力してください");
                officialTimetableUrlInput.requestFocus();
                return;
            }
            officialTimetableUrlInput.setText(sourceUrl);
            fetchOfficialTimetableIntoForm(sourceUrl, weekdayInput, weekendInput, holidayInput,
                    fetchOfficial, () -> {
                        long now = System.currentTimeMillis();
                        draftFetchedAt[0] = now;
                        draftAttemptedAt[0] = now;
                        draftLastError[0] = "";
                        draftFetchedUrl[0] = sourceUrl;
                        draftFetchedInThisSession[0] = true;
                    });
        });
        form.addView(fetchOfficial);
        form.addView(space(18));
        TextView manualTimesLabel = text("手入力の予備", 15, INK, Typeface.BOLD);
        markAsHeading(manualTimesLabel);
        form.addView(manualTimesLabel);
        TextView manualTimesHelp = text("取得結果はここへ反映されます。URLを設定すれば、初回保存後も自動更新します。",
                13, MUTED, Typeface.NORMAL);
        manualTimesHelp.setPadding(0, dp(4), 0, dp(10));
        form.addView(manualTimesHelp);
        addField(form, "平日の時刻表", weekdayInput, "空白・改行・カンマ区切り。0705 の形式でも入力できます");
        addFrequencyBuilder(form, weekdayInput, "平日");
        addField(form, "土日の時刻表", weekendInput, "空欄なら平日の時刻を使います");
        addFrequencyBuilder(form, weekendInput, "土日");
        addField(form, "祝日の時刻表", holidayInput, "設定画面で登録した祝日に使います。空欄なら曜日どおり");
        addFrequencyBuilder(form, holidayInput, "祝日");

        form.addView(space(6));
        form.addView(formSectionTitle("4", "補足", "更新期限と乗り場の目印"));
        form.addView(space(12));

        EditText validUntilInput = input("例：2026-12-31", InputType.TYPE_CLASS_DATETIME, false);
        validUntilInput.setId(R.id.form_valid_until);
        addField(form, "時刻表の有効期限（任意）", validUntilInput, "期限30日前から更新忘れを表示します");
        EditText notesInput = input("例：3番ホーム／南口のバス停", InputType.TYPE_CLASS_TEXT, true);
        notesInput.setId(R.id.form_notes);
        notesInput.setMinLines(2);
        addField(form, "乗り場メモ（任意）", notesInput, null);

        if (existing != null) {
            routeInput.setText(existing.routeName());
            stopInput.setText(existing.stopName());
            destinationInput.setText(existing.destination());
            walkInput.setText(String.valueOf(existing.walkMinutes()));
            rideInput.setText(String.valueOf(existing.rideMinutes()));
            finalWalkInput.setText(String.valueOf(existing.finalWalkMinutes()));
            officialTimetableUrlInput.setText(existing.officialTimetableUrl());
            weekdayInput.setText(times(existing.weekdayTimes()));
            weekendInput.setText(times(existing.weekendTimes()));
            holidayInput.setText(times(existing.holidayTimes()));
            validUntilInput.setText(existing.validUntil() == null ? "" : existing.validUntil().toString());
            notesInput.setText(existing.notes());
        } else {
            finalWalkInput.setText("0");
        }

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = vertical(SURFACE);
        footer.setPadding(dp(18), dp(12), dp(18), dp(14));
        footer.setBackground(roundRect(SURFACE, 0, LINE, 1));
        footer.setElevation(dp(8));
        Button save = primaryButton(existing == null ? "登録して比較する" : "変更を保存");
        save.setOnClickListener(v -> {
            try {
                if (!requireInput(routeInput, "路線名を入力してください")
                        || !requireInput(stopInput, "駅・停留所を入力してください")
                        || !requireInput(destinationInput, "行き先を入力してください")) return;
                int walk = parseInt(walkInput, "徒歩時間");
                int ride = parseInt(rideInput, "乗車時間");
                int finalWalk = parseInt(finalWalkInput, "到着後の徒歩時間");
                List<LocalTime> weekdays;
                List<LocalTime> weekends;
                List<LocalTime> holidays;
                try {
                    weekdays = ScheduleEngine.parseTimes(weekdayInput.getText().toString());
                } catch (IllegalArgumentException error) {
                    weekdayInput.setError(error.getMessage()); weekdayInput.requestFocus(); return;
                }
                try {
                    weekends = ScheduleEngine.parseTimes(weekendInput.getText().toString());
                } catch (IllegalArgumentException error) {
                    weekendInput.setError(error.getMessage()); weekendInput.requestFocus(); return;
                }
                try {
                    holidays = ScheduleEngine.parseTimes(holidayInput.getText().toString());
                } catch (IllegalArgumentException error) {
                    holidayInput.setError(error.getMessage()); holidayInput.requestFocus(); return;
                }
                if (weekends.isEmpty()) weekends = weekdays;
                String officialTimetableUrl;
                try {
                    officialTimetableUrl = RoutePlan.normalizeOfficialTimetableUrl(
                            officialTimetableUrlInput.getText().toString());
                } catch (IllegalArgumentException error) {
                    officialTimetableUrlInput.setError(error.getMessage());
                    officialTimetableUrlInput.requestFocus();
                    return;
                }
                boolean fetchedFromCurrentUrl = officialTimetableUrl.equals(draftFetchedUrl[0]);
                if (!fetchedFromCurrentUrl) {
                    draftFetchedAt[0] = 0;
                    draftAttemptedAt[0] = 0;
                    draftLastError[0] = "";
                }
                boolean manualTimesChanged = existing != null
                        && (!weekdays.equals(existing.weekdayTimes())
                        || !weekends.equals(existing.weekendTimes())
                        || !holidays.equals(existing.holidayTimes()));
                if (manualTimesChanged && !draftFetchedInThisSession[0]) {
                    draftFetchedAt[0] = 0;
                    draftAttemptedAt[0] = 0;
                    draftLastError[0] = "";
                }
                LocalDate validUntil;
                try {
                    validUntil = validUntilInput.getText().toString().trim().isEmpty()
                            ? null : LocalDate.parse(validUntilInput.getText().toString().trim());
                } catch (RuntimeException error) {
                    validUntilInput.setError("YYYY-MM-DD 形式で入力してください");
                    validUntilInput.requestFocus();
                    return;
                }
                RoutePlan plan = new RoutePlan(
                        existing == null ? null : existing.id(),
                        directionInput.getSelectedItemPosition() == 0
                                ? RoutePlan.Direction.OUTBOUND : RoutePlan.Direction.RETURN,
                        modeInput.getSelectedItemPosition() == 0 ? RoutePlan.Mode.TRAIN : RoutePlan.Mode.BUS,
                        routeInput.getText().toString(), stopInput.getText().toString(),
                        destinationInput.getText().toString(), walk, ride, finalWalk,
                        enabledInput.isChecked(), weekdays, weekends, holidays,
                        notesInput.getText().toString(), validUntil, System.currentTimeMillis(),
                        officialTimetableUrl, draftFetchedAt[0], draftAttemptedAt[0], draftLastError[0]);
                List<RoutePlan> updated = new ArrayList<>(plans);
                if (existing == null) updated.add(plan);
                else updated.set(indexOf(existing.id()), plan);
                if (!saveAndApply(updated)) throw new IllegalStateException("端末に保存できませんでした");
                direction = plan.direction();
                appPreferences.setDirection(direction);
                showDashboard();
                if (plan.hasOfficialTimetableSource()
                        && (plan.officialTimetableFetchedAtEpochMillis() == 0 || !plan.hasCachedTimetable())) {
                    Toast.makeText(this, "保存しました。公式時刻表を取得しています", Toast.LENGTH_SHORT).show();
                    refreshOfficialTimetables(List.of(plan.id()), true);
                } else {
                    Toast.makeText(this, existing == null ? "登録しました" : "変更を保存しました", Toast.LENGTH_SHORT).show();
                }
            } catch (RuntimeException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        footer.addView(save);
        if (existing != null) {
            Button duplicate = smallButton("この登録を複製して編集");
            duplicate.setMinHeight(dp(48));
            duplicate.setOnClickListener(v -> duplicateRoute(existing));
            footer.addView(duplicate);
        }
        root.addView(footer);
        setScreenContent(root);
    }

    private void showTransitServicePicker(
            TransitCatalog.Provider provider,
            Spinner modeInput,
            EditText routeInput,
            EditText stopInput,
            EditText destinationInput,
            TextView catalogStatus
    ) {
        List<TransitCatalog.Service> services = TransitCatalog.servicesFor(provider);
        CharSequence[] labels = new CharSequence[services.size()];
        for (int index = 0; index < services.size(); index++) {
            labels[index] = services.get(index).displayName();
        }
        new AlertDialog.Builder(this)
                .setTitle(provider.displayName() + "の路線・系統を選ぶ")
                .setItems(labels, (dialog, selected) -> showTransitStopPicker(services.get(selected),
                        modeInput, routeInput, stopInput, destinationInput, catalogStatus))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showTransitStopPicker(
            TransitCatalog.Service service,
            Spinner modeInput,
            EditText routeInput,
            EditText stopInput,
            EditText destinationInput,
            TextView catalogStatus
    ) {
        List<String> stops = service.stops();
        new AlertDialog.Builder(this)
                .setTitle(service.displayName() + "\n乗る駅・停留所を選ぶ")
                .setItems(stops.toArray(new CharSequence[0]), (dialog, selected) ->
                        showTransitDestinationPicker(service, stops.get(selected), modeInput,
                                routeInput, stopInput, destinationInput, catalogStatus))
                .setNegativeButton("戻る", (dialog, ignored) -> showTransitServicePicker(service.provider(),
                        modeInput, routeInput, stopInput, destinationInput, catalogStatus))
                .show();
    }

    private void showTransitDestinationPicker(
            TransitCatalog.Service service,
            String stop,
            Spinner modeInput,
            EditText routeInput,
            EditText stopInput,
            EditText destinationInput,
            TextView catalogStatus
    ) {
        List<String> destinations = service.destinations();
        new AlertDialog.Builder(this)
                .setTitle("行き先・方面を選ぶ")
                .setItems(destinations.toArray(new CharSequence[0]), (dialog, selected) -> {
                    modeInput.setSelection(service.mode() == RoutePlan.Mode.TRAIN ? 0 : 1);
                    routeInput.setText(service.displayName());
                    stopInput.setText(stop);
                    destinationInput.setText(destinations.get(selected));
                    String result = "選択済み: " + service.provider().displayName() + " / "
                            + service.displayName() + " / " + stop + " / "
                            + destinations.get(selected);
                    catalogStatus.setText(result);
                    catalogStatus.announceForAccessibility(result);
                })
                .setNegativeButton("戻る", (dialog, ignored) -> showTransitStopPicker(service,
                        modeInput, routeInput, stopInput, destinationInput, catalogStatus))
                .show();
    }

    private void showTimetable(RoutePlan plan) {
        hideKeyboard();
        screen = Screen.TIMETABLE;
        selectedPlan = plan;
        LocalDate date = referenceTime().toLocalDate();
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        int type = appPreferences.holidays().contains(date) && !plan.holidayTimes().isEmpty()
                ? 2 : (weekend ? 1 : 0);
        showTimetableFor(plan, type);
    }

    private void showTimetableFor(RoutePlan plan, int scheduleType) {
        screen = Screen.TIMETABLE;
        timetableScheduleType = scheduleType;
        LinearLayout root = vertical(CANVAS);
        root.addView(pageHeader("駅の時刻表", this::showDashboard, () -> showForm(plan)));

        LinearLayout summary = vertical(SURFACE);
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.setBackground(roundRect(SURFACE, 20, OUTLINE, 1));
        summary.setElevation(dp(1));
        summary.addView(pill(plan.mode() == RoutePlan.Mode.TRAIN ? "電車" : "バス",
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_DARK : AMBER,
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_SOFT : AMBER_SOFT));
        TextView stop = text(plan.stopName(), 26, INK, Typeface.BOLD);
        stop.setPadding(0, dp(10), 0, dp(3));
        summary.addView(stop);
        summary.addView(text(plan.routeName() + "  ·  " + plan.destination() + " 行き", 14, MUTED, Typeface.NORMAL));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(dp(18), dp(8), dp(18), dp(12));
        root.addView(summary, summaryParams);

        View sourceStatus = officialTimetableStatusCard(plan);
        LinearLayout.LayoutParams sourceStatusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceStatusParams.setMargins(dp(18), 0, dp(18), dp(12));
        root.addView(sourceStatus, sourceStatusParams);

        List<ScheduleEngine.Departure> next = ScheduleEngine.nextDepartures(
                plan, referenceTime(), 3, appPreferences.holidays());
        if (!next.isEmpty()) root.addView(nextDeparturesCard(next));

        LinearLayout tabs = horizontal(Gravity.CENTER);
        tabs.setPadding(dp(18), dp(14), dp(18), dp(10));
        Button weekday = segmentButton("平日", scheduleType == 0);
        Button weekendButton = segmentButton("土日", scheduleType == 1);
        Button holidayButton = segmentButton("祝日", scheduleType == 2);
        weekday.setOnClickListener(v -> showTimetableFor(plan, 0));
        weekendButton.setOnClickListener(v -> showTimetableFor(plan, 1));
        holidayButton.setOnClickListener(v -> showTimetableFor(plan, 2));
        tabs.addView(weekday, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        second.setMarginStart(dp(8));
        tabs.addView(weekendButton, second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        third.setMarginStart(dp(8));
        tabs.addView(holidayButton, third);
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        LinearLayout schedule = vertical(Color.TRANSPARENT);
        schedule.setPadding(dp(18), 0, dp(18), dp(24));
        List<LocalTime> times = scheduleType == 0 ? plan.weekdayTimes()
                : scheduleType == 1 ? plan.weekendTimes() : plan.holidayTimes();
        if (times.isEmpty() && scheduleType != 0) times = plan.weekdayTimes();
        schedule.addView(timetableRows(times));
        scroll.addView(schedule);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setScreenContent(root);
    }

    private View officialTimetableStatusCard(RoutePlan plan) {
        LinearLayout card = vertical(INFO_SOFT);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(INFO_SOFT, 18, INFO, 1));

        LinearLayout titleRow = horizontal(Gravity.CENTER_VERTICAL);
        titleRow.addView(pill(plan.hasOfficialTimetableSource() ? "公式サイト" : "手入力",
                INFO, SURFACE));
        TextView title = text(plan.hasOfficialTimetableSource()
                        ? "時刻表の取得と保存" : "時刻表のデータ元",
                15, INK, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(titleRow);

        boolean refreshing = refreshingOfficialTimetableIds.contains(plan.id());
        String status;
        if (!plan.hasOfficialTimetableSource()) {
            status = "現在は手入力の時刻表です。交通事業者の公式ページを設定すると、自動取得できます。";
        } else if (refreshing) {
            status = "公式サイトから取得中です。表示中の時刻表は端末に保存された最終データのままです。";
        } else if (!plan.hasCachedTimetable()) {
            status = "まだ取得済みの時刻表がありません。オンラインで更新すると、この端末に保存されます。";
        } else if (!plan.officialTimetableLastError().isEmpty()) {
            status = "前回の更新に失敗したため、保存済みの最終取得データを表示しています。";
        } else if (plan.officialTimetableFetchedAtEpochMillis() > 0) {
            status = "最終取得: " + officialTimetableTimestamp(plan.officialTimetableFetchedAtEpochMillis())
                    + "\n通信できない場合も、この保存済みデータを使用します。";
        } else {
            status = "保存済みの手入力データを表示しています。公式サイトから更新すると、次回以降も自動で確認します。";
        }
        TextView body = text(status, 13, INK, Typeface.NORMAL);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(0, dp(9), 0, dp(9));
        card.addView(body);

        Button action = plan.hasOfficialTimetableSource()
                ? secondaryButton(refreshing ? "公式サイトから取得中…" : "公式サイトから今すぐ更新")
                : smallButton("公式サイトを設定");
        action.setEnabled(!refreshing);
        action.setContentDescription(plan.hasOfficialTimetableSource()
                ? "公式サイトから時刻表を今すぐ更新する" : "公式時刻表ページを設定する");
        action.setOnClickListener(v -> {
            if (plan.hasOfficialTimetableSource()) {
                refreshOfficialTimetables(List.of(plan.id()), true);
            } else {
                showForm(plan);
            }
        });
        card.addView(action);
        return card;
    }

    private String officialTimetableTimestamp(long epochMillis) {
        LocalDateTime value = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return date(value.toLocalDate()) + " " + time(value);
    }

    private View nextDeparturesCard(List<ScheduleEngine.Departure> departures) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < departures.size(); i++) {
            ScheduleEngine.Departure item = departures.get(i);
            LinearLayout chip = vertical(Color.TRANSPARENT);
            chip.setPadding(dp(16), dp(12), dp(16), dp(12));
            chip.setBackground(roundRect(i == 0 ? BRAND : SURFACE_VARIANT, 17,
                    i == 0 ? 0 : LINE, 1));
            chip.addView(text((i == 0 ? "次の便  " : "") + time(item.at()),
                    17, i == 0 ? Color.WHITE : INK, Typeface.BOLD));
            chip.addView(text(ScheduleEngine.formatMinutes(item.waitMinutes()), 14,
                    i == 0 ? WHITE : MUTED, Typeface.NORMAL));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        return scroll;
    }

    private View timetableRows(List<LocalTime> times) {
        LinearLayout card = vertical(SURFACE);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(roundRect(SURFACE, 20, OUTLINE, 1));
        card.setElevation(dp(1));
        if (times.isEmpty()) {
            TextView empty = centerText("この曜日の時刻は未登録です", 13, MUTED, Typeface.NORMAL);
            empty.setPadding(0, dp(24), 0, dp(24));
            card.addView(empty);
            return card;
        }
        Map<Integer, List<Integer>> byHour = new LinkedHashMap<>();
        for (LocalTime time : times) {
            byHour.computeIfAbsent(time.getHour(), ignored -> new ArrayList<>()).add(time.getMinute());
        }
        int rowIndex = 0;
        for (Map.Entry<Integer, List<Integer>> entry : byHour.entrySet()) {
            LinearLayout row = horizontal(Gravity.TOP);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            TextView hour = text(String.format(Locale.JAPAN, "%02d", entry.getKey()), 19, BRAND, Typeface.BOLD);
            hour.setFontFeatureSettings("tnum");
            row.addView(hour, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
            StringBuilder minutes = new StringBuilder();
            for (int minute : entry.getValue()) {
                if (minutes.length() > 0) minutes.append("    ");
                minutes.append(String.format(Locale.JAPAN, "%02d", minute));
            }
            TextView minuteText = text(minutes.toString(), 17, INK, Typeface.NORMAL);
            minuteText.setFontFeatureSettings("tnum");
            minuteText.setLineSpacing(dp(8), 1f);
            row.addView(minuteText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(row);
            if (rowIndex++ < byHour.size() - 1) {
                View line = new View(this);
                line.setBackgroundColor(LINE);
                card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
        return card;
    }

    private View pageHeader(String title, Runnable back, Runnable action) {
        LinearLayout bar = horizontal(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(7), dp(10), dp(7));
        bar.setBackgroundColor(SURFACE);
        bar.setElevation(dp(3));
        Button backButton = smallButton("←  戻る");
        backButton.setOnClickListener(v -> back.run());
        bar.addView(backButton);
        TextView heading = centerText(title, 19, INK, Typeface.BOLD);
        markAsHeading(heading);
        heading.setMinHeight(dp(56));
        bar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (action != null) {
            Button actionButton = smallButton(screen == Screen.FORM ? "削除" : "編集");
            if (screen == Screen.FORM) actionButton.setTextColor(DANGER);
            actionButton.setOnClickListener(v -> action.run());
            bar.addView(actionButton);
        } else {
            Space placeholder = new Space(this);
            bar.addView(placeholder, new LinearLayout.LayoutParams(dp(70), dp(56)));
        }
        return bar;
    }

    private void confirmDelete(RoutePlan plan) {
        new AlertDialog.Builder(this)
                .setTitle("この登録を削除しますか？")
                .setMessage(plan.routeName() + " / " + plan.stopName() + "\n削除後は元に戻せません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (dialog, which) -> {
                    List<RoutePlan> updated = new ArrayList<>(plans);
                    updated.removeIf(item -> item.id().equals(plan.id()));
                    if (!saveAndApply(updated)) {
                        Toast.makeText(this, "削除内容を保存できませんでした", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show();
                    showDashboard();
                })
                .show();
    }

    private void showSettings() {
        hideKeyboard();
        screen = Screen.SETTINGS;
        LinearLayout root = vertical(CANVAS);
        root.addView(pageHeader("設定", this::showDashboard, null));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical(Color.TRANSPARENT);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));

        LinearLayout backupCard = settingsCard("データの保全", "機種変更や誤操作に備えます");
        Button export = secondaryButton("JSONバックアップを書き出す");
        export.setOnClickListener(v -> startExport());
        backupCard.addView(export);
        backupCard.addView(space(8));
        Button importButton = secondaryButton("JSONバックアップを読み込む");
        importButton.setOnClickListener(v -> startImport());
        backupCard.addView(importButton);
        backupCard.addView(space(8));
        Button restore = smallButton("直前の自動バックアップに戻す");
        restore.setMinHeight(dp(48));
        restore.setOnClickListener(v -> confirmRestoreBackup());
        backupCard.addView(restore);
        content.addView(backupCard);
        content.addView(space(12));

        List<String> officialSourceIds = new ArrayList<>();
        for (RoutePlan plan : plans) {
            if (plan.hasOfficialTimetableSource()) officialSourceIds.add(plan.id());
        }
        LinearLayout officialCard = settingsCard("公式時刻表の更新",
                "設定済みの公式ページを起動時に確認し、最終取得データを端末に保存します");
        if (officialSourceIds.isEmpty()) {
            TextView empty = text("公式時刻表ページが設定された路線はありません。路線の編集から追加できます。",
                    14, MUTED, Typeface.NORMAL);
            empty.setLineSpacing(dp(2), 1f);
            officialCard.addView(empty);
        } else {
            int refreshingCount = 0;
            for (String id : officialSourceIds) {
                if (refreshingOfficialTimetableIds.contains(id)) refreshingCount++;
            }
            TextView status = text(refreshingCount > 0
                            ? refreshingCount + "件を取得中です。保存済みの時刻表はそのまま利用できます。"
                            : officialSourceIds.size() + "件の公式時刻表を設定済みです。通信できない場合も最終取得データを使用します。",
                    14, BRAND_DARK, Typeface.NORMAL);
            status.setLineSpacing(dp(2), 1f);
            status.setPadding(dp(12), dp(10), dp(12), dp(10));
            status.setBackground(roundRect(BRAND_SOFT, 14, 0, 0));
            officialCard.addView(status);
            officialCard.addView(space(10));
            Button refreshAll = secondaryButton(refreshingCount > 0
                    ? "公式時刻表を取得中…" : "公式時刻表をすべて更新");
            refreshAll.setEnabled(refreshingCount == 0);
            refreshAll.setOnClickListener(v -> refreshOfficialTimetables(officialSourceIds, true));
            officialCard.addView(refreshAll);
        }
        content.addView(officialCard);
        content.addView(space(12));

        LinearLayout holidayCard = settingsCard("祝日・臨時休日", "1行に1日、YYYY-MM-DD形式で登録");
        EditText holidaysInput = input("2026-09-21\n2026-09-22", InputType.TYPE_CLASS_DATETIME, true);
        holidaysInput.setText(joinDates(appPreferences.holidays()));
        holidayCard.addView(holidaysInput);
        holidayCard.addView(space(10));
        Button saveHolidays = secondaryButton("祝日を保存");
        saveHolidays.setOnClickListener(v -> {
            try {
                Set<LocalDate> dates = parseDates(holidaysInput.getText().toString());
                appPreferences.setHolidays(dates);
                NextDepartureWidget.updateAll(this);
                Toast.makeText(this, "祝日を保存しました", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException error) {
                holidaysInput.setError(error.getMessage());
            }
        });
        holidayCard.addView(saveHolidays);
        content.addView(holidayCard);
        content.addView(space(12));

        LinearLayout homeCard = settingsCard("ホーム画面と通知", "アプリを開かず次発を確認できます");
        Button pinWidget = secondaryButton("次発ウィジェットを追加");
        pinWidget.setOnClickListener(v -> requestPinWidget());
        homeCard.addView(pinWidget);
        homeCard.addView(space(8));
        Button notificationSettings = smallButton("通知の設定を開く");
        notificationSettings.setMinHeight(dp(48));
        notificationSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });
        homeCard.addView(notificationSettings);
        Button cancelReminders = smallButton("予約した出発通知をすべて取り消す");
        cancelReminders.setMinHeight(dp(48));
        cancelReminders.setOnClickListener(v -> cancelAllReminders());
        homeCard.addView(cancelReminders);
        content.addView(homeCard);
        content.addView(space(12));

        LinearLayout routesCard = settingsCard("登録管理", "無効にした候補もここから編集できます");
        if (plans.isEmpty()) {
            TextView empty = centerText("登録はありません", 14, MUTED, Typeface.NORMAL);
            empty.setPadding(0, dp(16), 0, dp(16));
            routesCard.addView(empty);
        } else {
            for (RoutePlan plan : plans) {
                LinearLayout item = horizontal(Gravity.CENTER_VERTICAL);
                if (isConstrainedContent()) item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(14), dp(11), dp(8), dp(11));
                item.setBackground(roundRect(SURFACE_VARIANT, 15, OUTLINE, 1));
                LinearLayout labels = vertical(Color.TRANSPARENT);
                LinearLayout status = horizontal(Gravity.CENTER_VERTICAL);
                status.addView(pill(plan.enabled() ? "使用中" : "停止中",
                        plan.enabled() ? BRAND_DARK : MUTED,
                        plan.enabled() ? BRAND_SOFT : SEGMENT));
                TextView routeName = text(plan.routeName(), 14, plan.enabled() ? INK : MUTED, Typeface.BOLD);
                routeName.setPadding(dp(8), 0, 0, 0);
                status.addView(routeName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                labels.addView(status);
                labels.addView(text(plan.stopName() + " → " + plan.destination(), 14, MUTED, Typeface.NORMAL));
                item.addView(labels, isConstrainedContent()
                        ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Button edit = smallButton("編集");
                edit.setOnClickListener(v -> showForm(plan));
                if (isConstrainedContent()) edit.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                item.addView(edit);
                routesCard.addView(item);
                routesCard.addView(space(8));
            }
        }
        content.addView(routesCard);
        content.addView(space(12));

        LinearLayout privacyCard = settingsCard("プライバシー", "登録内容はこの端末の中だけに保存されます");
        TextView privacy = text("通信・アカウント・位置情報・分析SDKは使用しません。", 14, BRAND_DARK, Typeface.NORMAL);
        privacy.setPadding(dp(14), dp(12), dp(14), dp(12));
        privacy.setBackground(roundRect(BRAND_SOFT, 14, 0, 0));
        privacyCard.addView(privacy);
        content.addView(privacyCard);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setScreenContent(root);
    }

    private View sectionTitle(String title, String description) {
        LinearLayout block = vertical(Color.TRANSPARENT);
        TextView heading = text(title, 17, INK, Typeface.BOLD);
        markAsHeading(heading);
        block.addView(heading);
        TextView body = text(description, 14, MUTED, Typeface.NORMAL);
        body.setPadding(0, dp(4), 0, dp(10));
        body.setLineSpacing(dp(2), 1f);
        block.addView(body);
        return block;
    }

    private LinearLayout settingsCard(String title, String description) {
        LinearLayout card = vertical(SURFACE);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(SURFACE, 20, OUTLINE, 1));
        card.setElevation(dp(1));
        card.addView(sectionTitle(title, description));
        return card;
    }

    private View formSectionTitle(String step, String title, String description) {
        LinearLayout block = horizontal(Gravity.CENTER_VERTICAL);
        TextView number = centerText(step, 14, WHITE, Typeface.BOLD);
        number.setBackground(roundRect(BRAND, 15, 0, 0));
        block.addView(number, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout labels = vertical(Color.TRANSPARENT);
        labels.setPadding(dp(11), 0, 0, 0);
        TextView heading = text(title, 18, INK, Typeface.BOLD);
        markAsHeading(heading);
        labels.addView(heading);
        labels.addView(text(description, 13, MUTED, Typeface.NORMAL));
        block.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return block;
    }

    private Button secondaryButton(String label) {
        Button button = primaryButton(label);
        button.setTextColor(BRAND_DARK);
        button.setBackground(interactiveBackground(SURFACE, 14, BRAND_DARK, 1));
        button.setElevation(0);
        return button;
    }

    private void choosePreviewDateTime() {
        LocalDateTime initial = LocalDateTime.now();
        new DatePickerDialog(this, (picker, year, month, day) ->
                new TimePickerDialog(this, (timePicker, hour, minute) -> {
                    previewTime = LocalDateTime.of(year, month + 1, day, hour, minute);
                    showDashboard();
                }, initial.getHour(), initial.getMinute(), true).show(),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private LocalDateTime referenceTime() {
        return previewTime == null ? LocalDateTime.now() : previewTime;
    }

    private void requestReminder(RoutePlan plan, ScheduleEngine.Departure departure) {
        pendingReminderPlan = plan;
        pendingReminderDeparture = departure;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            scheduleReminder(plan, departure);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_NOTIFICATIONS && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED && pendingReminderPlan != null) {
            scheduleReminder(pendingReminderPlan, pendingReminderDeparture);
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            Toast.makeText(this, "通知が許可されていません", Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleReminder(RoutePlan plan, ScheduleEngine.Departure departure) {
        LocalDateTime leaveAt = departure.at().minusMinutes(plan.walkMinutes());
        long triggerAt = leaveAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        triggerAt = Math.max(triggerAt, System.currentTimeMillis() + 1000);
        Intent intent = new Intent(this, DepartureAlarmReceiver.class)
                .putExtra("title", plan.routeName() + "へ出発する時間です")
                .putExtra("detail", plan.stopName() + " " + time(departure.at()) + "発")
                .putExtra("notificationId", Math.abs(plan.id().hashCode()));
        PendingIntent pending = PendingIntent.getBroadcast(this, Math.abs(plan.id().hashCode()), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        Toast.makeText(this, time(leaveAt) + "ごろ通知します", Toast.LENGTH_LONG).show();
        pendingReminderPlan = null;
        pendingReminderDeparture = null;
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "sugunoru-backup-" + LocalDate.now() + ".json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == REQUEST_EXPORT) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new IllegalStateException("保存先を開けません");
                    output.write(repository.exportJson(plans).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "バックアップを書き出しました", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_IMPORT) {
                byte[] bytes;
                try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                    if (input == null) throw new IllegalStateException("ファイルを開けません");
                    bytes = readLimited(input, 2_000_001);
                }
                if (bytes.length > 2_000_000) throw new IllegalArgumentException("ファイルが大きすぎます（上限2MB）");
                List<RoutePlan> imported = repository.importJson(new String(bytes, StandardCharsets.UTF_8));
                chooseImportMode(imported);
            }
        } catch (Exception error) {
            Toast.makeText(this, "処理できません: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseImportMode(List<RoutePlan> imported) {
        new AlertDialog.Builder(this)
                .setTitle(imported.size() + "件の登録を読み込みます")
                .setItems(new String[]{"現在の登録に追加", "現在の登録を置き換え"}, (dialog, which) -> {
                    List<RoutePlan> updated = which == 1 ? new ArrayList<>() : new ArrayList<>(plans);
                    for (RoutePlan item : imported) {
                        updated.removeIf(current -> current.id().equals(item.id()));
                        updated.add(item);
                    }
                    if (saveAndApply(updated)) {
                        Toast.makeText(this, "読み込みました", Toast.LENGTH_SHORT).show();
                        showSettings();
                    }
                }).show();
    }

    private byte[] readLimited(InputStream input, int limit) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (count < 0) break;
            output.write(buffer, 0, count);
            total += count;
        }
        return output.toByteArray();
    }

    private void confirmRestoreBackup() {
        new AlertDialog.Builder(this)
                .setTitle("直前の状態に戻しますか？")
                .setMessage("現在の登録は、前回保存する直前の状態に置き換わります。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("戻す", (dialog, which) -> {
                    if (repository.restoreAutomaticBackup()) {
                        applyPlans(repository.load());
                        showSettings();
                    } else Toast.makeText(this, "戻せるデータがありません", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void requestPinWidget() {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, NextDepartureWidget.class), null, null);
        } else {
            Toast.makeText(this, "ホーム画面を長押しし、ウィジェットから追加してください", Toast.LENGTH_LONG).show();
        }
    }

    private void duplicateRoute(RoutePlan source) {
        RoutePlan copy = source.duplicate(source.routeName() + " コピー");
        List<RoutePlan> updated = new ArrayList<>(plans);
        updated.add(copy);
        if (saveAndApply(updated)) {
            Toast.makeText(this, "複製しました", Toast.LENGTH_SHORT).show();
            showForm(copy);
        }
    }

    private void installSampleRoutes() {
        List<LocalTime> times = new ArrayList<>();
        for (int hour = 5; hour <= 23; hour++) {
            times.add(LocalTime.of(hour, 5)); times.add(LocalTime.of(hour, 25)); times.add(LocalTime.of(hour, 45));
        }
        RoutePlan sample = new RoutePlan(null, direction, RoutePlan.Mode.TRAIN, "サンプル線 快速",
                "サンプル駅", "目的地方面", 7, 24, 4, true, times, times, times,
                "これはサンプルです。編集してお使いください", LocalDate.now().plusMonths(3), System.currentTimeMillis());
        List<RoutePlan> updated = new ArrayList<>(plans); updated.add(sample);
        if (saveAndApply(updated)) {
            showDashboard();
        }
    }

    private boolean saveAndApply(List<RoutePlan> updated) {
        if (!repository.save(updated)) return false;
        applyPlans(updated);
        return true;
    }

    private void applyPlans(List<RoutePlan> updated) {
        plans.clear();
        plans.addAll(updated);
        NextDepartureWidget.updateAll(this);
    }

    private void fetchOfficialTimetableIntoForm(
            String sourceUrl, EditText weekdayInput, EditText weekendInput, EditText holidayInput,
            Button action, Runnable onSuccess
    ) {
        if (!hasUsableNetwork()) {
            Toast.makeText(this,
                    "通信できないため取得できません。入力済みの時刻表は変更していません。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        action.setEnabled(false);
        action.setText("公式サイトから取得中…");
        officialTimetableExecutor.execute(() -> {
            try {
                OfficialTimetableFetcher.FetchResult result = new OfficialTimetableFetcher().fetch(sourceUrl);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    OfficialTimetableParser.Timetable timetable = result.timetable();
                    weekdayInput.setText(times(timetable.weekdayTimes()));
                    weekendInput.setText(times(timetable.weekendTimes()));
                    holidayInput.setText(times(timetable.holidayTimes()));
                    onSuccess.run();
                    action.setEnabled(true);
                    action.setText("公式サイトから時刻表を再取得");
                    Toast.makeText(this, "公式サイトから時刻表を取得しました。内容を確認して保存してください。",
                            Toast.LENGTH_LONG).show();
                });
            } catch (OfficialTimetableFetcher.FetchException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    action.setEnabled(true);
                    action.setText("公式サイトから時刻表を取得");
                    Toast.makeText(this, error.getMessage() + "。入力済みの時刻表は変更していません。",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshStaleOfficialTimetables() {
        long now = System.currentTimeMillis();
        List<String> staleIds = new ArrayList<>();
        for (RoutePlan plan : plans) {
            if (plan.hasOfficialTimetableSource() && officialTimetableRefreshIsDue(plan, now)) {
                staleIds.add(plan.id());
            }
        }
        refreshOfficialTimetables(staleIds, false);
    }

    private boolean officialTimetableRefreshIsDue(RoutePlan plan, long now) {
        long lastAttempt = Math.max(plan.officialTimetableFetchedAtEpochMillis(),
                plan.officialTimetableAttemptedAtEpochMillis());
        return lastAttempt == 0 || now - lastAttempt >= OFFICIAL_TIMETABLE_REFRESH_INTERVAL_MILLIS;
    }

    private void refreshOfficialTimetables(List<String> requestedIds, boolean userRequested) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            if (userRequested) {
                Toast.makeText(this, "公式時刻表ページを設定した路線がありません", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!hasUsableNetwork()) {
            if (userRequested) {
                Toast.makeText(this,
                        "通信できないため更新できません。保存済みの時刻表はそのまま使用します。",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        List<OfficialRefreshRequest> requests = new ArrayList<>();
        for (String id : requestedIds) {
            RoutePlan plan = findPlan(id);
            if (plan == null || !plan.hasOfficialTimetableSource()
                    || refreshingOfficialTimetableIds.contains(plan.id())) continue;
            refreshingOfficialTimetableIds.add(plan.id());
            requests.add(new OfficialRefreshRequest(plan.id(), plan.officialTimetableUrl(),
                    plan.updatedAtEpochMillis()));
        }
        if (requests.isEmpty()) {
            if (userRequested) {
                Toast.makeText(this, "公式時刻表を取得中です", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        refreshVisibleOfficialTimetableState();

        officialTimetableExecutor.execute(() -> {
            OfficialTimetableFetcher fetcher = new OfficialTimetableFetcher();
            List<OfficialRefreshOutcome> outcomes = new ArrayList<>();
            for (OfficialRefreshRequest request : requests) {
                try {
                    OfficialTimetableFetcher.FetchResult result = fetcher.fetch(request.sourceUrl());
                    outcomes.add(new OfficialRefreshOutcome(request, result.timetable(), ""));
                } catch (OfficialTimetableFetcher.FetchException error) {
                    outcomes.add(new OfficialRefreshOutcome(request, null, error.getMessage()));
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                applyOfficialTimetableRefreshOutcomes(outcomes, userRequested);
            });
        });
    }

    private void applyOfficialTimetableRefreshOutcomes(
            List<OfficialRefreshOutcome> outcomes, boolean userRequested
    ) {
        List<RoutePlan> updated = new ArrayList<>(plans);
        int successCount = 0;
        int failureCount = 0;
        boolean changed = false;
        long attemptedAt = System.currentTimeMillis();
        for (OfficialRefreshOutcome outcome : outcomes) {
            refreshingOfficialTimetableIds.remove(outcome.request().planId());
            int index = indexOfOrMinusOne(updated, outcome.request().planId());
            if (index < 0) continue;
            RoutePlan current = updated.get(index);
            if (!current.officialTimetableUrl().equals(outcome.request().sourceUrl())
                    || current.updatedAtEpochMillis() != outcome.request().updatedAtEpochMillis()) {
                continue;
            }
            if (outcome.timetable() != null) {
                OfficialTimetableParser.Timetable timetable = outcome.timetable();
                updated.set(index, current.withFetchedOfficialTimetable(timetable.weekdayTimes(),
                        timetable.weekendTimes(), timetable.holidayTimes(), attemptedAt));
                successCount++;
            } else {
                updated.set(index, current.withOfficialTimetableFetchFailure(attemptedAt, outcome.error()));
                failureCount++;
            }
            changed = true;
        }

        boolean saved = !changed || saveAndApply(updated);
        if (!saved) {
            Toast.makeText(this, "取得結果を端末に保存できませんでした", Toast.LENGTH_LONG).show();
            successCount = 0;
            failureCount = outcomes.size();
        }
        refreshVisibleOfficialTimetableState();
        if (userRequested) {
            if (successCount > 0 && failureCount == 0) {
                Toast.makeText(this, "公式時刻表を" + successCount + "件更新しました", Toast.LENGTH_SHORT).show();
            } else if (successCount > 0) {
                Toast.makeText(this, "" + successCount + "件更新しました。失敗した路線は保存済みの時刻表を使用します。",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "更新できませんでした。保存済みの時刻表は変更していません。",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshVisibleOfficialTimetableState() {
        if (screen == Screen.TIMETABLE && selectedPlan != null) {
            RoutePlan current = findPlan(selectedPlan.id());
            if (current != null) {
                selectedPlan = current;
                showTimetableFor(current, timetableScheduleType);
            }
        } else if (screen == Screen.SETTINGS) {
            showSettings();
        } else if (screen == Screen.DASHBOARD) {
            showDashboard();
        }
    }

    private boolean hasUsableNetwork() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) return false;
        Network active = manager.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private RoutePlan findPlan(String id) {
        for (RoutePlan plan : plans) if (plan.id().equals(id)) return plan;
        return null;
    }

    private int indexOfOrMinusOne(List<RoutePlan> values, String id) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id().equals(id)) return index;
        }
        return -1;
    }

    private record OfficialRefreshRequest(String planId, String sourceUrl, long updatedAtEpochMillis) {}

    private record OfficialRefreshOutcome(
            OfficialRefreshRequest request,
            OfficialTimetableParser.Timetable timetable,
            String error
    ) {}

    private String joinDates(Set<LocalDate> dates) {
        List<LocalDate> sorted = new ArrayList<>(dates);
        java.util.Collections.sort(sorted);
        StringBuilder result = new StringBuilder();
        for (LocalDate date : sorted) {
            if (result.length() > 0) result.append('\n');
            result.append(date);
        }
        return result.toString();
    }

    private Set<LocalDate> parseDates(String raw) {
        Set<LocalDate> result = new java.util.HashSet<>();
        for (String token : raw.replace(',', ' ').replace('、', ' ').split("[\\s]+")) {
            if (token.isBlank()) continue;
            try { result.add(LocalDate.parse(token)); }
            catch (RuntimeException error) { throw new IllegalArgumentException("「" + token + "」を YYYY-MM-DD で入力してください"); }
        }
        return result;
    }

    private boolean requireInput(EditText input, String message) {
        if (!input.getText().toString().trim().isEmpty()) return true;
        input.setError(message);
        input.requestFocus();
        return false;
    }

    private void addFrequencyBuilder(LinearLayout parent, EditText target, String label) {
        Button builder = smallButton("＋ " + label + "の等間隔ダイヤを作る");
        builder.setMinHeight(dp(48));
        builder.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        builder.setOnClickListener(v -> showFrequencyBuilder(target));
        parent.addView(builder);
        parent.addView(space(8));
    }

    private void showFrequencyBuilder(EditText target) {
        LinearLayout fields = vertical(Color.TRANSPARENT);
        fields.setPadding(dp(20), dp(4), dp(20), 0);
        EditText start = input("始発  例 06:00", InputType.TYPE_CLASS_DATETIME, false);
        EditText end = input("最終  例 23:30", InputType.TYPE_CLASS_DATETIME, false);
        EditText interval = input("間隔（分）  例 15", InputType.TYPE_CLASS_NUMBER, false);
        fields.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.addView(space(8));
        fields.addView(end, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.addView(space(8));
        fields.addView(interval, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("等間隔の便を作成")
                .setView(fields)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("時刻欄に追加", (dialog, which) -> {
                    try {
                        List<LocalTime> starts = ScheduleEngine.parseTimes(start.getText().toString());
                        List<LocalTime> ends = ScheduleEngine.parseTimes(end.getText().toString());
                        int minutes = Integer.parseInt(interval.getText().toString().trim());
                        if (starts.size() != 1 || ends.size() != 1 || minutes < 1 || minutes > 180
                                || ends.get(0).isBefore(starts.get(0))) {
                            throw new IllegalArgumentException("始発・最終・1〜180分の間隔を確認してください");
                        }
                        List<LocalTime> generated = new ArrayList<>();
                        for (LocalTime time = starts.get(0); !time.isAfter(ends.get(0)); time = time.plusMinutes(minutes)) {
                            generated.add(time);
                            if (generated.size() > 300) throw new IllegalArgumentException("便数が多すぎます");
                        }
                        List<LocalTime> combined = new ArrayList<>(ScheduleEngine.parseTimes(target.getText().toString()));
                        combined.addAll(generated);
                        combined = new ArrayList<>(new java.util.TreeSet<>(combined));
                        target.setText(times(combined));
                    } catch (RuntimeException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void cancelAllReminders() {
        AlarmManager alarms = getSystemService(AlarmManager.class);
        for (RoutePlan plan : plans) {
            Intent intent = new Intent(this, DepartureAlarmReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(this, Math.abs(plan.id().hashCode()), intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) {
                alarms.cancel(pending);
                pending.cancel();
            }
        }
        getSystemService(android.app.NotificationManager.class).cancelAll();
        Toast.makeText(this, "出発通知を取り消しました", Toast.LENGTH_SHORT).show();
    }

    private int indexOf(String id) {
        for (int i = 0; i < plans.size(); i++) if (plans.get(i).id().equals(id)) return i;
        throw new IllegalStateException("編集対象が見つかりません");
    }

    private int parseInt(EditText input, String label) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            input.setError(label + "を入力してください");
            input.requestFocus();
            throw new IllegalArgumentException(label + "を入力してください");
        }
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException error) {
            input.setError(label + "は数字で入力してください");
            input.requestFocus();
            throw new IllegalArgumentException(label + "は数字で入力してください");
        }
    }

    private void setScreenContent(View content) {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(CANVAS);
        int width = getResources().getConfiguration().screenWidthDp >= 600
                ? dp(720) : ViewGroup.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        outer.addView(content, params);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            String paneTitle = screen == Screen.DASHBOARD ? "次発比較"
                    : screen == Screen.FORM ? "路線の登録と編集"
                    : screen == Screen.TIMETABLE ? "時刻表" : "設定";
            content.setAccessibilityPaneTitle(paneTitle);
        }
        setContentView(outer);
        if (android.os.Build.VERSION.SDK_INT >= 30) SystemBarInsetsApplier.install(outer);
    }

}
