package cn.pickup.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class ParcelWorkflowTest {
    private Context app;

    @Before public void resetLocalData() {
        app = RuntimeEnvironment.getApplication();
        app.deleteDatabase("parcels.db");
    }

    @Test public void databaseRetainsFullFieldsAndCompletionAfterReopening() {
        Parcel p = ParcelParser.parse("取件码：A-02-0038\n地点：东门菜鸟驿站\n地址：幸福路18号", "短信").get(0);
        p.needsReview = false;
        p.pickedAt = 1000;
        try (ParcelStore store = new ParcelStore(app)) { store.save(p); }
        try (ParcelStore store = new ParcelStore(app)) {
            Parcel actual = store.all().get(0);
            assertEquals(p.id, actual.id);
            assertEquals("A-02-0038", actual.code);
            assertEquals("东门菜鸟驿站", actual.station);
            assertEquals("幸福路18号", actual.address);
            assertEquals(1000, actual.pickedAt);
            assertFalse(actual.needsReview);
            assertEquals(p.original, actual.original);
            assertEquals(p.importKey, actual.importKey);
        }
    }

    @Test public void exactReimportDoesNotOverwriteUserCorrections() {
        String text = "取件码：A-02-0038\n地点：东门菜鸟驿站";
        try (ParcelStore store = new ParcelStore(app)) {
            Parcel p = ParcelParser.parse(text, "截图1").get(0);
            assertTrue(store.importDraft(p));
            p.code = "A-02-0088"; p.needsReview = false; store.save(p);
            assertFalse(store.importDraft(ParcelParser.parse(text, "截图2").get(0)));
            assertEquals(1, store.all().size());
            assertEquals("A-02-0088", store.all().get(0).code);
            p.pickedAt = System.currentTimeMillis(); store.save(p);
            assertTrue(store.importDraft(ParcelParser.parse(text, "新包裹").get(0)));
            assertEquals(2, store.all().size());
        }
    }

    @Test public void aSharedPickupCodeCanRepresentTwoSeparateDrafts() {
        List<Parcel> records = ParcelParser.parse("地点：东门驿站\n取件码：1-2-003、1-2-003", "截图");
        try (ParcelStore store = new ParcelStore(app)) {
            assertTrue(store.importDraft(records.get(0)));
            assertTrue(store.importDraft(records.get(1)));
            assertEquals(2, store.all().size());
        }
    }

    @Test public void userCanReviewEditCollectAndRestoreAParcel() {
        try (ParcelStore store = new ParcelStore(app)) {
            store.save(ParcelParser.parse("取件码：2-103-4567\n地点：幸福小区东门菜鸟驿站", "测试通知").get(0));
        }
        try (ActivityController<ParcelActivity> controller = Robolectric.buildActivity(ParcelActivity.class).setup()) {
            ParcelActivity activity = controller.get();
            click(activity.getWindow().getDecorView(), "待核对 1");
            click(activity.getWindow().getDecorView(), "核对 / 修改");
            AlertDialog edit = ShadowAlertDialog.getLatestAlertDialog();
            EditText code = hint(edit.getWindow().getDecorView(), "完整取件码 / 货架码");
            assertNotNull(code);
            code.setText("A-02-0038");
            edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            click(activity.getWindow().getDecorView(), "待取 1");
            assertNotNull(text(activity.getWindow().getDecorView(), "A-02-0038"));
            click(activity.getWindow().getDecorView(), "已取");
            click(activity.getWindow().getDecorView(), "已取 1");
            click(activity.getWindow().getDecorView(), "恢复待取");
            try (ParcelStore store = new ParcelStore(app)) {
                Parcel actual = store.all().get(0);
                assertFalse(actual.needsReview);
                assertEquals(0, actual.pickedAt);
                assertEquals("A-02-0038", actual.code);
            }
        }
    }

    @Test public void screenshotImportRequestsMultipleImages() {
        try (ActivityController<ParcelActivity> controller = Robolectric.buildActivity(ParcelActivity.class).setup()) {
            ParcelActivity activity = controller.get();
            click(activity.getWindow().getDecorView(), "导入多张截图");
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;
            assertEquals(Intent.ACTION_GET_CONTENT, intent.getAction());
            assertEquals("image/*", intent.getType());
            assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
        }
    }

    @Test public void anUnsubmittedCorrectionSurvivesRecreation() {
        try (ParcelStore store = new ParcelStore(app)) {
            store.save(ParcelParser.parse("取件码：1-2-003\n地点：东门驿站", "测试").get(0));
        }
        try (ActivityController<ParcelActivity> controller = Robolectric.buildActivity(ParcelActivity.class).setup()) {
            click(controller.get().getWindow().getDecorView(), "待核对 1");
            click(controller.get().getWindow().getDecorView(), "核对 / 修改");
            AlertDialog edit = ShadowAlertDialog.getLatestAlertDialog();
            hint(edit.getWindow().getDecorView(), "完整取件码 / 货架码").setText("B-09-0008");
            controller.recreate();
            AlertDialog restored = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("B-09-0008", hint(restored.getWindow().getDecorView(), "完整取件码 / 货架码").getText().toString());
            restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            try (ParcelStore store = new ParcelStore(app)) {
                assertEquals(1, store.all().size());
                assertEquals("B-09-0008", store.all().get(0).code);
            }
        }
    }

    @Test public void groupLocationEditingPreservesCodesAndGroupsTheRecords() {
        List<Parcel> records = ParcelParser.parse("地点：东门驿站\n取件码：1-2-003、A-3-128", "测试");
        for (Parcel p : records) p.needsReview = false;
        try (ParcelStore store = new ParcelStore(app)) { store.saveAll(records); }
        try (ActivityController<ParcelActivity> controller = Robolectric.buildActivity(ParcelActivity.class).setup()) {
            click(controller.get().getWindow().getDecorView(), "统一地点");
            AlertDialog edit = ShadowAlertDialog.getLatestAlertDialog();
            hint(edit.getWindow().getDecorView(), "统一取件点名称").setText("菜鸟驿站（东门店）");
            hint(edit.getWindow().getDecorView(), "统一地址").setText("幸福路18号");
            edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            try (ParcelStore store = new ParcelStore(app)) {
                List<Parcel> actual = store.all();
                assertEquals(2, actual.size());
                assertEquals(1, Parcel.groups(actual).size());
                assertEquals("幸福路18号", actual.get(0).address);
                assertEquals("幸福路18号", actual.get(1).address);
                assertTrue(Parcel.summary(actual).contains("1-2-003"));
                assertTrue(Parcel.summary(actual).contains("A-3-128"));
            }
        }
    }

    @Test public void launcherOpensTheChecklistInsteadOfTheOldDigitDialog() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            Intent intent = Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(ParcelActivity.class.getName(), intent.getComponent().getClassName());
        }
    }

    private static void click(View root, String label) {
        TextView target = text(root, label);
        assertNotNull("Missing action " + label, target);
        assertTrue(target.performClick());
    }

    private static TextView text(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                TextView found = text(((ViewGroup) root).getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static EditText hint(View root, String hint) {
        if (root instanceof EditText && hint.contentEquals(((EditText) root).getHint())) return (EditText) root;
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                EditText found = hint(((ViewGroup) root).getChildAt(i), hint);
                if (found != null) return found;
            }
        }
        return null;
    }
}
