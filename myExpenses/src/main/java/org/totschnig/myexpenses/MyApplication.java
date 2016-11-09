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

package org.totschnig.myexpenses;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.multidex.MultiDex;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.calendar.CalendarContractCompat;
import com.android.calendar.CalendarContractCompat.Calendars;
import com.android.calendar.CalendarContractCompat.Events;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.totschnig.myexpenses.di.AppComponent;
import org.totschnig.myexpenses.di.AppModule;
import org.totschnig.myexpenses.di.DaggerAppComponent;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.preference.SharedPreferencesCompat;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.service.DailyAutoBackupScheduler;
import org.totschnig.myexpenses.service.PlanExecutor;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.LicenceHandler;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.widget.AbstractWidget;
import org.totschnig.myexpenses.widget.AccountWidget;
import org.totschnig.myexpenses.widget.TemplateWidget;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

public class MyApplication extends Application implements
    OnSharedPreferenceChangeListener {

  public AppComponent getAppComponent() {
    return appComponent;
  }

  private AppComponent appComponent;
  @Inject
  LicenceHandler licenceHandler;
  @Inject @Nullable
  ACRAConfiguration acraConfiguration;
  private static boolean instrumentationTest = false;
  private static String testId;
  public static final String PLANNER_CALENDAR_NAME = "MyExpensesPlanner";
  public static final String PLANNER_ACCOUNT_NAME = "Local Calendar";
  public static final String INVALID_CALENDAR_ID = "-1";
  private SharedPreferences mSettings;
  private static MyApplication mSelf;

  public static final String BACKUP_DB_FILE_NAME = "BACKUP";
  public static final String BACKUP_PREF_FILE_NAME = "BACKUP_PREF";

  public static final String KEY_NOTIFICATION_ID = "notification_id";
  public static final String KEY_OPERATION_TYPE = "operationType";

  public static final String CONTRIB_SECRET = "RANDOM_SECRET";
  public static final String CALENDAR_FULL_PATH_PROJECTION = "ifnull("
      + Calendars.ACCOUNT_NAME + ",'') || '/' ||" + "ifnull("
      + Calendars.ACCOUNT_TYPE + ",'') || '/' ||" + "ifnull(" + Calendars.NAME
      + ",'')";

  private long mLastPause = 0;
  public final static String TAG = "MyExpenses";

  private boolean isLocked;

  public static void setInstrumentationTest(boolean instrumentationTest) {
    MyApplication.instrumentationTest = instrumentationTest;
  }

  public static boolean isInstrumentationTest() {
    return instrumentationTest;
  }

  public boolean isLocked() {
    return isLocked;
  }

  public void setLocked(boolean isLocked) {
    this.isLocked = isLocked;
  }

  public static final String FEEDBACK_EMAIL = "support@myexpenses.mobi";
  // public static int BACKDOOR_KEY = KeyEvent.KEYCODE_CAMERA;

  /**
   * we cache value of planner calendar id, so that we can handle changes in
   * value
   */
  private String mPlannerCalendarId = "-1";
  /**
   * we store the systemLocale if the user wants to come back to it after having
   * tried a different locale;
   */
  private Locale systemLocale = Locale.getDefault();

  @Override
  public void onCreate() {
    super.onCreate();
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    //Maybe prevents occasional crashes on Gingerbread
    //https://code.google.com/p/android/issues/detail?id=81083
    try {Class.forName("android.os.AsyncTask");} catch(Throwable ignore) {}
    mSelf = this;
    if (!ACRA.isACRASenderServiceProcess()) {
      // sets up mSettings
      getSettings().registerOnSharedPreferenceChangeListener(this);
      licenceHandler.init(this);
      initPlannerInternal(60000);
      registerWidgetObservers();
    }
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    MultiDex.install(this);
    appComponent = DaggerAppComponent.builder()
        .appModule(new AppModule(this)).build();
    appComponent.inject(this);
    if (acraConfiguration != null) {
      ACRA.init(this, acraConfiguration);
    }

  }

  private void registerWidgetObservers() {
    final ContentResolver r = getContentResolver();
    WidgetObserver mTemplateObserver = new WidgetObserver(TemplateWidget.class);
    for (Uri uri : TemplateWidget.OBSERVED_URIS) {
      r.registerContentObserver(uri, true, mTemplateObserver);
    }
    WidgetObserver mAccountObserver = new WidgetObserver(AccountWidget.class);
    for (Uri uri : AccountWidget.OBSERVED_URIS) {
      r.registerContentObserver(uri, true, mAccountObserver);
    }
  }

  public static MyApplication getInstance() {
    return mSelf;
  }

  public SharedPreferences getSettings() {
    if (mSettings == null) {
      mSettings = instrumentationTest ? getSharedPreferences(getTestId(), Context.MODE_PRIVATE) :
          PreferenceManager.getDefaultSharedPreferences(this);
    }
    return mSettings;
  }

  public static String getTestId() {
    if (testId == null) {
      testId = UUID.randomUUID().toString();
    }
    return testId;
  }

  public static void cleanUpAfterTest() {
    mSelf.deleteDatabase(testId);
    mSelf.getSettings().edit().clear().commit();
    new File(new File(mSelf.getFilesDir().getParentFile().getPath() + "/shared_prefs/"),
        testId + ".xml").delete();
  }

  public static int getThemeId() {
    return getThemeId("");
  }

  public static int getThemeIdEditDialog() {
    return getThemeId("EditDialog");
  }

  public static int getThemeIdTranslucent() {
    return getThemeId("Translucent");
  }

  public LicenceHandler getLicenceHandler() {
    return licenceHandler;
  }

  public enum ThemeType {
    dark, light
  }

  public static ThemeType getThemeType() {
    try {
      return ThemeType.valueOf(PrefKey.UI_THEME_KEY.getString(ThemeType.dark.name()));
    } catch (IllegalArgumentException e) {
      return ThemeType.dark;
    }
  }

  private static int getThemeId(String subStyle) {
    int fontScale;
    try {
      fontScale = PrefKey.UI_FONTSIZE.getInt(0);
    } catch (Exception e) {
      // in a previous version, the same key was holding an integer
      fontScale = 0;
      PrefKey.UI_FONTSIZE.remove();
    }
    String style = getThemeType() == ThemeType.light ? "ThemeLight" : "ThemeDark";
    if (!TextUtils.isEmpty(subStyle)) {
      style += "." + subStyle;
    }
    String resolve = style;
    if (fontScale > 0 && fontScale < 4) {
      resolve = style + ".s" + fontScale;
    }
    int resId = mSelf.getResources().getIdentifier(resolve, "style", mSelf.getPackageName());
    if (resId==0) {
      //try style without font scaling as fallback
      resId = mSelf.getResources().getIdentifier(style, "style", mSelf.getPackageName());
      if (resId==0) throw new RuntimeException(style + " is not defined");
    }
    return resId;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    systemLocale = newConfig.locale;
  }

  public void setLanguage() {
    String language = PrefKey.UI_LANGUAGE.getString("default");
    Locale l;
    if (language.equals("default")) {
      l = systemLocale;
    } else if (language.contains("-")) {
      String[] parts = language.split("-");
      l = new Locale(parts[0], parts[1]);
    } else {
      l = new Locale(language);
    }
    setLanguage(l);
  }

  public void setLanguage(Locale locale) {
    if (!Locale.getDefault().equals(locale)) {
      Locale.setDefault(locale);
      Configuration config = new Configuration();
      config.locale = locale;
      config.fontScale = getResources().getConfiguration().fontScale;
      getResources().updateConfiguration(config,
          getResources().getDisplayMetrics());
    }
  }

  public static DocumentFile requireBackupFile(@NonNull DocumentFile appDir) {
    return Utils.timeStampedFile(appDir, "backup", "application/zip", false);
  }

  public static File getBackupDbFile(File backupDir) {
    return new File(backupDir, BACKUP_DB_FILE_NAME);
  }

  public static File getBackupPrefFile(File backupDir) {
    return new File(backupDir, BACKUP_PREF_FILE_NAME);
  }

  public long getLastPause() {
    return mLastPause;
  }

  public void setLastPause(Activity ctx) {
    if (!isLocked()) {
      // if we are dealing with an activity called from widget that allows to
      // bypass password protection, we do not reset last pause
      // otherwise user could gain unprotected access to the app
      boolean isDataEntryEnabled = PrefKey.PROTECTION_ENABLE_DATA_ENTRY_FROM_WIDGET
          .getBoolean(false);
      boolean isStartFromWidget = ctx.getIntent().getBooleanExtra(
          AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, false);
      if (!isDataEntryEnabled || !isStartFromWidget) {
        this.mLastPause = System.nanoTime();
      }
    }
  }

  public void resetLastPause() {
    this.mLastPause = 0;
  }

  /**
   * @param ctx
   *          Activity that should be password protected, can be null if called
   *          from widget provider
   * @return true if password protection is set, and we have paused for at least
   *         {@link PrefKey#PROTECTION_DELAY_SECONDS} seconds unless we are called
   *         from widget or from an activity called from widget and passwordless
   *         data entry from widget is allowed sets isLocked as a side effect
   */
  public boolean shouldLock(Activity ctx) {
    boolean isStartFromWidget = ctx == null
        || ctx.getIntent().getBooleanExtra(
            AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, false);
    boolean isProtected = isProtected();
    long lastPause = getLastPause();
    boolean isPostDelay = System.nanoTime() - lastPause > (PrefKey.PROTECTION_DELAY_SECONDS
        .getInt(15) * 1000000000L);
    boolean isDataEntryEnabled = PrefKey.PROTECTION_ENABLE_DATA_ENTRY_FROM_WIDGET
        .getBoolean(false);
    if (isProtected && isPostDelay
        && (!isDataEntryEnabled || !isStartFromWidget)) {
      setLocked(true);
      return true;
    }
    return false;
  }

  public boolean isProtected() {
    return PrefKey.PERFORM_PROTECTION.getBoolean(false);
  }

  /**
   * @param calendarId id of calendar in system calendar content provider
   * @return verifies if the passed in calendarid exists and is the one stored
   *         in {@link PrefKey#PLANNER_CALENDAR_PATH}
   */
  private boolean checkPlannerInternal(String calendarId) {
    ContentResolver cr = getContentResolver();
    Cursor c = cr.query(Calendars.CONTENT_URI,
        new String[] { CALENDAR_FULL_PATH_PROJECTION + " AS path" },
        Calendars._ID + " = ?", new String[] { calendarId }, null);
    boolean result = true;
    if (c == null)
      return false;
    else {
      if (c.moveToFirst()) {
        String found = DbUtils.getString(c, 0);
        String expected = PrefKey.PLANNER_CALENDAR_PATH.getString("");
        if (!found.equals(expected)) {
          Log.w(TAG, String.format(
              "found calendar, but path did not match; expected %s ; got %s",
              expected, found));
          result = false;
        }
      } else {
        Log.i(TAG, "configured calendar has been deleted: " + calendarId);
        result = false;
      }
      c.close();
      return result;
    }
  }

  public String checkPlanner() {
    mPlannerCalendarId = PrefKey.PLANNER_CALENDAR_ID.getString(INVALID_CALENDAR_ID);
    if (!mPlannerCalendarId.equals(INVALID_CALENDAR_ID) && !checkPlannerInternal(mPlannerCalendarId)) {
      removePlanner();
      return
          INVALID_CALENDAR_ID;
    }
    return mPlannerCalendarId;
  }

  public void removePlanner() {
    SharedPreferencesCompat.apply(mSettings.edit()
        .remove(PrefKey.PLANNER_CALENDAR_ID.getKey())
        .remove(PrefKey.PLANNER_CALENDAR_PATH.getKey())
        .remove(PrefKey.PLANNER_LAST_EXECUTION_TIMESTAMP.getKey()));
  }

  /**
   * check if we already have a calendar in Account {@link #PLANNER_ACCOUNT_NAME}
   * of type {@link CalendarContractCompat#ACCOUNT_TYPE_LOCAL} with name
   * {@link #PLANNER_ACCOUNT_NAME} if yes use it, otherwise create it
   * 
   * @return true if we have configured a useable calendar
   * @param persistToSharedPref if true id of the created calendar is stored in preferences
   */
  public String createPlanner(boolean persistToSharedPref) {
    Uri.Builder builder = Calendars.CONTENT_URI.buildUpon();
    String plannerCalendarId;
    builder.appendQueryParameter(Calendars.ACCOUNT_NAME, PLANNER_ACCOUNT_NAME);
    builder.appendQueryParameter(Calendars.ACCOUNT_TYPE,
        CalendarContractCompat.ACCOUNT_TYPE_LOCAL);
    builder.appendQueryParameter(CalendarContractCompat.CALLER_IS_SYNCADAPTER,
        "true");
    Uri calendarUri = builder.build();
    Cursor c = getContentResolver().query(calendarUri,
        new String[] { Calendars._ID }, Calendars.NAME + " = ?",
        new String[] { PLANNER_CALENDAR_NAME }, null);
    if (c == null) {
      AcraHelper.report(new Exception(
              "Searching for planner calendar failed, Calendar app not installed?"));
      return INVALID_CALENDAR_ID;
    }
    if (c.moveToFirst()) {
      plannerCalendarId = String.valueOf(c.getLong(0));
      Log.i(TAG, "found a preexisting calendar: " + plannerCalendarId);
      c.close();
    } else {
      c.close();
      ContentValues values = new ContentValues();
      values.put(Calendars.ACCOUNT_NAME, PLANNER_ACCOUNT_NAME);
      values.put(Calendars.ACCOUNT_TYPE,
          CalendarContractCompat.ACCOUNT_TYPE_LOCAL);
      values.put(Calendars.NAME, PLANNER_CALENDAR_NAME);
      values.put(Calendars.CALENDAR_DISPLAY_NAME,
          getString(R.string.plan_calendar_name));
      values.put(Calendars.CALENDAR_COLOR,
          getResources().getColor(R.color.appDefault));
      values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
      values.put(Calendars.OWNER_ACCOUNT, "private");
      Uri uri;
      try {
        uri = getContentResolver().insert(calendarUri, values);
      } catch (IllegalArgumentException e) {
        AcraHelper.report(e);
        return INVALID_CALENDAR_ID;
      }
      if (uri == null) {
        AcraHelper.report(new Exception(
            "Inserting planner calendar failed, uri is null"));
        return INVALID_CALENDAR_ID;
      }
      plannerCalendarId = uri.getLastPathSegment();
      if (plannerCalendarId == null || plannerCalendarId.equals("0")) {
        AcraHelper.report(new Exception(String.format(Locale.US,
                "Inserting planner calendar failed, last path segment is %s", plannerCalendarId)));
        return INVALID_CALENDAR_ID;
      }
      Log.i(TAG, "successfully set up new calendar: " + plannerCalendarId);
    }
    if (persistToSharedPref) {
      // onSharedPreferenceChanged should now trigger initPlanner
      PrefKey.PLANNER_CALENDAR_ID.putString(plannerCalendarId);
    }
    return plannerCalendarId;
  }

  /**
   * call PlanExecutor, which will 1) set up the planner calendar 2) execute
   * plans 3) reschedule execution through alarm
   */
  public void initPlanner() {
    initPlannerInternal(0);
  }

  private void initPlannerInternal(long delay) {
    Log.i(TAG, "initPlanner called, setting plan executor to run with delay " + delay);
    PlanExecutor.setAlarm(this, System.currentTimeMillis() + delay);
  }

  public static String[] buildEventProjection() {
    String[] projection = new String[android.os.Build.VERSION.SDK_INT >= 16 ? 10
        : 8];
    projection[0] = Events.DTSTART;
    projection[1] = Events.DTEND;
    projection[2] = Events.RRULE;
    projection[3] = Events.TITLE;
    projection[4] = Events.ALL_DAY;
    projection[5] = Events.EVENT_TIMEZONE;
    projection[6] = Events.DURATION;
    projection[7] = Events.DESCRIPTION;
    if (android.os.Build.VERSION.SDK_INT >= 16) {
      projection[8] = Events.CUSTOM_APP_PACKAGE;
      projection[9] = Events.CUSTOM_APP_URI;
    }
    return projection;
  }

  /**
   * @param eventCursor
   *          must have been populated with a projection built by
   *          {@link #buildEventProjection()}
   * @param eventValues ContentValues where the extracted data is copied to
   */
  public static void copyEventData(Cursor eventCursor, ContentValues eventValues) {
    eventValues.put(Events.DTSTART, DbUtils.getLongOrNull(eventCursor, 0));
    //older Android versions have populated both dtend and duration
    //restoring those on newer versions leads to IllegalArgumentexception
    Long dtEnd = DbUtils.getLongOrNull(eventCursor, 1);
    String dtDuration = dtEnd != null ? null : eventCursor.getString(6);
    eventValues.put(Events.DTEND, dtEnd);
    eventValues.put(Events.RRULE, eventCursor.getString(2));
    eventValues.put(Events.TITLE, eventCursor.getString(3));
    eventValues.put(Events.ALL_DAY, eventCursor.getInt(4));
    eventValues.put(Events.EVENT_TIMEZONE, eventCursor.getString(5));
    eventValues.put(Events.DURATION, dtDuration);
    eventValues.put(Events.DESCRIPTION, eventCursor.getString(7));
    if (android.os.Build.VERSION.SDK_INT >= 16) {
      eventValues.put(Events.CUSTOM_APP_PACKAGE, eventCursor.getString(8));
      eventValues.put(Events.CUSTOM_APP_URI, eventCursor.getString(9));
    }
  }

  private boolean insertEventAndUpdatePlan(ContentValues eventValues,
      long templateId) {
    Uri uri = getContentResolver().insert(Events.CONTENT_URI, eventValues);
    long planId = ContentUris.parseId(uri);
    Log.i(TAG, "event copied with new id: " + planId);
    ContentValues planValues = new ContentValues();
    planValues.put(DatabaseConstants.KEY_PLANID, planId);
    int updated = getContentResolver().update(
        ContentUris.withAppendedId(Template.CONTENT_URI, templateId),
        planValues, null, null);
    return updated > 0;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    if (!key.equals(PrefKey.AUTO_BACKUP_DIRTY.getKey())) {
      markDataDirty();
    }
    // TODO: move to TaskExecutionFragment
    if (!key.equals(PrefKey.PLANNER_CALENDAR_ID.getKey())) {
      return;
    }
    String oldValue = mPlannerCalendarId;
    boolean safeToMovePlans = true;
    String newValue = sharedPreferences.getString(
        PrefKey.PLANNER_CALENDAR_ID.getKey(), "-1");
    if (oldValue.equals(newValue)) {
      return;
    }
    mPlannerCalendarId = newValue;
    if (!newValue.equals("-1")) {
      // if we cannot verify that the oldValue has the correct path
      // we will not risk mangling with an unrelated calendar
      if (!oldValue.equals("-1") && !checkPlannerInternal(oldValue))
        safeToMovePlans = false;
      ContentResolver cr = getContentResolver();
      // we also store the name and account of the calendar,
      // to protect against cases where a user wipes the data of the calendar
      // provider
      // and then accidentally we link to the wrong calendar
      Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI,
          Long.parseLong(mPlannerCalendarId));
      Cursor c = cr.query(uri, new String[] { CALENDAR_FULL_PATH_PROJECTION
          + " AS path" }, null, null, null);
      if (c != null && c.moveToFirst()) {
        String path = c.getString(0);
        Log.i(TAG, "storing calendar path : " + path);
        PrefKey.PLANNER_CALENDAR_PATH.putString(path);
      } else {
        AcraHelper.report(new IllegalStateException(
            "could not retrieve configured calendar"));
        mPlannerCalendarId = "-1";
        PrefKey.PLANNER_CALENDAR_PATH.remove();
        PrefKey.PLANNER_CALENDAR_ID.putString("-1");
      }
      if (c != null) {
        c.close();
      }
      if (mPlannerCalendarId.equals("-1")) {
        return;
      }
      if (oldValue.equals("-1")) {
        initPlanner();
      } else if (safeToMovePlans) {
        ContentValues eventValues = new ContentValues();
        eventValues.put(Events.CALENDAR_ID, Long.parseLong(newValue));
        Cursor planCursor = cr.query(Template.CONTENT_URI, new String[] {
            DatabaseConstants.KEY_ROWID, DatabaseConstants.KEY_PLANID },
            DatabaseConstants.KEY_PLANID + " IS NOT null", null, null);
        if (planCursor != null) {
          if (planCursor.moveToFirst()) {
            do {
              long templateId = planCursor.getLong(0);
              long planId = planCursor.getLong(1);
              Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI,
                  planId);

              Cursor eventCursor = cr.query(eventUri, buildEventProjection(),
                  Events.CALENDAR_ID + " = ?", new String[] { oldValue }, null);
              if (eventCursor != null) {
                if (eventCursor.moveToFirst()) {
                  // Log.i("DEBUG",
                  // DatabaseUtils.dumpCursorToString(eventCursor));
                  copyEventData(eventCursor, eventValues);
                  if (insertEventAndUpdatePlan(eventValues, templateId)) {
                    Log.i(TAG, "updated plan id in template:" + templateId);
                    int deleted = cr.delete(eventUri, null, null);
                    Log.i(TAG, "deleted old event: " + deleted);
                  }
                }
                eventCursor.close();
              }
            } while (planCursor.moveToNext());
          }
          planCursor.close();
        }
      }
    } else {
      PrefKey.PLANNER_CALENDAR_PATH.remove();
    }
  }

  private class WidgetObserver extends ContentObserver {
    /**
       * 
       */
    private Class<? extends AbstractWidget<?>> mProvider;

    WidgetObserver(Class<? extends AbstractWidget<?>> provider) {
      super(null);
      mProvider = provider;
    }

    @Override
    public void onChange(boolean selfChange) {
      AbstractWidget.updateWidgets(mSelf, mProvider);
    }
  }

  public int getMemoryClass() {
    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    return am.getMemoryClass();
  }

  /**
   * 1.check if a planner is configured. If no, nothing to do 2.check if the
   * configured planner exists on the device 2.1 if yes go through all events
   * and look for them based on UUID added to description recreate events that
   * we did not find (2.2 if no, user should have been asked to select a target
   * calendar where we will store the recreated events)
   *
   * @return Result with success true
   */
  public Result restorePlanner() {
    ContentResolver cr = getContentResolver();
    String TAG = "restorePlanner";
    String calendarId = PrefKey.PLANNER_CALENDAR_ID.getString("-1");
    String calendarPath = PrefKey.PLANNER_CALENDAR_PATH.getString("");
    Log.d(TAG, String.format(
        "restore plans to calendar with id %s and path %s", calendarId,
        calendarPath));
    int restoredPlansCount = 0;
    if (!(calendarId.equals("-1") || calendarPath.equals(""))) {
      Cursor c = cr.query(Calendars.CONTENT_URI,
          new String[] { Calendars._ID }, CALENDAR_FULL_PATH_PROJECTION
              + " = ?", new String[] { calendarPath }, null);
      if (c != null) {
        if (c.moveToFirst()) {
          mPlannerCalendarId = c.getString(0);
          Log.d(TAG, String.format("restorePlaner: found calendar with id %s",
              mPlannerCalendarId));
          PrefKey.PLANNER_CALENDAR_ID.putString(mPlannerCalendarId);
          ContentValues planValues = new ContentValues(), eventValues = new ContentValues();
          eventValues.put(Events.CALENDAR_ID,
              Long.parseLong(mPlannerCalendarId));
          Cursor planCursor = cr.query(Template.CONTENT_URI, new String[] {
              DatabaseConstants.KEY_ROWID, DatabaseConstants.KEY_PLANID,
              DatabaseConstants.KEY_UUID }, DatabaseConstants.KEY_PLANID
              + " IS NOT null", null, null);
          if (planCursor != null) {
            if (planCursor.moveToFirst()) {
              do {
                long templateId = planCursor.getLong(0);
                long oldPlanId = planCursor.getLong(1);
                String uuid = planCursor.getString(2);
                Cursor eventCursor = cr
                    .query(Events.CONTENT_URI, new String[] { Events._ID },
                        Events.CALENDAR_ID + " = ? AND " + Events.DESCRIPTION
                            + " LIKE ?", new String[] { mPlannerCalendarId,
                            "%" + uuid + "%" }, null);
                if (eventCursor != null) {
                  if (eventCursor.moveToFirst()) {
                    long newPlanId = eventCursor.getLong(0);
                    Log.d(TAG, String.format(
                        "Looking for event with uuid %s: found id %d. "
                            + "Original event had id %d", uuid, newPlanId,
                        oldPlanId));
                    if (newPlanId != oldPlanId) {
                      planValues.put(DatabaseConstants.KEY_PLANID, newPlanId);
                      int updated = cr.update(ContentUris.withAppendedId(
                          Template.CONTENT_URI, templateId), planValues, null,
                          null);
                      if (updated > 0) {
                        Log.i(TAG, "updated plan id in template:" + templateId);
                        restoredPlansCount++;
                      }
                    } else {
                      restoredPlansCount++;
                    }
                    continue;
                  }
                  eventCursor.close();
                }
                Log.d(
                    TAG,
                    String
                        .format(
                            "Looking for event with uuid %s did not find, now reconstructing from cache",
                            uuid));
                eventCursor = cr.query(TransactionProvider.EVENT_CACHE_URI,
                    buildEventProjection(), Events.DESCRIPTION + " LIKE ?",
                    new String[] { "%" + uuid + "%" }, null);
                boolean found = false;
                if (eventCursor != null) {
                  if (eventCursor.moveToFirst()) {
                    found = true;
                    copyEventData(eventCursor, eventValues);
                    if (insertEventAndUpdatePlan(eventValues, templateId)) {
                      Log.i(TAG, "updated plan id in template:" + templateId);
                      restoredPlansCount++;
                    }
                  }
                  eventCursor.close();
                }
                if (!found) {
                  //need to set eventId to null
                  planValues.putNull(DatabaseConstants.KEY_PLANID);
                  getContentResolver().update(
                      ContentUris.withAppendedId(Template.CONTENT_URI, templateId),
                      planValues, null, null);
                }
              } while (planCursor.moveToNext());
            }
            planCursor.close();
          }
        }
        c.close();
      }
    }
    return new Result(true, R.string.restore_calendar_success,
        restoredPlansCount);
  }

  public static String getMarketPrefix() {
    switch(BuildConfig.FLAVOR) {
      case "amazon":
        return "amzn://apps/android?p=";
      default:
        return "market://details?id=";
    }
  }

  public static String getMarketSelfUri() {
    if (BuildConfig.FLAVOR.equals("blackberry")) {
      return "appworld://content/54472888";
    } else {
      return getMarketPrefix() + "org.totschnig.myexpenses";
    }
  }

  public static void markDataDirty() {
    boolean persistedDirty =  PrefKey.AUTO_BACKUP_DIRTY.getBoolean(true);
    if (!persistedDirty) {
      PrefKey.AUTO_BACKUP_DIRTY.putBoolean(true);
      DailyAutoBackupScheduler.updateAutoBackupAlarms(mSelf);
    }
  }
}
