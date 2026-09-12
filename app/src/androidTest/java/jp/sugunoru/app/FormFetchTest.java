package jp.sugunoru.app;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import jp.sugunoru.app.data.OfficialTimetableParser;

/** Exercises real async completion with controlled results, without contacting a provider. */
public class FormFetchTest extends InstrumentationTestCase {
    public void testStaleSuccessAndFailureCheckSelectionBeforeApplyingResult() throws Exception {
        assertEquals("jp.sugunoru.app.verification",
                getInstrumentation().getTargetContext().getPackageName());
        MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            checkCompletion(activity, false, false);
            checkCompletion(activity, true, false);
            checkCompletion(activity, false, true);
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    private void checkCompletion(MainActivity activity, boolean fail, boolean current) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod("fetchTimetableIntoForm", String.class,
                Callable.class, EditText.class, EditText.class, EditText.class,
                Button.class, BooleanSupplier.class, Runnable.class);
        method.setAccessible(true);
        CountDownLatch checkedSelection = new CountDownLatch(1);
        AtomicBoolean applied = new AtomicBoolean();
        EditText[] inputs = new EditText[3];
        Button[] button = new Button[1];
        getInstrumentation().runOnMainSync(() -> {
            LinearLayout form = new LinearLayout(activity);
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = new EditText(activity);
                inputs[i].setText("06:00");
                form.addView(inputs[i]);
            }
            button[0] = new Button(activity);
            form.addView(button[0]);
            activity.setContentView(form);
        });
        getInstrumentation().waitForIdleSync();
        Callable<OfficialTimetableParser.Timetable> fetch = () -> {
            if (fail) throw new Exception("controlled failure");
            return new OfficialTimetableParser.Timetable(List.of(LocalTime.of(7, 0)), List.of(), List.of());
        };
        getInstrumentation().runOnMainSync(() -> {
            try {
                method.invoke(activity, "検証", fetch, inputs[0], inputs[1], inputs[2], button[0],
                        (BooleanSupplier) () -> { checkedSelection.countDown(); return current; },
                        (Runnable) () -> applied.set(true));
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
        assertTrue("Completion must check selection (emulator needs a usable network)",
                checkedSelection.await(10, TimeUnit.SECONDS));
        getInstrumentation().runOnMainSync(() -> {
            assertTrue(button[0].isEnabled());
            assertEquals(current && !fail, applied.get());
            assertEquals(current && !fail ? "07:00" : "06:00", inputs[0].getText().toString());
        });
    }
}
