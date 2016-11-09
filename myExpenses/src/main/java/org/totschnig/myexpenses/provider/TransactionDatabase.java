/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.provider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Locale;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.model.Category;
import org.totschnig.myexpenses.model.CurrencyEnum;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.PaymentMethod;
import org.totschnig.myexpenses.model.Plan;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.Utils;

import com.android.calendar.CalendarContractCompat.Events;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

public class TransactionDatabase extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 58;
    public static final String DATABASE_NAME = "data";
    private Context mCtx;

    private static final String TAG = "TransactionDatabase";
    /**
     * SQL statement for expenses TABLE
     * both transactions and transfers are stored in this table
     * for transfers there are two rows (one per account) which
     * are linked by KEY_TRANSFER_PEER
     * for normal transactions KEY_TRANSFER_PEER is set to NULL
     * split parts are linked with their parents through KEY_PARENTID
     * KEY_STATUS has STATUS_EXPORTED if transaction is exported, and
     * STATUS_UNCOMMITTED for transactions that are created during editing of splits
     * KEY_CR_STATUS stores cleared/reconciled
     */
    private static final String DATABASE_CREATE =
            "CREATE TABLE " + TABLE_TRANSACTIONS + "( "
                    + KEY_ROWID + " integer primary key autoincrement, "
                    + KEY_COMMENT + " text, "
                    + KEY_DATE + " datetime not null, "
                    + KEY_AMOUNT + " integer not null, "
                    + KEY_CATID + " integer references " + TABLE_CATEGORIES + "(" + KEY_ROWID + "), "
                    + KEY_ACCOUNTID + " integer not null references " + TABLE_ACCOUNTS + "(" + KEY_ROWID + ") ON DELETE CASCADE,"
                    + KEY_PAYEEID + " integer references " + TABLE_PAYEES + "(" + KEY_ROWID + "), "
                    + KEY_TRANSFER_PEER + " integer references " + TABLE_TRANSACTIONS + "(" + KEY_ROWID + "), "
                    + KEY_TRANSFER_ACCOUNT + " integer references " + TABLE_ACCOUNTS + "(" + KEY_ROWID + "),"
                    + KEY_METHODID + " integer references " + TABLE_METHODS + "(" + KEY_ROWID + "),"
                    + KEY_PARENTID + " integer references " + TABLE_TRANSACTIONS + "(" + KEY_ROWID + ") ON DELETE CASCADE, "
                    + KEY_STATUS + " integer default 0, "
                    + KEY_CR_STATUS + " text not null check (" + KEY_CR_STATUS + " in (" + Transaction.CrStatus.JOIN + ")) default '" + Transaction.CrStatus.RECONCILED.name() + "',"
                    + KEY_REFERENCE_NUMBER + " text, "
                    + KEY_PICTURE_URI + " text);";

    private static String buildViewDefinition(String tableName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" AS SELECT ").append(tableName).append(".*, ").append(TABLE_PAYEES)
                .append(".").append(KEY_PAYEE_NAME).append(", ")
                .append(TABLE_METHODS).append(".").append(KEY_LABEL).append(" AS ").append(KEY_METHOD_LABEL);

        if (tableName.equals(TABLE_TRANSACTIONS)) {
            stringBuilder.append(", ").append(TABLE_PLAN_INSTANCE_STATUS).append(".").append(KEY_TEMPLATEID);
        }

        stringBuilder.append(" FROM ").append(tableName).append(" LEFT JOIN ").append(TABLE_PAYEES).append(" ON ")
                .append(KEY_PAYEEID).append(" = ").append(TABLE_PAYEES).append(".").append(KEY_ROWID).append(" LEFT JOIN ")
                .append(TABLE_METHODS).append(" ON ").append(KEY_METHODID).append(" = ").append(TABLE_METHODS)
                .append(".").append(KEY_ROWID);

        if (tableName.equals(TABLE_TRANSACTIONS)) {
            stringBuilder.append(" LEFT JOIN ").append(TABLE_PLAN_INSTANCE_STATUS)
                    .append(" ON ").append(tableName).append(".").append(KEY_ROWID).append(" = ")
                    .append(TABLE_PLAN_INSTANCE_STATUS).append(".").append(KEY_TRANSACTIONID);
        }
        return stringBuilder.toString();
    }

    private static String buildViewDefinitionExtended(String tableName) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(" AS SELECT ").append(tableName).append(".*, ").append(TABLE_PAYEES)
                .append(".").append(KEY_PAYEE_NAME).append(", ").append(KEY_COLOR).append(", ")
                .append(KEY_CURRENCY).append(", ").append(KEY_EXCLUDE_FROM_TOTALS).append(", ")
                .append(TABLE_METHODS).append(".").append(KEY_LABEL).append(" AS ").append(KEY_METHOD_LABEL);

        if (tableName.equals(TABLE_TRANSACTIONS)) {
            stringBuilder.append(", ").append(TABLE_PLAN_INSTANCE_STATUS).append(".").append(KEY_TEMPLATEID);
        }

        stringBuilder.append(" FROM ").append(tableName).append(" LEFT JOIN ").append(TABLE_PAYEES).append(" ON ")
                .append(KEY_PAYEEID).append(" = ").append(TABLE_PAYEES).append(".").append(KEY_ROWID)
                .append(" LEFT JOIN ").append(TABLE_ACCOUNTS).append(" ON ").append(KEY_ACCOUNTID)
                .append(" = ").append(TABLE_ACCOUNTS).append(".").append(KEY_ROWID).append(" LEFT JOIN ")
                .append(TABLE_METHODS).append(" ON ").append(KEY_METHODID).append(" = ").append(TABLE_METHODS)
                .append(".").append(KEY_ROWID);

        if (tableName.equals(TABLE_TRANSACTIONS)) {
            stringBuilder.append(" LEFT JOIN ").append(TABLE_PLAN_INSTANCE_STATUS)
                    .append(" ON ").append(tableName).append(".").append(KEY_ROWID).append(" = ")
                    .append(TABLE_PLAN_INSTANCE_STATUS).append(".").append(KEY_TRANSACTIONID);
        }

        return stringBuilder.toString();
    }

    /**
     * SQL statement for accounts TABLE
     */
    private static final String ACCOUNTS_CREATE =
            "CREATE TABLE " + TABLE_ACCOUNTS + " ("
                    + KEY_ROWID + " integer primary key autoincrement, "
                    + KEY_LABEL + " text not null, "
                    + KEY_OPENING_BALANCE + " integer, "
                    + KEY_DESCRIPTION + " text, "
                    + KEY_CURRENCY + " text not null, "
                    + KEY_TYPE + " text not null check (" + KEY_TYPE + " in (" + AccountType.JOIN + ")) default '" + AccountType.CASH.name() + "', "
                    + KEY_COLOR + " integer default -3355444, "
                    + KEY_GROUPING + " text not null check (" + KEY_GROUPING + " in (" + Grouping.JOIN + ")) default '" + Grouping.NONE.name() + "', "
                    + KEY_USAGES + " integer default 0,"
                    + KEY_LAST_USED + " datetime, "
                    + KEY_SORT_KEY + " integer,"
                    + KEY_EXCLUDE_FROM_TOTALS + " boolean default 0);";

    /**
     * SQL statement for categories TABLE
     * Table definition reflects format of Grisbis categories
     * Main categories have parent_id 0
     * usages counts how often the cat is selected
     */
    private static final String CATEGORIES_CREATE =
            "CREATE TABLE " + TABLE_CATEGORIES + " ("
                    + KEY_ROWID + " integer primary key autoincrement, "
                    + KEY_LABEL + " text not null, "
                    + KEY_LABEL_NORMALIZED + " text,"
                    + KEY_PARENTID + " integer references " + TABLE_CATEGORIES + "(" + KEY_ROWID + "), "
                    + KEY_USAGES + " integer default 0, "
                    + KEY_LAST_USED + " datetime, "
                    + "unique (" + KEY_LABEL + "," + KEY_PARENTID + "));";

    private static final String PAYMENT_METHODS_CREATE =
            "CREATE TABLE " + TABLE_METHODS + " ("
                    + KEY_ROWID + " integer primary key autoincrement, "
                    + KEY_LABEL + " text not null, "
                    + KEY_IS_NUMBERED + " boolean default 0, "
                    + KEY_TYPE + " integer " +
                    "check (" + KEY_TYPE + " in ("
                    + PaymentMethod.EXPENSE + ","
                    + PaymentMethod.NEUTRAL + ","
                    + PaymentMethod.INCOME + ")) default 0);";

    private static final String ACCOUNTTYE_METHOD_CREATE =
            "CREATE TABLE " + TABLE_ACCOUNTTYES_METHODS + " ("
                    + KEY_TYPE + " text not null check (" + KEY_TYPE + " in (" + AccountType.JOIN + ")), "
                    + KEY_METHODID + " integer references " + TABLE_METHODS + "(" + KEY_ROWID + "), "
                    + "primary key (" + KEY_TYPE + "," + KEY_METHODID + "));";

    /**
     * {@link DatabaseConstants#KEY_TRANSFER_PEER} does not point to another instance
     * but is a boolean indicating if the template is for a transfer
     * {@link DatabaseConstants#KEY_PLANID} references an event in com.android.providers.calendar
     */
    private static final String TEMPLATE_CREATE =
            "CREATE TABLE " + TABLE_TEMPLATES + " ( "
                    + KEY_ROWID + " integer primary key autoincrement, "
                    + KEY_COMMENT + " text, "
                    + KEY_AMOUNT + " integer not null, "
                    + KEY_CATID + " integer references " + TABLE_CATEGORIES + "(" + KEY_ROWID + "), "
                    + KEY_ACCOUNTID + " integer not null references " + TABLE_ACCOUNTS + "(" + KEY_ROWID + ") ON DELETE CASCADE,"
                    + KEY_PAYEEID + " integer references " + TABLE_PAYEES + "(" + KEY_ROWID + "), "
                    + KEY_TRANSFER_PEER + " boolean default 0, "
                    + KEY_TRANSFER_ACCOUNT + " integer references " + TABLE_ACCOUNTS + "(" + KEY_ROWID + ") ON DELETE CASCADE,"
                    + KEY_METHODID + " integer references " + TABLE_METHODS + "(" + KEY_ROWID + "), "
                    + KEY_TITLE + " text not null, "
                    + KEY_USAGES + " integer default 0, "
                    + KEY_PLANID + " integer, "
                    + KEY_PLAN_EXECUTION + " boolean default 0, "
                    + KEY_UUID + " text, "
                    + KEY_LAST_USED + " datetime);";

    private static final String EVENT_CACHE_CREATE =
            "CREATE TABLE " + TABLE_EVENT_CACHE + " ( " +
                    Events.TITLE + " TEXT," +
                    Events.DESCRIPTION + " TEXT," +
                    Events.DTSTART + " INTEGER," +
                    Events.DTEND + " INTEGER," +
                    Events.EVENT_TIMEZONE + " TEXT," +
                    Events.DURATION + " TEXT," +
                    Events.ALL_DAY + " INTEGER NOT NULL DEFAULT 0," +
                    Events.RRULE + " TEXT," +
                    Events.CUSTOM_APP_PACKAGE + " TEXT," +
                    Events.CUSTOM_APP_URI + " TEXT);";


    /**
     * stores payees and payers
     * this table is used for populating the autocompleting text field,
     */
    private static final String PAYEE_CREATE =
            "CREATE TABLE " + TABLE_PAYEES
                    + " (" + KEY_ROWID + " integer primary key autoincrement, " +
                    KEY_PAYEE_NAME + " text unique not null," +
                    KEY_PAYEE_NAME_NORMALIZED + " text);";

    private static final String CURRENCY_CREATE =
            "CREATE TABLE " + TABLE_CURRENCIES
                    + " (" + KEY_ROWID + " integer primary key autoincrement, " + KEY_CODE
                    + " text unique not null);";

    /**
     * in this table we store links between plan instances and transactions,
     * thus allowing us to track if an instance has been applied, and to allow editing or cancellation of
     * transactions added from plan instances
     */
    private static final String PLAN_INSTANCE_STATUS_CREATE =
            "CREATE TABLE " + TABLE_PLAN_INSTANCE_STATUS
                    + " ( " + KEY_TEMPLATEID + " integer references " + TABLE_TEMPLATES + "(" + KEY_ROWID + ") ON DELETE CASCADE," +
                    KEY_INSTANCEID + " integer," + // references Instances._ID in calendar content provider
                    KEY_TRANSACTIONID + " integer references " + TABLE_TRANSACTIONS + "(" + KEY_ROWID + ") ON DELETE CASCADE, " +
                    "primary key (" + KEY_INSTANCEID + "," + KEY_TRANSACTIONID + "));";

    private static final String STALE_URIS_CREATE =
            "CREATE TABLE " + TABLE_STALE_URIS
                    + " ( " + KEY_PICTURE_URI + " text);";

    private static final String STALE_URI_TRIGGER_CREATE =
            "CREATE TRIGGER cache_stale_uri " +
                    "AFTER DELETE ON " + TABLE_TRANSACTIONS + " " +
                    "WHEN old." + KEY_PICTURE_URI + " NOT NULL " +
                    "AND NOT EXISTS " +
                    "(SELECT 1 FROM " + TABLE_TRANSACTIONS + " " +
                    "WHERE " + KEY_PICTURE_URI + " = old." + KEY_PICTURE_URI + ") " +
                    "BEGIN INSERT INTO " + TABLE_STALE_URIS + " VALUES (old." + KEY_PICTURE_URI + "); END";

    private static final String ACCOUNTS_TRIGGER_CREATE =
            "CREATE TRIGGER sort_key_default " +
                    "AFTER INSERT ON " + TABLE_ACCOUNTS + " " +
                    "BEGIN UPDATE " + TABLE_ACCOUNTS + " SET " + KEY_SORT_KEY +
                    " = (SELECT coalesce(max(" + KEY_SORT_KEY + "),0) FROM " + TABLE_ACCOUNTS + ") + 1 WHERE " +
                    KEY_ROWID + " = NEW." + KEY_ROWID + "; END";

    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    public static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    TransactionDatabase(Context context) {
        super(context, getDbName(), null, DATABASE_VERSION);
        mCtx = context;
    }

    public static String getDbName() {
        return MyApplication.isInstrumentationTest() ? MyApplication.getTestId() : DATABASE_NAME;
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        //since API 16 we could use onConfigure to enable foreign keys
        //which is run before onUpgrade
        //but this makes upgrades more difficult, since then you have to maintain the constraint in
        //each step of a multi statement upgrade with table rename
        //we stick to doing upgrades with foreign keys disabled which forces us
        //to take care of ensuring consistency during upgrades
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
        try {
            db.delete(TABLE_TRANSACTIONS, KEY_STATUS + " = " + STATUS_UNCOMMITTED, null);
        } catch (SQLiteException e) {
            AcraHelper.report(e,
                    DbUtils.getTableDetails(
                            db.query("sqlite_master",
                                    new String[]{"name","sql"},
                                    "type = 'table'",
                                    null, null, null, null)));
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
        db.execSQL(PAYEE_CREATE);
        db.execSQL(PAYMENT_METHODS_CREATE);
        db.execSQL(TEMPLATE_CREATE);
        db.execSQL(PLAN_INSTANCE_STATUS_CREATE);
        String viewTransactions = buildViewDefinition(TABLE_TRANSACTIONS);
        db.execSQL("CREATE VIEW " + VIEW_COMMITTED + viewTransactions + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW " + VIEW_UNCOMMITTED + viewTransactions + " WHERE " + KEY_STATUS + " = " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW " + VIEW_ALL + viewTransactions);
        db.execSQL("CREATE VIEW " + VIEW_TEMPLATES + buildViewDefinition(TABLE_TEMPLATES));
        db.execSQL(CATEGORIES_CREATE);
        db.execSQL(ACCOUNTS_CREATE);
        db.execSQL("CREATE VIEW " + VIEW_EXTENDED + buildViewDefinitionExtended(TABLE_TRANSACTIONS) + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW " + VIEW_TEMPLATES_EXTENDED + buildViewDefinitionExtended(TABLE_TEMPLATES));
        db.execSQL(ACCOUNTS_TRIGGER_CREATE);
        insertDefaultAccount(db);
        db.execSQL(ACCOUNTTYE_METHOD_CREATE);
        insertDefaultPaymentMethods(db);
        db.execSQL(CURRENCY_CREATE);
        //category for splits needed to honour foreign constraint
        ContentValues splitCatValues = new ContentValues();
        splitCatValues.put(KEY_ROWID, SPLIT_CATID);
        splitCatValues.put(KEY_PARENTID, SPLIT_CATID);
        splitCatValues.put(KEY_LABEL, "__SPLIT_TRANSACTION__");
        db.insertOrThrow(TABLE_CATEGORIES, null, splitCatValues);
        insertCurrencies(db);
        db.execSQL(EVENT_CACHE_CREATE);
        db.execSQL(STALE_URIS_CREATE);
        db.execSQL(STALE_URI_TRIGGER_CREATE);
        db.execSQL("CREATE INDEX transactions_cat_id_index on " + TABLE_TRANSACTIONS + "(" + KEY_CATID + ")");
        db.execSQL("CREATE INDEX templates_cat_id_index on " + TABLE_TEMPLATES + "(" + KEY_CATID + ")");
    }

    private void insertCurrencies(SQLiteDatabase db) {
        ContentValues initialValues = new ContentValues();
        for (CurrencyEnum currency : CurrencyEnum.values()) {
            initialValues.put(KEY_CODE, currency.name());
            db.insert(TABLE_CURRENCIES, null, initialValues);
        }
    }

    /**
     * @param db insert the predefined payment methods in the database, all of them are valid only for bank accounts
     */
    private void insertDefaultPaymentMethods(SQLiteDatabase db) {
        ContentValues initialValues;
        long _id;
        for (PaymentMethod.PreDefined pm : PaymentMethod.PreDefined.values()) {
            initialValues = new ContentValues();
            initialValues.put(KEY_LABEL, pm.name());
            initialValues.put(KEY_TYPE, pm.paymentType);
            initialValues.put(KEY_IS_NUMBERED, pm.isNumbered);
            _id = db.insert(TABLE_METHODS, null, initialValues);
            initialValues = new ContentValues();
            initialValues.put(KEY_METHODID, _id);
            initialValues.put(KEY_TYPE, "BANK");
            db.insert(TABLE_ACCOUNTTYES_METHODS, null, initialValues);
        }
    }

    private void insertDefaultAccount(SQLiteDatabase db) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_LABEL, mCtx.getString(R.string.default_account_name));
        initialValues.put(KEY_OPENING_BALANCE, 0);
        initialValues.put(KEY_DESCRIPTION, mCtx.getString(R.string.default_account_description));
        Currency localCurrency = Utils.getLocalCurrency();
        initialValues.put(KEY_CURRENCY, localCurrency.getCurrencyCode());
        initialValues.put(KEY_TYPE, AccountType.CASH.name());
        initialValues.put(KEY_GROUPING, Grouping.NONE.name());
        initialValues.put(KEY_COLOR, Account.DEFAULT_COLOR);
        db.insert(TABLE_ACCOUNTS, null, initialValues);
        Money.ensureFractionDigitsAreCached(localCurrency);
    }

    /*
    * in onUpgrade, we can not rely on the constants, since we need the statements to be executed as defined
    * as is
    * if we would use the constants, and they change in the future, we would no longer have the same upgrade
    * and this can lead to bugs, if a later upgrade relies on column names as defined earlier,
    * and a user upgrading several versions at once would get a broken upgrade process
    */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ".");
            if (oldVersion < 17) {
                db.execSQL("drop table accounts");
                db.execSQL("CREATE TABLE accounts (_id integer primary key autoincrement, label text not null, " +
                        "opening_balance integer, description text, currency text not null);");
                //db.execSQL("ALTER TABLE expenses add column account_id integer");
            }

            if (oldVersion < 18) {
                db.execSQL("CREATE TABLE payee (_id integer primary key autoincrement, name text unique not null);");
                db.execSQL("ALTER TABLE expenses add column payee text");
            }

            if (oldVersion < 19) {
                db.execSQL("ALTER TABLE expenses add column transfer_peer text");
            }

            if (oldVersion < 20) {
                db.execSQL("CREATE TABLE transactions ( _id integer primary key autoincrement, comment text not null, "
                        + "date datetime not null, amount integer not null, cat_id integer, account_id integer, "
                        + "payee  text, transfer_peer integer default null);");
                db.execSQL("INSERT INTO transactions (comment,date,amount,cat_id,account_id,payee,transfer_peer)" +
                        " SELECT comment,date,CAST(ROUND(amount*100) AS INTEGER),cat_id,account_id,payee,transfer_peer FROM expenses");
                db.execSQL("DROP TABLE expenses");
                db.execSQL("ALTER TABLE accounts RENAME to accounts_old");
                db.execSQL("CREATE TABLE accounts (_id integer primary key autoincrement, label text not null, " +
                        "opening_balance integer, description text, currency text not null);");
                db.execSQL("INSERT INTO accounts (label,opening_balance,description,currency)" +
                        " SELECT label,CAST(ROUND(opening_balance*100) AS INTEGER),description,currency FROM accounts_old");
                db.execSQL("DROP TABLE accounts_old");
            }

            if (oldVersion < 21) {
                db.execSQL("CREATE TABLE paymentmethods (_id integer primary key autoincrement, label text not null, type integer default 0);");
                db.execSQL("CREATE TABLE accounttype_paymentmethod (type text, method_id integer, primary key (type,method_id));");
                ContentValues initialValues;
                long _id;
                for (PaymentMethod.PreDefined pm : PaymentMethod.PreDefined.values()) {
                    initialValues = new ContentValues();
                    initialValues.put("label", pm.name());
                    initialValues.put("type", pm.paymentType);
                    _id = db.insert("paymentmethods", null, initialValues);
                    initialValues = new ContentValues();
                    initialValues.put("method_id", _id);
                    initialValues.put("type", "BANK");
                    db.insert("accounttype_paymentmethod", null, initialValues);
                }
                db.execSQL("ALTER TABLE transactions add column payment_method_id integer");
                db.execSQL("ALTER TABLE accounts add column type text default 'CASH'");
            }

            if (oldVersion < 22) {
                db.execSQL("CREATE TABLE templates ( _id integer primary key autoincrement, comment text not null, "
                        + "amount integer not null, cat_id integer, account_id integer, payee text, transfer_peer integer default null, "
                        + "payment_method_id integer, title text not null);");
            }

            if (oldVersion < 23) {
                db.execSQL("ALTER TABLE templates RENAME to templates_old");
                db.execSQL("CREATE TABLE templates ( _id integer primary key autoincrement, comment text not null, "
                        + "amount integer not null, cat_id integer, account_id integer, payee text, transfer_peer integer default null, "
                        + "payment_method_id integer, title text not null, unique(account_id, title));");
                try {
                    db.execSQL("INSERT INTO templates(comment,amount,cat_id,account_id,payee,transfer_peer,payment_method_id,title)" +
                            " SELECT comment,amount,cat_id,account_id,payee,transfer_peer,payment_method_id,title FROM templates_old");
                } catch (SQLiteConstraintException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                    //theoretically we could have entered duplicate titles for one account
                    //we silently give up in that case (since this concerns only a narrowly distributed alpha version)
                }
                db.execSQL("DROP TABLE templates_old");
            }

            if (oldVersion < 24) {
                db.execSQL("ALTER TABLE templates add column usages integer default 0");
            }

            if (oldVersion < 25) {
                //for transactions that were not transfers, transfer_peer was set to null in transactions, but to 0 in templates
                db.execSQL("update transactions set transfer_peer=0 WHERE transfer_peer is null;");
            }

            if (oldVersion < 26) {
                db.execSQL("alter table accounts add column color integer default -6697984");
            }

            if (oldVersion < 27) {
                db.execSQL("CREATE TABLE feature_used (feature text not null);");
            }

            if (oldVersion < 28) {
                db.execSQL("ALTER TABLE transactions RENAME to transactions_old");
                db.execSQL("CREATE TABLE transactions(_id integer primary key autoincrement, comment text, date datetime not null, amount integer not null, " +
                        "cat_id integer references categories(_id), account_id integer not null references accounts(_id),payee text, " +
                        "transfer_peer integer references transactions(_id), transfer_account integer references accounts(_id), " +
                        "method_id integer references paymentmethods(_id));");
                db.execSQL("INSERT INTO transactions (_id,comment,date,amount,cat_id,account_id,payee,transfer_peer,transfer_account,method_id) " +
                        "SELECT _id,comment,date,amount, " +
                        "CASE WHEN transfer_peer THEN null ELSE CASE WHEN cat_id THEN cat_id ELSE null END END, " +
                        "account_id,payee, " +
                        "CASE WHEN transfer_peer THEN transfer_peer ELSE null END, " +
                        "CASE WHEN transfer_peer THEN cat_id ELSE null END, " +
                        "CASE WHEN payment_method_id THEN payment_method_id ELSE null END " +
                        "FROM transactions_old");
                db.execSQL("ALTER TABLE accounts RENAME to accounts_old");
                db.execSQL("CREATE TABLE accounts (_id integer primary key autoincrement, label text not null, opening_balance integer, description text, " +
                        "currency text not null, type text not null check (type in ('CASH','BANK','CCARD','ASSET','LIABILITY')) default 'CASH', color integer default -3355444);");
                db.execSQL("INSERT INTO accounts (_id,label,opening_balance,description,currency,type,color) " +
                        "SELECT _id,label,opening_balance,description,currency,type,color FROM accounts_old");
                //previously templates where not deleted if referred to accounts were deleted
                db.execSQL("DELETE FROM templates where account_id not in (SELECT _id FROM accounts) or (cat_id != 0 and transfer_peer = 1 and cat_id not in (SELECT _id from accounts))");
                db.execSQL("ALTER TABLE templates RENAME to templates_old");
                db.execSQL("CREATE TABLE templates ( _id integer primary key autoincrement, comment text not null, amount integer not null, " +
                        "cat_id integer references categories(_id), account_id integer not null references accounts(_id),payee text, " +
                        "transfer_peer boolean default false, transfer_account integer references accounts(_id),method_id integer references paymentmethods(_id), " +
                        "title text not null, usages integer default 0, unique(account_id,title));");
                db.execSQL("INSERT INTO templates (_id,comment,amount,cat_id,account_id,payee,transfer_peer,transfer_account,method_id,title,usages) " +
                        "SELECT _id,comment,amount," +
                        "CASE WHEN transfer_peer THEN null ELSE CASE WHEN cat_id THEN cat_id ELSE null END END, " +
                        "account_id,payee, " +
                        "CASE WHEN transfer_peer THEN 1 ELSE 0 END, " +
                        "CASE WHEN transfer_peer THEN cat_id ELSE null END, " +
                        "CASE WHEN payment_method_id THEN payment_method_id ELSE null END, " +
                        "title,usages FROM templates_old");
                db.execSQL("ALTER TABLE categories RENAME to categories_old");
                db.execSQL("CREATE TABLE categories (_id integer primary key autoincrement, label text not null, parent_id integer references categories(_id), " +
                        "usages integer default 0, unique (label,parent_id));");
                db.execSQL("INSERT INTO categories (_id,label,parent_id,usages) " +
                        "SELECT _id,label,CASE WHEN parent_id THEN parent_id ELSE null END,usages FROM categories_old");
                db.execSQL("ALTER TABLE paymentmethods RENAME to paymentmethods_old");
                db.execSQL("CREATE TABLE paymentmethods (_id integer primary key autoincrement, label text not null, type integer check (type in (-1,0,1)) default 0);");
                db.execSQL("INSERT INTO paymentmethods (_id,label,type) SELECT _id,label,type FROM paymentmethods_old");
                db.execSQL("ALTER TABLE accounttype_paymentmethod RENAME to accounttype_paymentmethod_old");
                db.execSQL("CREATE TABLE accounttype_paymentmethod (type text not null check (type in ('CASH','BANK','CCARD','ASSET','LIABILITY')), method_id integer references paymentmethods (_id), primary key (type,method_id));");
                db.execSQL("INSERT INTO accounttype_paymentmethod (type,method_id) SELECT type,method_id FROM accounttype_paymentmethod_old");
                db.execSQL("DROP TABLE transactions_old");
                db.execSQL("DROP TABLE accounts_old");
                db.execSQL("DROP TABLE templates_old");
                db.execSQL("DROP TABLE categories_old");
                db.execSQL("DROP TABLE paymentmethods_old");
                db.execSQL("DROP TABLE accounttype_paymentmethod_old");
                //Changes to handle
                //1) Transfer account no longer stored as cat_id but in transfer_account (in transactions and templates)
                //2) parent_id for categories uses foreign key on itself, hence root categories have null instead of 0 as parent_id
                //3) catId etc now need to be null instead of 0
                //4) transactions payment_method_id renamed to method_id
            }

            if (oldVersion < 29) {
                db.execSQL("ALTER TABLE transactions add column status integer default 0");
            }

            if (oldVersion < 30) {
                db.execSQL("ALTER TABLE transactions add column parent_id integer references transactions (_id)");
                //      db.execSQL("CREATE VIEW committed AS SELECT * FROM transactions WHERE status != 2;");
                //      db.execSQL("CREATE VIEW uncommitted AS SELECT * FROM transactions WHERE status = 2;");
                ContentValues initialValues = new ContentValues();
                initialValues.put("_id", 0);
                initialValues.put("parent_id", 0);
                initialValues.put("label", "__SPLIT_TRANSACTION__");
                db.insert("categories", null, initialValues);
            }

            if (oldVersion < 31) {
                //in an alpha version distributed on Google Play, we had SPLIT_CATID as -1
                ContentValues initialValues = new ContentValues();
                initialValues.put("_id", 0);
                initialValues.put("parent_id", 0);
                db.update("categories", initialValues, "_id=-1", null);
            }

            if (oldVersion < 32) {
                db.execSQL("ALTER TABLE accounts add column grouping text not null check (grouping in " +
                        "('NONE','DAY','WEEK','MONTH','YEAR')) default 'NONE'");
            }

            if (oldVersion < 33) {
                db.execSQL("ALTER TABLE accounts add column usages integer default 0");
                db.execSQL("UPDATE accounts SET usages = (SELECT count(*) FROM transactions WHERE account_id = accounts._id AND parent_id IS null)");
            }

            if (oldVersion < 34) {
                //fix for https://github.com/mtotschnig/MyExpenses/issues/69
                db.execSQL("UPDATE transactions set date = (SELECT date from transactions parent WHERE parent._id = transactions.parent_id) WHERE parent_id IS NOT null");
            }

            if (oldVersion < 35) {
                db.execSQL("ALTER TABLE transactions add column cr_status text not null check (cr_status in ('UNRECONCILED','CLEARED','RECONCILED')) default 'UNRECONCILED'");
            }

            if (oldVersion < 36) {
                //move payee field in transactions from text to foreign key
                db.execSQL("ALTER TABLE transactions RENAME to transactions_old");
                db.execSQL("CREATE TABLE transactions (" +
                        " _id integer primary key autoincrement," +
                        " comment text, date datetime not null," +
                        " amount integer not null," +
                        " cat_id integer references categories(_id)," +
                        " account_id integer not null references accounts(_id)," +
                        " payee_id integer references payee(_id)," +
                        " transfer_peer integer references transactions(_id)," +
                        " transfer_account integer references accounts(_id)," +
                        " method_id integer references paymentmethods(_id)," +
                        " parent_id integer references transactions(_id)," +
                        " status integer default 0," +
                        " cr_status text not null check (cr_status in ('UNRECONCILED','CLEARED','RECONCILED')) default 'RECONCILED')");
                //insert all payees that are stored in transactions, but are not in payee
                db.execSQL("INSERT INTO payee (name) SELECT DISTINCT payee FROM transactions_old WHERE payee != '' AND NOT exists (SELECT 1 FROM payee WHERE name=transactions_old.payee)");
                db.execSQL("INSERT INTO transactions " +
                        "(_id,comment,date,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,parent_id,status,cr_status) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "date, " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "(SELECT _id from payee WHERE name = payee), " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "parent_id," +
                        "status," +
                        "cr_status " +
                        "FROM transactions_old");
                db.execSQL("DROP TABLE transactions_old");

                //move payee field in templates from text to foreign key
                db.execSQL("ALTER TABLE templates RENAME to templates_old");
                db.execSQL("CREATE TABLE templates (" +
                        " _id integer primary key autoincrement," +
                        " comment text," +
                        " amount integer not null," +
                        " cat_id integer references categories(_id)," +
                        " account_id integer not null references accounts(_id)," +
                        " payee_id integer references payee(_id)," +
                        " transfer_peer boolean default false," +
                        " transfer_account integer references accounts(_id)," +
                        " method_id integer references paymentmethods(_id)," +
                        " title text not null," +
                        " usages integer default 0," +
                        " unique(account_id,title));");
                //insert all payees that are stored in templates, but are not in payee
                db.execSQL("INSERT INTO payee (name) SELECT DISTINCT payee FROM templates_old WHERE payee != '' AND NOT exists (SELECT 1 FROM payee WHERE name=templates_old.payee)");
                db.execSQL("INSERT INTO templates " +
                        "(_id,comment,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,title,usages) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "(SELECT _id from payee WHERE name = payee), " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "title," +
                        "usages " +
                        "FROM templates_old");
                db.execSQL("DROP TABLE templates_old");

                db.execSQL("DROP VIEW IF EXISTS committed");
                db.execSQL("DROP VIEW IF EXISTS uncommitted");
                //for the definition of the view, it is safe to rely on the constants,
                //since we will not alter the view, but drop it, and recreate it, if needed
                //      String viewTransactions = VIEW_DEFINITION(TABLE_TRANSACTIONS);
                //      db.execSQL("CREATE VIEW transactions_committed "  + viewTransactions + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
                //      db.execSQL("CREATE VIEW transactions_uncommitted" + viewTransactions + " WHERE " + KEY_STATUS +  " = " + STATUS_UNCOMMITTED + ";");
                //      db.execSQL("CREATE VIEW transactions_all" + viewTransactions);
                //      db.execSQL("CREATE VIEW templates_all" +  VIEW_DEFINITION(TABLE_TEMPLATES));
            }

            if (oldVersion < 37) {
                db.execSQL("ALTER TABLE transactions add column number text");
                db.execSQL("ALTER TABLE paymentmethods add column is_numbered boolean default 0");
                ContentValues initialValues = new ContentValues();
                initialValues.put("is_numbered", true);
                db.update("paymentmethods", initialValues, "label = ?", new String[]{"CHEQUE"});
            }

            if (oldVersion < 38) {
                db.execSQL("ALTER TABLE templates add column plan_id integer");
                db.execSQL("ALTER TABLE templates add column plan_execution boolean default 0");
            }

            if (oldVersion < 39) {
                //      db.execSQL("CREATE VIEW transactions_extended" + VIEW_DEFINITION_EXTENDED(TABLE_TRANSACTIONS) + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
                //      db.execSQL("CREATE VIEW templates_extended" +  VIEW_DEFINITION_EXTENDED(TABLE_TEMPLATES));
                db.execSQL("CREATE TABLE currency (_id integer primary key autoincrement, code text unique not null);");
                insertCurrencies(db);
            }

            if (oldVersion < 40) {
                //added currency to extended view
                db.execSQL("DROP VIEW IF EXISTS transactions_extended");
                db.execSQL("DROP VIEW IF EXISTS templates_extended");
                //      db.execSQL("CREATE VIEW transactions_extended" + VIEW_DEFINITION_EXTENDED(TABLE_TRANSACTIONS) + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
                //      db.execSQL("CREATE VIEW templates_extended" +  VIEW_DEFINITION_EXTENDED(TABLE_TEMPLATES));
            }

            if (oldVersion < 41) {
                db.execSQL("CREATE TABLE planinstance_transaction " +
                        "(template_id integer references templates(_id), " +
                        "instance_id integer, " +
                        "transaction_id integer references transactions(_id), " +
                        "primary key (instance_id,transaction_id));");
            }

            if (oldVersion < 42) {
                //migrate date field to unix time stamp (UTC)
                db.execSQL("ALTER TABLE transactions RENAME to transactions_old");
                db.execSQL("CREATE TABLE transactions (" +
                        " _id integer primary key autoincrement," +
                        " comment text, date datetime not null," +
                        " amount integer not null," +
                        " cat_id integer references categories(_id)," +
                        " account_id integer not null references accounts(_id)," +
                        " payee_id integer references payee(_id)," +
                        " transfer_peer integer references transactions(_id)," +
                        " transfer_account integer references accounts(_id)," +
                        " method_id integer references paymentmethods(_id)," +
                        " parent_id integer references transactions(_id)," +
                        " status integer default 0," +
                        " cr_status text not null check (cr_status in ('UNRECONCILED','CLEARED','RECONCILED')) default 'RECONCILED'," +
                        " number text)");
                db.execSQL("INSERT INTO transactions " +
                        "(_id,comment,date,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,parent_id,status,cr_status,number) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "strftime('%s',date,'utc'), " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "payee_id, " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "parent_id," +
                        "status," +
                        "cr_status, " +
                        "number " +
                        "FROM transactions_old");
                db.execSQL("DROP TABLE transactions_old");
            }

            if (oldVersion < 43) {
                db.execSQL("UPDATE accounts set currency = 'ZMW' WHERE currency = 'ZMK'");
                db.execSQL("UPDATE currency set code = 'ZMW' WHERE code = 'ZMK'");
            }

            if (oldVersion < 44) {
                //add ON DELETE CASCADE
                //accounts table sort_key column
                db.execSQL("ALTER TABLE planinstance_transaction RENAME to planinstance_transaction_old");
                db.execSQL("CREATE TABLE planinstance_transaction " +
                        "(template_id integer references templates(_id) ON DELETE CASCADE, " +
                        "instance_id integer, " +
                        "transaction_id integer references transactions(_id) ON DELETE CASCADE, " +
                        "primary key (instance_id,transaction_id));");
                db.execSQL("INSERT INTO planinstance_transaction " +
                        "(template_id,instance_id,transaction_id)" +
                        "SELECT " +
                        "template_id,instance_id,transaction_id FROM planinstance_transaction_old");
                db.execSQL("DROP TABLE planinstance_transaction_old");
                db.execSQL("ALTER TABLE transactions RENAME to transactions_old");
                db.execSQL("CREATE TABLE transactions (" +
                        " _id integer primary key autoincrement," +
                        " comment text, date datetime not null," +
                        " amount integer not null," +
                        " cat_id integer references categories(_id)," +
                        " account_id integer not null references accounts(_id) ON DELETE CASCADE," +
                        " payee_id integer references payee(_id)," +
                        " transfer_peer integer references transactions(_id)," +
                        " transfer_account integer references accounts(_id)," +
                        " method_id integer references paymentmethods(_id)," +
                        " parent_id integer references transactions(_id) ON DELETE CASCADE," +
                        " status integer default 0," +
                        " cr_status text not null check (cr_status in ('UNRECONCILED','CLEARED','RECONCILED')) default 'RECONCILED'," +
                        " number text)");
                db.execSQL("INSERT INTO transactions " +
                        "(_id,comment,date,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,parent_id,status,cr_status,number) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "date, " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "payee_id, " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "parent_id," +
                        "status," +
                        "cr_status, " +
                        "number " +
                        "FROM transactions_old");
                db.execSQL("DROP TABLE transactions_old");
                db.execSQL("ALTER TABLE templates RENAME to templates_old");
                db.execSQL("CREATE TABLE templates (" +
                        " _id integer primary key autoincrement," +
                        " comment text," +
                        " amount integer not null," +
                        " cat_id integer references categories(_id)," +
                        " account_id integer not null references accounts(_id) ON DELETE CASCADE," +
                        " payee_id integer references payee(_id)," +
                        " transfer_peer boolean default 0," +
                        " transfer_account integer references accounts(_id) ON DELETE CASCADE," +
                        " method_id integer references paymentmethods(_id)," +
                        " title text not null," +
                        " usages integer default 0," +
                        " plan_id integer, " +
                        " plan_execution boolean default 0, " +
                        " unique(account_id,title));");
                db.execSQL("INSERT INTO templates " +
                        "(_id,comment,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,title,usages,plan_id,plan_execution) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "payee_id, " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "title," +
                        "usages, " +
                        "plan_id, " +
                        "plan_execution " +
                        "FROM templates_old");
                db.execSQL("ALTER TABLE accounts add column sort_key integer");
            }

            if (oldVersion < 45) {
                db.execSQL("ALTER TABLE accounts add column exclude_from_totals boolean default 0");
                //added  to extended view
                db.execSQL("DROP VIEW IF EXISTS transactions_extended");
                db.execSQL("DROP VIEW IF EXISTS templates_extended");
                //      db.execSQL("CREATE VIEW transactions_extended" + VIEW_DEFINITION_EXTENDED(TABLE_TRANSACTIONS) + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
                //      db.execSQL("CREATE VIEW templates_extended" +  VIEW_DEFINITION_EXTENDED(TABLE_TEMPLATES));
            }

            if (oldVersion < 46) {
                db.execSQL("ALTER TABLE payee add column name_normalized text");
                Cursor c = db.query("payee", new String[]{"_id", "name"}, null, null, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        ContentValues v = new ContentValues();
                        while (c.getPosition() < c.getCount()) {
                            v.put("name_normalized", Utils.normalize(c.getString(1)));
                            db.update("payee", v, "_id = " + c.getLong(0), null);
                            c.moveToNext();
                        }
                    }
                    c.close();
                }
            }

            if (oldVersion < 47) {
                db.execSQL("ALTER TABLE templates add column uuid text");
                db.execSQL(EVENT_CACHE_CREATE);
            }

            if (oldVersion < 48) {
                //added method_label to extended view
                //do not comment out, since it is needed by the uuid update
                refreshViews(db);
                //need to inline to protect against later renames

                if (oldVersion < 47) {
                    String[] projection = new String[]{
                            "templates._id",
                            "amount",
                            "comment",
                            "cat_id",
                            "CASE WHEN " +
                                    "  " + "transfer_peer" + " " +
                                    " THEN " +
                                    "  (SELECT " + "label" + " FROM " + "accounts" + " WHERE " + "_id" + " = " + "transfer_account" + ") " +
                                    " ELSE " +
                                    " CASE WHEN " +
                                    " (SELECT " + "parent_id" + " FROM " + "categories" + " WHERE " + "_id" + " = " + "cat_id" + ") " +
                                    " THEN " +
                                    " (SELECT " + "label" + " FROM " + "categories" + " WHERE " + "_id" + " = " +
                                    " (SELECT " + "parent_id" + " FROM " + "categories" + " WHERE " + "_id" + " = " + "cat_id" + ")) " +
                                    "  || ' : ' || " +
                                    " (SELECT " + "label" + " FROM " + "categories" + " WHERE " + "_id" + " = " + "cat_id" + ") " +
                                    " ELSE" +
                                    " (SELECT " + "label" + " FROM " + "categories" + " WHERE " + "_id" + " = " + "cat_id" + ") " +
                                    " END " +
                                    " END AS  " + "label",
                            "name",
                            "transfer_peer",
                            "transfer_account",
                            "account_id",
                            "method_id",
                            "paymentmethods.label AS method_label",
                            "title",
                            "plan_id",
                            "plan_execution",
                            "uuid",
                            "currency"
                    };
                    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                    qb.setTables("templates LEFT JOIN payee ON payee_id = payee._id" +
                            " LEFT JOIN accounts ON account_id = accounts._id" +
                            " LEFT JOIN paymentmethods ON method_id = paymentmethods._id");
                    Cursor c = qb.query(db, projection, null, null, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            ContentValues templateValues = new ContentValues(),
                                    eventValues = new ContentValues();
                            String planCalendarId = MyApplication.getInstance().checkPlanner();
                            while (c.getPosition() < c.getCount()) {
                                Template t = new Template(c);
                                templateValues.put(DatabaseConstants.KEY_UUID, t.getUuid());
                                long templateId = c.getLong(c.getColumnIndex("_id"));
                                long planId = c.getLong(c.getColumnIndex("plan_id"));
                                eventValues.put(Events.DESCRIPTION, t.compileDescription(mCtx));
                                db.update("templates", templateValues, "_id = " + templateId, null);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                    try {
                                        mCtx.getContentResolver().update(Events.CONTENT_URI,
                                                eventValues, Events._ID + "= ? AND " + Events.CALENDAR_ID + " = ?",
                                                new String[]{String.valueOf(planId), planCalendarId});
                                    } catch (Exception e) {
                                        //fails with IllegalArgumentException on 2.x devices,
                                        //since the same uri works for inserting and querying
                                        //but also on HUAWEI Y530-U00 with 4.3
                                        //probably SecurityException could arise here
                                    }
                                }
                                c.moveToNext();
                            }
                        }
                        c.close();
                    }
                }
            }

            if (oldVersion < 49) {
                //forgotten to drop in previous upgrade
                db.execSQL("DROP TABLE IF EXISTS templates_old");
            }

            if (oldVersion < 50) {
                db.execSQL("ALTER TABLE transactions add column picture_id text");
                db.execSQL("DROP TABLE IF EXISTS feature_used");
            }

            if (oldVersion < 51) {
                File pictureDir = Utils.getPictureDir(false);
                //fallback if not mounted
                if (pictureDir == null) {
                    pictureDir = new File(
                            Environment.getExternalStorageDirectory().getPath() +
                                    "/Android/data/" + MyApplication.getInstance().getPackageName() + "/files",
                            Environment.DIRECTORY_PICTURES);
                }
                if (!pictureDir.exists()) {
                    AcraHelper.report(new Exception("Unable to calculate pictureDir during upgrade"));
                }
                //if pictureDir does not exist, we use its URI nonetheless, in order to have the data around
                //for potential trouble handling
                String prefix = Uri.fromFile(pictureDir).toString() + "/";
                String postfix = ".jpg";
                //if picture_id concat expression will also be null
                db.execSQL("UPDATE transactions set picture_id = '" + prefix + "'||picture_id||'" + postfix + "'");

                db.execSQL("CREATE TABLE stale_uris ( picture_id text);");
                db.execSQL("CREATE TRIGGER cache_stale_uri BEFORE DELETE ON transactions WHEN old.picture_id NOT NULL "
                        + " BEGIN INSERT INTO stale_uris VALUES (old.picture_id); END");
            }

            if (oldVersion < 52) {
                db.execSQL("CREATE INDEX transactions_cat_id_index on transactions(cat_id)");
                db.execSQL("CREATE INDEX templates_cat_id_index on templates(cat_id)");
            }

            if (oldVersion < 53) {
                //add VOID status
                db.execSQL("ALTER TABLE transactions RENAME to transactions_old");
                db.execSQL("CREATE TABLE " + "transactions" + "( "
                        + "_id" + " integer primary key autoincrement, "
                        + "comment" + " text, "
                        + "date" + " datetime not null, "
                        + "amount" + " integer not null, "
                        + "cat_id" + " integer references " + "categories" + "(" + "_id" + "), "
                        + "account_id" + " integer not null references " + "accounts" + "(" + "_id" + ") ON DELETE CASCADE,"
                        + "payee_id" + " integer references " + "payee" + "(" + "_id" + "), "
                        + "transfer_peer" + " integer references " + "transactions" + "(" + "_id" + "), "
                        + "transfer_account" + " integer references " + "accounts" + "(" + "_id" + "),"
                        + "method_id" + " integer references " + "paymentmethods" + "(" + "_id" + "),"
                        + "parent_id" + " integer references " + "transactions" + "(" + "_id" + ") ON DELETE CASCADE, "
                        + "status" + " integer default 0, "
                        + "cr_status" + " text not null check (" + "cr_status" + " in ('UNRECONCILED','CLEARED','RECONCILED','VOID')) default 'RECONCILED', "
                        + "number" + " text, "
                        + "picture_id" + " text);");
                db.execSQL("INSERT INTO transactions " +
                        "(_id,comment,date,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,parent_id,status,cr_status,number,picture_id) " +
                        "SELECT " +
                        "_id, " +
                        "comment, " +
                        "date, " +
                        "amount, " +
                        "cat_id, " +
                        "account_id, " +
                        "payee_id, " +
                        "transfer_peer, " +
                        "transfer_account, " +
                        "method_id," +
                        "parent_id," +
                        "status," +
                        "cr_status, " +
                        "number, " +
                        "picture_id " +
                        "FROM transactions_old");
                db.execSQL("DROP TABLE transactions_old");
                db.execSQL("CREATE TRIGGER cache_stale_uri BEFORE DELETE ON transactions WHEN old.picture_id NOT NULL "
                        + " BEGIN INSERT INTO stale_uris VALUES (old.picture_id); END");
                db.execSQL("CREATE INDEX transactions_cat_id_index on transactions(cat_id)");
            }

            if (oldVersion < 54) {
                db.execSQL("DROP TRIGGER cache_stale_uri");
                db.execSQL("CREATE TRIGGER cache_stale_uri " +
                        "AFTER DELETE ON " + "transactions" + " " +
                        "WHEN old." + "picture_id" + " NOT NULL " +
                        "AND NOT EXISTS " +
                        "(SELECT 1 FROM " + "transactions" + " " +
                        "WHERE " + "picture_id" + " = old." + "picture_id" + ") " +
                        "BEGIN INSERT INTO " + "stale_uris" + " VALUES (old." + "picture_id" + "); END");
                //all Accounts with old default color are updated to the new one
                db.execSQL(String.format(Locale.US, "UPDATE accounts set color = %d WHERE color = %d", 0xff009688, 0xff99CC00));
            }

            if (oldVersion < 55) {
                db.execSQL("ALTER TABLE categories add column label_normalized text");
                Cursor c = db.query("categories", new String[]{"_id", "label"}, null, null, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        ContentValues v = new ContentValues();
                        while (c.getPosition() < c.getCount()) {
                            v.put("label_normalized", Utils.normalize(c.getString(1)));
                            db.update("categories", v, "_id = " + c.getLong(0), null);
                            c.moveToNext();
                        }
                    }
                    c.close();
                }
            }

            if (oldVersion < 56) {
                db.execSQL("ALTER TABLE templates add column last_used datetime");
                db.execSQL("ALTER TABLE categories add column last_used datetime");
                db.execSQL("ALTER TABLE accounts add column last_used datetime");
                db.execSQL("CREATE TRIGGER sort_key_default AFTER INSERT ON accounts " +
                        "BEGIN UPDATE accounts SET sort_key = (SELECT coalesce(max(sort_key),0) FROM accounts) + 1 " +
                        "WHERE _id = NEW._id; END");
                //The sort key could be set by user in previous versions, now it is handled internally
                Cursor c = db.query("accounts", new String[]{"_id", "sort_key"}, null, null, null, null, "sort_key ASC");
                boolean hasAccountSortKeySet = false;
                if (c != null) {
                    if (c.moveToFirst()) {
                        ContentValues v = new ContentValues();
                        while (c.getPosition() < c.getCount()) {
                            v.put("sort_key", c.getPosition() + 1);
                            db.update("accounts", v, "_id = ?", new String[]{c.getString(0)});
                            if (c.getInt(1) != 0) hasAccountSortKeySet = true;
                            c.moveToNext();
                        }
                    }
                    c.close();
                }
                String legacy = PrefKey.SORT_ORDER_LEGACY.getString("USAGES");
                PrefKey.SORT_ORDER_TEMPLATES.putString(legacy);
                PrefKey.SORT_ORDER_CATEGORIES.putString(legacy);
                PrefKey.SORT_ORDER_ACCOUNTS.putString(hasAccountSortKeySet ? "CUSTOM" : legacy);
                PrefKey.SORT_ORDER_LEGACY.remove();
            }
        } catch (SQLException e) {
            throw Utils.hasApiLevel(Build.VERSION_CODES.JELLY_BEAN) ?
                    new SQLiteUpgradeFailedException("Database upgrade failed", e) :
                    e;
        }

        if (oldVersion < 57) {
            //fix custom app uris
            if (ContextCompat.checkSelfPermission(mCtx,
                    Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                Cursor c = db.query("templates", new String[]{"_id", "plan_id"}, "plan_id IS NOT null", null, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        while (!c.isAfterLast()) {
                            Plan.updateCustomAppUri(c.getLong(1), Template.buildCustomAppUri(c.getLong(0)));
                            c.moveToNext();
                        }
                    }
                    c.close();
                }
            }

            //Drop unique constraint on templates

            db.execSQL("ALTER TABLE templates RENAME to templates_old");
            db.execSQL("CREATE TABLE templates (" +
                    " _id integer primary key autoincrement," +
                    " comment text," +
                    " amount integer not null," +
                    " cat_id integer references categories(_id)," +
                    " account_id integer not null references accounts(_id) ON DELETE CASCADE," +
                    " payee_id integer references payee(_id)," +
                    " transfer_peer boolean default 0," +
                    " transfer_account integer references accounts(_id) ON DELETE CASCADE," +
                    " method_id integer references paymentmethods(_id)," +
                    " title text not null," +
                    " usages integer default 0," +
                    " plan_id integer, " +
                    " plan_execution boolean default 0, " +
                    " uuid text, " +
                    " last_used datetime);");
            db.execSQL("INSERT INTO templates " +
                    "(_id,comment,amount,cat_id,account_id,payee_id,transfer_peer,transfer_account,method_id,title,usages,plan_id,plan_execution,uuid,last_used) " +
                    "SELECT " +
                    "_id, " +
                    "comment, " +
                    "amount, " +
                    "cat_id, " +
                    "account_id, " +
                    "payee_id, " +
                    "transfer_peer, " +
                    "transfer_account, " +
                    "method_id," +
                    "title," +
                    "usages, " +
                    "plan_id, " +
                    "plan_execution, uuid, last_used " +
                    "FROM templates_old");
            db.execSQL("DROP TABLE templates_old");
            //Recreate changed views
            refreshViews(db);
        }

        if (oldVersion < 58) {
            //cache fraction digits
            Cursor c = db.rawQuery("SELECT distinct currency from accounts", null);
            if (c != null) {
                if (c.moveToFirst()) {
                    while (!c.isAfterLast()) {
                        Money.ensureFractionDigitsAreCached(Utils.getSaveInstance(c.getString(0)));
                        c.moveToNext();
                    }
                }
                c.close();
            }
        }
    }

    private void refreshViews(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS transactions_extended");
        db.execSQL("DROP VIEW IF EXISTS templates_extended");
        db.execSQL("DROP VIEW IF EXISTS transactions_committed");
        db.execSQL("DROP VIEW IF EXISTS transactions_uncommitted");
        db.execSQL("DROP VIEW IF EXISTS transactions_all");
        db.execSQL("DROP VIEW IF EXISTS templates_all");
        db.execSQL("CREATE VIEW transactions_extended" + buildViewDefinitionExtended(TABLE_TRANSACTIONS) + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW templates_extended" + buildViewDefinitionExtended(TABLE_TEMPLATES));
        String viewTransactions = buildViewDefinition(TABLE_TRANSACTIONS);
        db.execSQL("CREATE VIEW transactions_committed " + viewTransactions + " WHERE " + KEY_STATUS + " != " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW transactions_uncommitted" + viewTransactions + " WHERE " + KEY_STATUS + " = " + STATUS_UNCOMMITTED + ";");
        db.execSQL("CREATE VIEW transactions_all" + viewTransactions);
        db.execSQL("CREATE VIEW templates_all" + buildViewDefinition(TABLE_TEMPLATES));
    }

    @Override
    public final void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteDowngradeFailedException();
    }

    public static class SQLiteDowngradeFailedException extends SQLiteException {
    }

    public static class SQLiteUpgradeFailedException extends SQLiteException {
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public SQLiteUpgradeFailedException(String error, Throwable cause) {
            super(error, cause);
        }
    }

}
