package com.farmmonitor.agriai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

// ✅ Correct import
import com.farmmonitor.agriai.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";

    // Database Info
    private static final String DATABASE_NAME = "AgriSmartDB";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_USERS = "users";

    // Users Table Columns
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_CITY = "city";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_NAME + " TEXT NOT NULL,"
                + KEY_EMAIL + " TEXT UNIQUE NOT NULL,"
                + KEY_CITY + " TEXT,"
                + KEY_PASSWORD + " TEXT NOT NULL,"
                + KEY_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";

        db.execSQL(CREATE_USERS_TABLE);
        Log.d(TAG, "Database tables created");

        // Insert default admin user
        insertDefaultAdmin(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Insert default admin user
    private void insertDefaultAdmin(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, "Admin User");
        values.put(KEY_EMAIL, "admin@gmail.com");
        values.put(KEY_CITY, "Delhi");
        values.put(KEY_PASSWORD, hashPassword("123456"));

        long result = db.insert(TABLE_USERS, null, values);
        if (result != -1) {
            Log.d(TAG, "Default admin user created");
        }
    }

    // Register new user
    public long registerUser(String name, String email, String city, String password) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_NAME, name);
        values.put(KEY_EMAIL, email.toLowerCase());
        values.put(KEY_CITY, city);
        values.put(KEY_PASSWORD, hashPassword(password));

        long result = db.insert(TABLE_USERS, null, values);
        db.close();

        if (result != -1) {
            Log.d(TAG, "User registered successfully: " + email);
        } else {
            Log.e(TAG, "Failed to register user: " + email);
        }

        return result;
    }

    // Check if user exists and password is correct
    public User loginUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        User user = null;

        String hashedPassword = hashPassword(password);

        String query = "SELECT * FROM " + TABLE_USERS +
                " WHERE " + KEY_EMAIL + " = ? AND " + KEY_PASSWORD + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{email.toLowerCase(), hashedPassword});

        if (cursor != null && cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID)));
            user.setName(cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)));
            user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(KEY_EMAIL)));
            user.setCity(cursor.getString(cursor.getColumnIndexOrThrow(KEY_CITY)));

            Log.d(TAG, "User logged in successfully: " + email);
            cursor.close();
        } else {
            Log.d(TAG, "Login failed for user: " + email);
        }

        db.close();
        return user;
    }

    // Check if email already exists
    public boolean isEmailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_EMAIL + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{email.toLowerCase()});

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();

        return exists;
    }

    // Get user by email
    public User getUserByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        User user = null;

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_EMAIL + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{email.toLowerCase()});

        if (cursor != null && cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID)));
            user.setName(cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)));
            user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(KEY_EMAIL)));
            user.setCity(cursor.getString(cursor.getColumnIndexOrThrow(KEY_CITY)));

            cursor.close();
        }

        db.close();
        return user;
    }

    // Update user profile
    public int updateUser(int userId, String name, String city) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_NAME, name);
        values.put(KEY_CITY, city);

        int rowsAffected = db.update(TABLE_USERS, values,
                KEY_ID + " = ?",
                new String[]{String.valueOf(userId)});
        db.close();

        return rowsAffected;
    }

    // Delete user
    public void deleteUser(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USERS, KEY_ID + " = ?", new String[]{String.valueOf(userId)});
        db.close();
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing password", e);
            return password; // Fallback (not recommended for production)
        }
    }

    // Get total user count
    public int getUserCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_USERS;
        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return count;
    }
}