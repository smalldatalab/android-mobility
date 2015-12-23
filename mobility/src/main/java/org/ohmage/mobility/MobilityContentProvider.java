package org.ohmage.mobility;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

public class MobilityContentProvider extends ContentProvider {
    private static final String CONTENT_AUTHORITY = "org.ohmage.mobility";

    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    private static final long MAX_POINTS = 50;
    private static UriMatcher sUriMatcher;
    private MobilityDbHelper dbHelper;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MatcherTypes.ACTIVITY:
                return ActivityPoint.CONTENT_ITEM_TYPE;
            case MatcherTypes.LOCATION:
                return LocationPoint.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("getType(): Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long result = -1;
        String table = null;

        ContentResolver cr = getContext().getContentResolver();
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        switch (sUriMatcher.match(uri)) {
            case MatcherTypes.ACTIVITY:
                table = MobilityDbHelper.Tables.Activity;
                break;
            case MatcherTypes.LOCATION:
                table = MobilityDbHelper.Tables.Location;
                break;
            default:
                throw new UnsupportedOperationException("insert(): Unknown URI: " + uri);
        }

        result = db.insert(table, null, values);
        // Only keep MAX_POINTS
        db.delete(table, BaseColumns._ID + "<" + Math.max(result - MAX_POINTS, 0), null);

        if (result != -1) {
            cr.notifyChange(uri, null, false);
        }
        return uri;
    }

    @Override
    public boolean onCreate() {
        dbHelper = new MobilityDbHelper(getContext());
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(CONTENT_AUTHORITY, "activity", MatcherTypes.ACTIVITY);
        sUriMatcher.addURI(CONTENT_AUTHORITY, "location", MatcherTypes.LOCATION);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String table = null;
        switch (sUriMatcher.match(uri)) {
            case MatcherTypes.ACTIVITY:
                table = MobilityDbHelper.Tables.Activity;
                break;
            case MatcherTypes.LOCATION:
                table = MobilityDbHelper.Tables.Location;
                break;
            default:
                throw new UnsupportedOperationException("query(): Unknown URI: " + uri);
        }

        Cursor cursor = dbHelper.getReadableDatabase().query(table, projection, selection,
                selectionArgs, null, null, sortOrder);

        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // enum of the URIs we can match using sUriMatcher
    private interface MatcherTypes {
        int ACTIVITY = 0;
        int LOCATION = 1;
    }

    public static class ActivityPoint {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath("activity")
                .build();

        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.ohmage.mobility.activity";

        public static final String DATA = "data";
    }

    public static class LocationPoint {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath("location")
                .build();

        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.ohmage.mobility.location";

        public static final String DATA = "data";
    }

    public static class MobilityDbHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "mobility.db";

        private static final int DB_VERSION = 2;

        public MobilityDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + Tables.Activity + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + ActivityPoint.DATA + " TEXT NOT NULL);");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + Tables.Location + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + ActivityPoint.DATA + " TEXT NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + Tables.Activity);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.Location);
            onCreate(db);
        }

        public interface Tables {
            static final String Activity = "activity";
            static final String Location = "location";
        }
    }
}
