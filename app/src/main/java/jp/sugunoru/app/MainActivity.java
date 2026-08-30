package jp.sugunoru.app;

import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;

import jp.sugunoru.app.data.RouteRepository;
import jp.sugunoru.app.data.AppPreferences;
import jp.sugunoru.app.model.RoutePlan;
import jp.sugunoru.app.model.ScheduleEngine;
import jp.sugunoru.app.notification.DepartureAlarmReceiver;
import jp.sugunoru.app.widget.NextDepartureWidget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

import static jp.sugunoru.app.ui.UiPalette.*;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends android.app.Activity {
    private enum Screen { DASHBOARD, FORM, TIMETABLE, SETTINGS }
    private static final int REQUEST_EXPORT = 301;
    private static final int REQUEST_IMPORT = 302;
    private static final int REQUEST_NOTIFICATIONS = 303;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final List<RoutePlan> plans = new ArrayList<>();
    private final DateTimeFormatter clockFormat = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN);
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN);
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
        getWindow().setStatusBarColor(CANVAS);
        getWindow().setNavigationBarColor(SURFACE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
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
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLaunchDirection(intent);
        showDashboard();
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
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
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
        root.setPadding(dp(20), dp(14), dp(20), dp(18));

        root.addView(dashboardHeader());
        root.addView(space(18));
        root.addView(nowCard());
        root.addView(space(16));
        root.addView(directionSwitch());
        root.addView(space(14));

        TextView heading = text("到着が早い順", 16, INK, Typeface.BOLD);
        markAsHeading(heading);
        root.addView(heading);
        TextView help = text("乗るまで・乗車・到着後の時間を含めて比較しています", 14, MUTED, Typeface.NORMAL);
        help.setPadding(0, dp(3), 0, dp(10));
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

        Button add = primaryButton("＋  この使い方に路線を追加");
        add.setOnClickListener(v -> showForm(null));
        root.addView(add);
        setScreenContent(root);
    }

    private View dashboardHeader() {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        TextView mark = text("す", 18, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundRect(BRAND, 14, 0, 0));
        row.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout names = vertical(Color.TRANSPARENT);
        names.setPadding(dp(11), 0, 0, 0);
        names.addView(text("すぐのる", 20, INK, Typeface.BOLD));
        names.addView(text("いつもの移動を、迷わず。", 14, MUTED, Typeface.NORMAL));
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button settingsButton = smallButton("設定");
        settingsButton.setContentDescription("設定とバックアップを開く");
        settingsButton.setOnClickListener(v -> showSettings());
        row.addView(settingsButton);
        return row;
    }

    private View nowCard() {
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setPadding(dp(20), dp(16), dp(20), dp(17));
        card.setBackground(roundGradient(BRAND_DARK, BRAND, 22));

        LinearLayout top = horizontal(Gravity.BOTTOM);
        LocalDateTime shown = referenceTime();
        liveClock = text(shown.format(clockFormat), 42, Color.WHITE, Typeface.BOLD);
        liveClock.setFontFeatureSettings("tnum");
        liveClock.setLetterSpacing(-0.03f);
        top.addView(liveClock);
        TextView now = text(previewTime == null ? "  現在" : "  試算中", 14, WHITE, Typeface.BOLD);
        now.setPadding(0, 0, 0, dp(7));
        top.addView(now);
        card.addView(top);

        liveDate = text(formatDate(shown.toLocalDate()), 14, WHITE, Typeface.NORMAL);
        liveDate.setPadding(0, dp(2), 0, 0);
        card.addView(liveDate);
        TextView simulate = text(previewTime == null ? "別の日時で調べる  ›" : "現在時刻に戻す  ×",
                14, WHITE, Typeface.BOLD);
        simulate.setPadding(0, dp(12), 0, 0);
        simulate.setMinHeight(dp(48));
        simulate.setGravity(Gravity.CENTER_VERTICAL);
        simulate.setFocusable(true);
        simulate.setBackground(interactiveBackground(Color.TRANSPARENT, 12, 0, 0));
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
        shell.setBackground(roundRect(SEGMENT, 16, CONTROL, 1));
        Button outbound = segmentButton("出かける", direction == RoutePlan.Direction.OUTBOUND);
        Button returning = segmentButton("帰る", direction == RoutePlan.Direction.RETURN);
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
        for (RoutePlan plan : plans) if (plan.direction() == direction) hasThisDirection = true;
        final boolean hasInactiveRoutes = hasThisDirection;
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(30), dp(24), dp(30));
        card.setBackground(roundRect(SURFACE, 20, CONTROL, 1));
        TextView icon = text(direction == RoutePlan.Direction.OUTBOUND ? "出発" : "帰宅", 14, BRAND_DARK, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(BRAND_SOFT, 18, 0, 0));
        icon.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.addView(icon);
        card.addView(space(14));
        card.addView(centerText(hasThisDirection ? "比較対象がありません" : "まだ路線がありません",
                18, INK, Typeface.BOLD));
        TextView body = centerText(hasThisDirection
                        ? "この場面の登録はすべて無効です。\n設定から比較対象に戻せます。"
                        : "よく使う駅・停留所と時刻表を登録すると\n次に乗れる便をすぐ比較できます。",
                13, MUTED, Typeface.NORMAL);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);
        Button sample = smallButton(hasThisDirection ? "設定で登録を管理" : "サンプルで試す");
        sample.setMinHeight(dp(48));
        sample.setOnClickListener(v -> { if (hasInactiveRoutes) showSettings(); else installSampleRoutes(); });
        card.addView(sample);
        return card;
    }

    private View routeCard(ScheduleEngine.RouteOption option, boolean fastest, LocalDateTime reference) {
        RoutePlan plan = option.plan();
        List<ScheduleEngine.Departure> next = ScheduleEngine.nextDepartures(
                plan, reference, 3, appPreferences.holidays());
        LinearLayout card = vertical(Color.TRANSPARENT);
        card.setPadding(dp(16), dp(14), dp(16), dp(13));
        card.setBackground(roundRect(SURFACE, 20, fastest ? BRAND : CONTROL, fastest ? 2 : 1));
        card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x1F006B4F), null, null));
        card.setOnClickListener(v -> showTimetable(plan));
        card.setFocusable(true);
        card.setContentDescription(plan.routeName() + "、" + plan.stopName() + "から"
                + plan.destination() + "、" + option.departure().at().format(timeFormat) + "発、"
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

        TextView title = text(plan.stopName() + " → " + plan.destination(), 17, INK, Typeface.BOLD);
        title.setPadding(0, dp(10), 0, dp(8));
        card.addView(title);

        LinearLayout timing = vertical(Color.TRANSPARENT);
        LinearLayout departureRow = horizontal(Gravity.BOTTOM);
        TextView departure = text(option.departure().at().format(timeFormat), 31, INK, Typeface.BOLD);
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

        String arrival = option.estimatedArrival().format(timeFormat) + " 着見込み";
        String detail = "乗るまで " + plan.walkMinutes() + "分  ・  乗車 " + plan.rideMinutes()
                + "分" + (plan.finalWalkMinutes() > 0 ? "  ・  到着後 " + plan.finalWalkMinutes() + "分" : "")
                + "  ・  " + arrival;
        TextView details = text(detail, 14, MUTED, Typeface.NORMAL);
        details.setPadding(0, dp(6), 0, dp(11));
        card.addView(details);
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
            following.append(next.get(i).at().format(timeFormat));
        }
        if (next.size() <= 1) following.append("登録便なし");
        footer.addView(text(following.toString(), 14, MUTED, Typeface.NORMAL));
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
        form.setPadding(dp(20), dp(10), dp(20), dp(26));

        TextView intro = text("乗る場所と時刻表を登録します", 14, MUTED, Typeface.NORMAL);
        intro.setPadding(0, 0, 0, dp(16));
        form.addView(intro);

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
        addField(form, "路線名", routeInput, null);
        addField(form, "乗る駅・停留所", stopInput, null);
        addField(form, "行き先", destinationInput, null);

        LinearLayout durations = horizontal(Gravity.TOP);
        LinearLayout walkBox = vertical(Color.TRANSPARENT);
        LinearLayout rideBox = vertical(Color.TRANSPARENT);
        addField(walkBox, "ここまで徒歩（分）", walkInput, null);
        addField(rideBox, "乗車時間（分）", rideInput, null);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        half.setMarginEnd(dp(6));
        durations.addView(walkBox, half);
        LinearLayout.LayoutParams otherHalf = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        otherHalf.setMarginStart(dp(6));
        durations.addView(rideBox, otherHalf);
        form.addView(durations);
        addField(form, "降りてから目的地まで（分）", finalWalkInput,
                "最終的な到着時刻の比較に含めます。なければ 0");

        EditText weekdayInput = input("07:05  07:18  07:34\n08:02  08:20", InputType.TYPE_CLASS_TEXT, true);
        weekdayInput.setId(R.id.form_weekday);
        EditText weekendInput = input("08:10  08:40  09:10", InputType.TYPE_CLASS_TEXT, true);
        weekendInput.setId(R.id.form_weekend);
        EditText holidayInput = input("08:10  08:40  09:10", InputType.TYPE_CLASS_TEXT, true);
        holidayInput.setId(R.id.form_holiday);
        addField(form, "平日の時刻表", weekdayInput, "空白・改行・カンマ区切り。0705 の形式でも入力できます");
        addFrequencyBuilder(form, weekdayInput, "平日");
        addField(form, "土日の時刻表", weekendInput, "空欄なら平日の時刻を使います");
        addFrequencyBuilder(form, weekendInput, "土日");
        addField(form, "祝日の時刻表", holidayInput, "設定画面で登録した祝日に使います。空欄なら曜日どおり");
        addFrequencyBuilder(form, holidayInput, "祝日");

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
            weekdayInput.setText(joinTimes(existing.weekdayTimes()));
            weekendInput.setText(joinTimes(existing.weekendTimes()));
            holidayInput.setText(joinTimes(existing.holidayTimes()));
            validUntilInput.setText(existing.validUntil() == null ? "" : existing.validUntil().toString());
            notesInput.setText(existing.notes());
        } else {
            finalWalkInput.setText("0");
        }

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = vertical(SURFACE);
        footer.setPadding(dp(20), dp(12), dp(20), dp(14));
        footer.setBackground(roundRect(SURFACE, 0, LINE, 1));
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
                        notesInput.getText().toString(), validUntil, System.currentTimeMillis());
                List<RoutePlan> updated = new ArrayList<>(plans);
                if (existing == null) updated.add(plan);
                else updated.set(indexOf(existing.id()), plan);
                if (!repository.save(updated)) throw new IllegalStateException("端末に保存できませんでした");
                plans.clear();
                plans.addAll(updated);
                direction = plan.direction();
                appPreferences.setDirection(direction);
                NextDepartureWidget.updateAll(this);
                Toast.makeText(this, existing == null ? "登録しました" : "変更を保存しました", Toast.LENGTH_SHORT).show();
                showDashboard();
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

        LinearLayout summary = vertical(Color.TRANSPARENT);
        summary.setPadding(dp(20), dp(8), dp(20), dp(14));
        summary.addView(pill(plan.mode() == RoutePlan.Mode.TRAIN ? "電車" : "バス",
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_DARK : AMBER,
                plan.mode() == RoutePlan.Mode.TRAIN ? BRAND_SOFT : AMBER_SOFT));
        TextView stop = text(plan.stopName(), 26, INK, Typeface.BOLD);
        stop.setPadding(0, dp(10), 0, dp(3));
        summary.addView(stop);
        summary.addView(text(plan.routeName() + "  ·  " + plan.destination() + " 行き", 14, MUTED, Typeface.NORMAL));
        root.addView(summary);

        List<ScheduleEngine.Departure> next = ScheduleEngine.nextDepartures(
                plan, referenceTime(), 3, appPreferences.holidays());
        if (!next.isEmpty()) root.addView(nextDeparturesCard(next));

        LinearLayout tabs = horizontal(Gravity.CENTER);
        tabs.setPadding(dp(20), dp(14), dp(20), dp(10));
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
        schedule.setPadding(dp(20), 0, dp(20), dp(24));
        List<LocalTime> times = scheduleType == 0 ? plan.weekdayTimes()
                : scheduleType == 1 ? plan.weekendTimes() : plan.holidayTimes();
        if (times.isEmpty() && scheduleType != 0) times = plan.weekdayTimes();
        schedule.addView(timetableRows(times));
        scroll.addView(schedule);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setScreenContent(root);
    }

    private View nextDeparturesCard(List<ScheduleEngine.Departure> departures) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < departures.size(); i++) {
            ScheduleEngine.Departure item = departures.get(i);
            LinearLayout chip = vertical(Color.TRANSPARENT);
            chip.setPadding(dp(16), dp(11), dp(16), dp(11));
            chip.setBackground(roundRect(i == 0 ? BRAND : SURFACE, 16, i == 0 ? 0 : CONTROL, 1));
            chip.addView(text((i == 0 ? "次の便  " : "") + item.at().format(timeFormat),
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
        card.setBackground(roundRect(SURFACE, 20, CONTROL, 1));
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
        bar.setPadding(dp(12), dp(10), dp(12), dp(8));
        Button backButton = smallButton("‹ 戻る");
        backButton.setOnClickListener(v -> back.run());
        bar.addView(backButton);
        TextView heading = centerText(title, 18, INK, Typeface.BOLD);
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
                    if (!repository.save(updated)) {
                        Toast.makeText(this, "削除内容を保存できませんでした", Toast.LENGTH_LONG).show();
                        return;
                    }
                    plans.clear();
                    plans.addAll(updated);
                    NextDepartureWidget.updateAll(this);
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
        content.setPadding(dp(20), dp(8), dp(20), dp(28));

        content.addView(sectionTitle("データの保全", "機種変更や誤操作に備えます"));
        Button export = secondaryButton("JSONバックアップを書き出す");
        export.setOnClickListener(v -> startExport());
        content.addView(export);
        content.addView(space(8));
        Button importButton = secondaryButton("JSONバックアップを読み込む");
        importButton.setOnClickListener(v -> startImport());
        content.addView(importButton);
        content.addView(space(8));
        Button restore = smallButton("直前の自動バックアップに戻す");
        restore.setMinHeight(dp(48));
        restore.setOnClickListener(v -> confirmRestoreBackup());
        content.addView(restore);
        content.addView(space(22));

        content.addView(sectionTitle("祝日・臨時休日", "1行に1日、YYYY-MM-DD形式で登録"));
        EditText holidaysInput = input("2026-09-21\n2026-09-22", InputType.TYPE_CLASS_DATETIME, true);
        holidaysInput.setText(joinDates(appPreferences.holidays()));
        content.addView(holidaysInput);
        content.addView(space(8));
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
        content.addView(saveHolidays);
        content.addView(space(22));

        content.addView(sectionTitle("ホーム画面", "アプリを開かず次発を確認できます"));
        Button pinWidget = secondaryButton("次発ウィジェットを追加");
        pinWidget.setOnClickListener(v -> requestPinWidget());
        content.addView(pinWidget);
        content.addView(space(8));
        Button notificationSettings = smallButton("通知の設定を開く");
        notificationSettings.setMinHeight(dp(48));
        notificationSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });
        content.addView(notificationSettings);
        Button cancelReminders = smallButton("予約した出発通知をすべて取り消す");
        cancelReminders.setMinHeight(dp(48));
        cancelReminders.setOnClickListener(v -> cancelAllReminders());
        content.addView(cancelReminders);
        content.addView(space(22));

        content.addView(sectionTitle("登録管理", "無効にした候補もここから編集できます"));
        if (plans.isEmpty()) {
            content.addView(text("登録はありません", 14, MUTED, Typeface.NORMAL));
        } else {
            for (RoutePlan plan : plans) {
                LinearLayout item = horizontal(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(14), dp(11), dp(8), dp(11));
                item.setBackground(roundRect(SURFACE, 14, CONTROL, 1));
                LinearLayout labels = vertical(Color.TRANSPARENT);
                labels.addView(text((plan.enabled() ? "●  " : "○  ") + plan.routeName(), 14,
                        plan.enabled() ? INK : MUTED, Typeface.BOLD));
                labels.addView(text(plan.stopName() + " → " + plan.destination(), 14, MUTED, Typeface.NORMAL));
                item.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Button edit = smallButton("編集");
                edit.setOnClickListener(v -> showForm(plan));
                item.addView(edit);
                content.addView(item);
                content.addView(space(8));
            }
        }
        content.addView(space(16));
        content.addView(sectionTitle("プライバシー", "通信・アカウント・位置情報・分析SDKは使用しません。登録は端末内だけに保存されます。"));
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

    private Button secondaryButton(String label) {
        Button button = primaryButton(label);
        button.setTextColor(BRAND_DARK);
        button.setBackground(interactiveBackground(SURFACE, 14, BRAND, 2));
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
                .putExtra("detail", plan.stopName() + " " + departure.at().format(timeFormat) + "発")
                .putExtra("notificationId", Math.abs(plan.id().hashCode()));
        PendingIntent pending = PendingIntent.getBroadcast(this, Math.abs(plan.id().hashCode()), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        Toast.makeText(this, leaveAt.format(timeFormat) + "ごろ通知します", Toast.LENGTH_LONG).show();
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
                    if (repository.save(updated)) {
                        plans.clear(); plans.addAll(updated);
                        NextDepartureWidget.updateAll(this);
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
                        plans.clear(); plans.addAll(repository.load());
                        NextDepartureWidget.updateAll(this);
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
        RoutePlan copy = new RoutePlan(null, source.direction(), source.mode(), source.routeName() + " コピー",
                source.stopName(), source.destination(), source.walkMinutes(), source.rideMinutes(),
                source.finalWalkMinutes(), source.enabled(), source.weekdayTimes(), source.weekendTimes(),
                source.holidayTimes(), source.notes(), source.validUntil(), System.currentTimeMillis());
        List<RoutePlan> updated = new ArrayList<>(plans);
        updated.add(copy);
        if (repository.save(updated)) {
            plans.clear(); plans.addAll(updated);
            NextDepartureWidget.updateAll(this);
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
        if (repository.save(updated)) {
            plans.clear(); plans.addAll(updated); NextDepartureWidget.updateAll(this); showDashboard();
        }
    }

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
                        target.setText(joinTimes(combined));
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

    private String joinTimes(List<LocalTime> times) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) result.append(i % 6 == 0 ? '\n' : ' ');
            result.append(times.get(i).format(timeFormat));
        }
        return result.toString();
    }

    private String formatDate(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日（"
                + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.JAPAN) + "）";
    }

    private void addField(LinearLayout parent, String label, View input, String hint) {
        TextView labelView = text(label, 14, INK, Typeface.BOLD);
        labelView.setPadding(0, 0, 0, dp(7));
        if (input.getId() != View.NO_ID) labelView.setLabelFor(input.getId());
        parent.addView(labelView);
        parent.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hint != null) {
            TextView hintView = text(hint, 14, MUTED, Typeface.NORMAL);
            hintView.setPadding(0, dp(5), 0, 0);
            parent.addView(hintView);
        }
        parent.addView(space(16));
    }

    private EditText input(String hint, int inputType, boolean multiline) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(HINT);
        input.setHint(hint);
        input.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), multiline ? dp(12) : 0, dp(14), multiline ? dp(12) : 0);
        input.setMinHeight(dp(56));
        input.setBackgroundTintList(null);
        input.setBackground(focusableInputBackground(13));
        if (multiline) {
            input.setMinLines(3);
            input.setMaxLines(7);
        } else {
            input.setSingleLine(true);
        }
        return input;
    }

    private Spinner spinner(String[] choices) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(16);
                view.setPadding(dp(12), 0, dp(12), 0);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(16);
                view.setBackgroundColor(SURFACE);
                view.setMinHeight(dp(52));
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(16), dp(8), dp(16), dp(8));
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setMinimumHeight(dp(56));
        spinner.setBackgroundTintList(null);
        spinner.setBackground(focusableInputBackground(13));
        return spinner;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(BRAND, 16, 0, 0));
        return button;
    }

    private Button segmentButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(selected ? BRAND_DARK : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(dp(52));
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(selected ? SURFACE : Color.TRANSPARENT,
                13, selected ? CONTROL : 0, selected ? 1 : 0));
        button.setElevation(selected ? dp(1) : 0);
        return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(BRAND_DARK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(dp(62));
        button.setMinHeight(dp(48));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(Color.TRANSPARENT, 12, 0, 0));
        return button;
    }

    private TextView pill(String label, int foreground, int background) {
        TextView view = text(label, 13, foreground, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(roundRect(background, 30, 0, 0));
        return view;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(1), 1.04f);
        return view;
    }

    private TextView centerText(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout vertical(int background) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(background);
        return layout;
    }

    private LinearLayout horizontal(int gravity) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(gravity);
        return layout;
    }

    private Space space(int heightDp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(heightDp)));
        return space;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable roundGradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Drawable focusableInputBackground(int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                roundRect(SURFACE, radiusDp, BRAND, 2));
        states.addState(new int[]{android.R.attr.state_enabled},
                roundRect(SURFACE, radiusDp, CONTROL, 1));
        states.addState(new int[]{}, roundRect(CANVAS, radiusDp, CONTROL, 1));
        return states;
    }

    private Drawable interactiveBackground(int color, int radiusDp, int strokeColor, int strokeDp) {
        int focusColor = color == BRAND || color == BRAND_DARK ? WHITE : BRAND;
        StateListDrawable content = new StateListDrawable();
        content.addState(new int[]{android.R.attr.state_focused},
                roundRect(color, radiusDp, focusColor, 2));
        content.addState(new int[]{}, roundRect(color, radiusDp, strokeColor, strokeDp));
        GradientDrawable mask = roundRect(WHITE, radiusDp, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(0x33006B4F), content, mask);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(focused.getWindowToken(), 0);
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
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            outer.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(outer);
    }

    private void markAsHeading(TextView view) {
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }
}
