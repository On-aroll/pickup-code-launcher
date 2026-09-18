package cn.pickup.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class ParcelStore extends SQLiteOpenHelper {
    ParcelStore(Context context) {
        super(context.getApplicationContext(), "parcels.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE parcels (id TEXT PRIMARY KEY, code TEXT NOT NULL, station TEXT NOT NULL, "
                + "address TEXT NOT NULL, courier TEXT NOT NULL, source TEXT NOT NULL, original TEXT NOT NULL, "
                + "warning TEXT NOT NULL, import_key TEXT NOT NULL, created_at INTEGER NOT NULL, picked_at INTEGER NOT NULL, review INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported parcel schema migration");
    }

    List<Parcel> all() {
        List<Parcel> parcels = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("parcels", null, null, null, null, null, "created_at DESC, rowid ASC")) {
            while (cursor.moveToNext()) {
                Parcel p = new Parcel();
                p.id = string(cursor, "id");
                p.code = string(cursor, "code");
                p.station = string(cursor, "station");
                p.address = string(cursor, "address");
                p.courier = string(cursor, "courier");
                p.source = string(cursor, "source");
                p.original = string(cursor, "original");
                p.warning = string(cursor, "warning");
                p.importKey = string(cursor, "import_key");
                p.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                p.pickedAt = cursor.getLong(cursor.getColumnIndexOrThrow("picked_at"));
                p.needsReview = cursor.getInt(cursor.getColumnIndexOrThrow("review")) != 0;
                parcels.add(p);
            }
        }
        return parcels;
    }

    private String string(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    void save(Parcel p) {
        ContentValues values = new ContentValues();
        values.put("id", p.id);
        values.put("code", p.code);
        values.put("station", p.station);
        values.put("address", p.address);
        values.put("courier", p.courier);
        values.put("source", p.source);
        values.put("original", p.original);
        values.put("warning", p.warning);
        values.put("import_key", p.importKey);
        values.put("created_at", p.createdAt);
        values.put("picked_at", p.pickedAt);
        values.put("review", p.needsReview ? 1 : 0);
        getWritableDatabase().replaceOrThrow("parcels", null, values);
    }

    /** Group edits are atomic, including when storage is full. */
    void saveAll(List<Parcel> parcels) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Parcel p : parcels) save(p);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Returns false only for an exact reimport of an active record. */
    boolean importDraft(Parcel p) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<Parcel> existing = all();
            for (Parcel other : existing) {
                if (other.exactImportOf(p)) {
                    db.setTransactionSuccessful();
                    return false;
                }
                if (other.possibleDuplicateOf(p)) p.warning += "；同地点已有相同码，请检查重复";
            }
            save(p);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    void delete(String id) {
        getWritableDatabase().delete("parcels", "id=?", new String[]{id});
    }
}
