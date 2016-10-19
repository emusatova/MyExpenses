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

package org.totschnig.myexpenses.fragment;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

import org.apache.commons.lang3.ArrayUtils;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ExpenseEdit;
import org.totschnig.myexpenses.activity.ManageCategories;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.adapter.TransactionAdapter;
import org.totschnig.myexpenses.dialog.AmountFilterDialog;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.DateFilterDialog;
import org.totschnig.myexpenses.dialog.EditTextDialog;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.dialog.SelectCrStatusDialogFragment;
import org.totschnig.myexpenses.dialog.SelectMethodDialogFragment;
import org.totschnig.myexpenses.dialog.SelectPayerDialogFragment;
import org.totschnig.myexpenses.dialog.SelectTransferAccountDialogFragment;
import org.totschnig.myexpenses.dialog.TransactionDetailFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.model.Transaction.CrStatus;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.preference.SharedPreferencesCompat;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.provider.filter.*;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.ui.SimpleCursorAdapter;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.Utils;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.ExpandableStickyListHeadersListView;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView.OnHeaderClickListener;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri.Builder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

//TODO: consider moving to ListFragment
public class TransactionList extends ContextualActionBarFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, OnHeaderClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

  protected int getMenuResource() {
    return R.menu.transactionlist_context;
  }

  protected WhereFilter mFilter = WhereFilter.empty();

  private static final int TRANSACTION_CURSOR = 0;
  private static final int SUM_CURSOR = 1;
  private static final int GROUPING_CURSOR = 2;

  public static final String KEY_FILTER = "filter";
  public static final String CATEGORY_SEPARATOR = " : ",
      COMMENT_SEPARATOR = " / ";
  private MyGroupedAdapter mAdapter;
  private AccountObserver aObserver;
  Account mAccount;
  public boolean hasItems, mappedCategories, mappedPayees, mappedMethods, hasTransfers;
  private Cursor mTransactionsCursor, mGroupingCursor;

  private ExpandableStickyListHeadersListView mListView;
  private LoaderManager mManager;
  private SparseBooleanArray mappedCategoriesPerGroup;

  /**
   * needs to be static, because a new instance is created, but loader is reused
   */
  private static boolean scheduledRestart = false;
  /**
   * used to restore list selection when drawer is reopened
   */
  private SparseBooleanArray mCheckedListItems;

  private int columnIndexYear,                 columnIndexYearOfWeekStart,  columnIndexMonth,
              columnIndexWeek,                 columnIndexDay,              columnIndexLabelSub,
              columnIndexPayee,                columnIndexCrStatus,         columnIndexGroupYear,
              columnIndexGroupMappedCategories,columnIndexGroupSumInterim,  columnIndexGroupSumIncome,
              columnIndexGroupSumExpense,      columnIndexGroupSumTransfer, columnIndexYearOfMonthStart,
              columnIndexLabelMain,            columnIndexGroupSecond;
  boolean indexesCalculated = false, indexesGroupingCalculated = false;
  //the following values are cached from the account object, so that we can react to changes in the observer
  private Grouping mGrouping;
  private AccountType mType;
  private String mCurrency;
  private Long mOpeningBalance;

  public static Fragment newInstance(long accountId) {
    TransactionList pageFragment = new TransactionList();
    Bundle bundle = new Bundle();
    bundle.putSerializable(KEY_ACCOUNTID, accountId);
    pageFragment.setArguments(bundle);
    return pageFragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
    mappedCategoriesPerGroup = new SparseBooleanArray();
    mAccount = Account.getInstanceFromDb(getArguments().getLong(KEY_ACCOUNTID));
    if (mAccount == null) {
      return;
    }
    mGrouping = mAccount.grouping;
    mType = mAccount.type;
    mCurrency = mAccount.currency.getCurrencyCode();
    mOpeningBalance = mAccount.openingBalance.getAmountMinor();
    MyApplication.getInstance().getSettings().registerOnSharedPreferenceChangeListener(this);
  }

  private void setAdapter() {
    Context ctx = getActivity();
    // Create an array to specify the fields we want to display in the list
    String[] from = new String[]{KEY_LABEL_MAIN, KEY_DATE, KEY_AMOUNT};

    // and an array of the fields we want to bind those fields to 
    int[] to = new int[]{R.id.category, R.id.date, R.id.amount};
    mAdapter = new MyGroupedAdapter(ctx, R.layout.expense_row, null, from, to, 0);
    mListView.setAdapter(mAdapter);
  }

  private void setGrouping() {
    mAdapter.refreshDateFormat();
    restartGroupingLoader();
  }

  private void restartGroupingLoader() {
    mGroupingCursor = null;
    if (mManager == null) {
      //can happen after an orientation change in ExportDialogFragment, when resetting multiple accounts
      mManager = getLoaderManager();
    }
    if (mManager.getLoader(GROUPING_CURSOR) != null && !mManager.getLoader(GROUPING_CURSOR).isReset())
      mManager.restartLoader(GROUPING_CURSOR, null, this);
    else
      mManager.initLoader(GROUPING_CURSOR, null, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    MyApplication.getInstance().getSettings().unregisterOnSharedPreferenceChangeListener(this);
    if (aObserver != null) {
      try {
        ContentResolver cr = getActivity().getContentResolver();
        cr.unregisterContentObserver(aObserver);
      } catch (IllegalStateException ise) {
        // Do Nothing.  Observer has already been unregistered.
      }
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final MyExpenses ctx = (MyExpenses) getActivity();
    if (mAccount == null) {
      TextView tv = new TextView(ctx);
      //noinspection SetTextI18n
      tv.setText("Error loading transaction list for account " + getArguments().getLong(KEY_ACCOUNTID));
      return tv;
    }
    mManager = getLoaderManager();
    //setGrouping();
    if (savedInstanceState != null) {
      mFilter = new WhereFilter(savedInstanceState.getSparseParcelableArray(KEY_FILTER));
    } else {
      restoreFilterFromPreferences();
    }
    View v = inflater.inflate(R.layout.expenses_list, container, false);
    //TODO check if still needed with Appcompat
    //work around the problem that the view pager does not display its background correctly with Sherlock
    if (Build.VERSION.SDK_INT < 11) {
      v.setBackgroundColor(ctx.getResources().getColor(
          PrefKey.UI_THEME_KEY.getString("dark").equals("light")
              ? android.R.color.white : android.R.color.black));
    }
    mListView = (ExpandableStickyListHeadersListView) v.findViewById(R.id.list);
    setAdapter();
    mListView.setOnHeaderClickListener(this);
    mListView.setDrawingListUnderStickyHeader(false);
    if (scheduledRestart) {
      mManager.restartLoader(TRANSACTION_CURSOR, null, this);
      mManager.restartLoader(GROUPING_CURSOR, null, this);
      scheduledRestart = false;
    } else {
      mManager.initLoader(GROUPING_CURSOR, null, this);
      mManager.initLoader(TRANSACTION_CURSOR, null, this);
    }
    mManager.initLoader(SUM_CURSOR, null, this);

    mListView.setEmptyView(v.findViewById(R.id.empty));
    mListView.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> a, View v, int position, long id) {
        FragmentManager fm = ctx.getSupportFragmentManager();
        DialogFragment f = (DialogFragment) fm.findFragmentByTag(TransactionDetailFragment.class.getName());
        if (f == null) {
          FragmentTransaction ft = fm.beginTransaction();
          TransactionDetailFragment.newInstance(id).show(ft, TransactionDetailFragment.class.getName());
        }
      }
    });
    aObserver = new AccountObserver(new Handler());
    ContentResolver cr = getActivity().getContentResolver();
    //when account has changed, we might have
    //1) to refresh the list (currency has changed),
    //2) update current balance(opening balance has changed),
    //3) update the bottombarcolor (color has changed)
    //4) refetch grouping cursor (grouping has changed)
    cr.registerContentObserver(
        TransactionProvider.ACCOUNTS_URI,
        true, aObserver);

    registerForContextualActionBar(mListView.getWrappedList());
    return v;
  }

  @Override
  public boolean dispatchCommandMultiple(int command,
                                         SparseBooleanArray positions, Long[] itemIds) {
    MyExpenses ctx = (MyExpenses) getActivity();
    FragmentManager fm = ctx.getSupportFragmentManager();
    switch (command) {
      case R.id.DELETE_COMMAND:
        boolean hasReconciled = false, hasNotVoid = false;
        for (int i = 0; i < positions.size(); i++) {
          if (positions.valueAt(i)) {
            mTransactionsCursor.moveToPosition(positions.keyAt(i));
            CrStatus status;
            try {
              status = CrStatus.valueOf(mTransactionsCursor.getString(columnIndexCrStatus));
            } catch (IllegalArgumentException ex) {
              status = CrStatus.UNRECONCILED;
            }
            if (status == CrStatus.RECONCILED) {
              hasReconciled = true;
            }
            if (status != CrStatus.VOID) {
              hasNotVoid = true;
            }
            if (hasNotVoid && hasReconciled) break;
          }
        }
        String message = getResources().getQuantityString(R.plurals.warning_delete_transaction, itemIds.length, itemIds.length);
        if (hasReconciled) {
          message += " " + getString(R.string.warning_delete_reconciled);
        }
        Bundle b = new Bundle();
        b.putInt(ConfirmationDialogFragment.KEY_TITLE,
            R.string.dialog_title_warning_delete_transaction);
        b.putString(
            ConfirmationDialogFragment.KEY_MESSAGE, message);
        b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
            R.id.DELETE_COMMAND_DO);
        b.putInt(ConfirmationDialogFragment.KEY_COMMAND_NEGATIVE,
            R.id.CANCEL_CALLBACK_COMMAND);
        b.putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.menu_delete);
        if (hasNotVoid) {
          b.putInt(ConfirmationDialogFragment.KEY_CHECKBOX_LABEL,
              R.string.mark_void_instead_of_delete);
        }
        b.putLongArray(TaskExecutionFragment.KEY_OBJECT_IDS, ArrayUtils.toPrimitive(itemIds));
        ConfirmationDialogFragment.newInstance(b)
            .show(getFragmentManager(), "DELETE_TRANSACTION");
        return true;
/*    case R.id.CLONE_TRANSACTION_COMMAND:
      ctx.startTaskExecution(
          TaskExecutionFragment.TASK_CLONE,
          itemIds,
          null,
          0);
      break;*/
      case R.id.SPLIT_TRANSACTION_COMMAND:
        ctx.contribFeatureRequested(ContribFeature.SPLIT_TRANSACTION, itemIds);
        break;
      case R.id.UNDELETE_COMMAND:
        ctx.startTaskExecution(
            TaskExecutionFragment.TASK_UNDELETE_TRANSACTION,
            itemIds,
            null,
            0);
        break;
      //super is handling deactivation of mActionMode
    }
    return super.dispatchCommandMultiple(command, positions, itemIds);
  }

  @Override
  public boolean dispatchCommandSingle(int command, ContextMenu.ContextMenuInfo info) {
    AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) info;
    MyExpenses ctx = (MyExpenses) getActivity();
    switch (command) {
      case R.id.EDIT_COMMAND:
      case R.id.CLONE_TRANSACTION_COMMAND:
        mTransactionsCursor.moveToPosition(acmi.position);
        if (DbUtils.getLongOrNull(mTransactionsCursor, "transfer_peer_parent") != null) {
          Toast.makeText(getActivity(), getString(R.string.warning_splitpartcategory_context), Toast.LENGTH_LONG).show();
        } else {
          Intent i = new Intent(ctx, ExpenseEdit.class);
          i.putExtra(KEY_ROWID, acmi.id);
          if (command == R.id.CLONE_TRANSACTION_COMMAND) {
            i.putExtra(ExpenseEdit.KEY_CLONE, true);
          }
          ctx.startActivityForResult(i, MyExpenses.EDIT_TRANSACTION_REQUEST);
        }
        //super is handling deactivation of mActionMode
        break;
      case R.id.CREATE_TEMPLATE_COMMAND:
        mTransactionsCursor.moveToPosition(acmi.position);
        String label = mTransactionsCursor.getString(columnIndexPayee);
        if (TextUtils.isEmpty(label))
          label = mTransactionsCursor.getString(columnIndexLabelSub);
        if (TextUtils.isEmpty(label))
          label = mTransactionsCursor.getString(columnIndexLabelMain);
        Bundle args = new Bundle();
        args.putLong(KEY_ROWID, acmi.id);
        args.putString(EditTextDialog.KEY_DIALOG_TITLE, getString(R.string.dialog_title_template_title));
        args.putString(EditTextDialog.KEY_VALUE, label);
        args.putInt(EditTextDialog.KEY_REQUEST_CODE, ProtectedFragmentActivity.TEMPLATE_TITLE_REQUEST);
        EditTextDialog.newInstance(args).show(ctx.getSupportFragmentManager(), "TEMPLATE_TITLE");
        return true;
    }
    return super.dispatchCommandSingle(command, info);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
    CursorLoader cursorLoader = null;
    String selection;
    String[] selectionArgs;
    if (mAccount.getId() < 0) {
      selection = KEY_ACCOUNTID + " IN " +
          "(SELECT " + KEY_ROWID + " from " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + " = ? AND " +
          KEY_EXCLUDE_FROM_TOTALS + "=0)";
      selectionArgs = new String[]{mAccount.currency.getCurrencyCode()};
    } else {
      selection = KEY_ACCOUNTID + " = ?";
      selectionArgs = new String[]{String.valueOf(mAccount.getId())};
    }
    switch (id) {
      case TRANSACTION_CURSOR:
        if (!mFilter.isEmpty()) {
          String selectionForParents = mFilter.getSelectionForParents(DatabaseConstants.VIEW_EXTENDED);
          if (!selectionForParents.equals("")) {
            selection += " AND " + selectionForParents;
            selectionArgs = Utils.joinArrays(selectionArgs, mFilter.getSelectionArgs(false));
          }
        }
        cursorLoader = new CursorLoader(getActivity(),
            Transaction.EXTENDED_URI, null, selection + " AND " + KEY_PARENTID + " is null",
            selectionArgs, null);
        break;
      //TODO: probably we can get rid of SUM_CURSOR, if we also aggregate unmapped transactions
      case SUM_CURSOR:
        cursorLoader = new CursorLoader(getActivity(),
            TransactionProvider.TRANSACTIONS_URI,
            new String[]{MAPPED_CATEGORIES, MAPPED_METHODS, MAPPED_PAYEES, HAS_TRANSFERS},
            selection,
            selectionArgs, null);
        break;
      case GROUPING_CURSOR:
        selection = null;
        selectionArgs = null;
        Builder builder = TransactionProvider.TRANSACTIONS_URI.buildUpon();
        if (!mFilter.isEmpty()) {
          selection = mFilter.getSelectionForParts(DatabaseConstants.VIEW_EXTENDED);//GROUP query uses extended view
          if (!selection.equals("")) {
            selectionArgs = mFilter.getSelectionArgs(true);
            builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_IS_FILTERED, "1");
          }
        }
        builder.appendPath(TransactionProvider.URI_SEGMENT_GROUPS)
            .appendPath(mAccount.grouping.name());
        if (mAccount.getId() < 0) {
          builder.appendQueryParameter(KEY_CURRENCY, mAccount.currency.getCurrencyCode());
        } else {
          builder.appendQueryParameter(KEY_ACCOUNTID, String.valueOf(mAccount.getId()));
        }
        cursorLoader = new CursorLoader(getActivity(),
            builder.build(),
            null, selection, selectionArgs, null);
        break;
    }
    return cursorLoader;
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor c) {
    switch (arg0.getId()) {
      case TRANSACTION_CURSOR:
        mTransactionsCursor = c;
        hasItems = c.getCount() > 0;
        if (!indexesCalculated) {
          columnIndexYear = c.getColumnIndex(KEY_YEAR);
          columnIndexYearOfWeekStart = c.getColumnIndex(KEY_YEAR_OF_WEEK_START);
          columnIndexYearOfMonthStart = c.getColumnIndex(KEY_YEAR_OF_MONTH_START);
          columnIndexMonth = c.getColumnIndex(KEY_MONTH);
          columnIndexWeek = c.getColumnIndex(KEY_WEEK);
          columnIndexDay = c.getColumnIndex(KEY_DAY);
          columnIndexLabelSub = c.getColumnIndex(KEY_LABEL_SUB);
          columnIndexLabelMain = c.getColumnIndex(KEY_LABEL_MAIN);
          columnIndexPayee = c.getColumnIndex(KEY_PAYEE_NAME);
          columnIndexCrStatus = c.getColumnIndex(KEY_CR_STATUS);
          indexesCalculated = true;
        }
        mAdapter.swapCursor(c);
        invalidateCAB();
        break;
      case SUM_CURSOR:
        c.moveToFirst();
        mappedCategories = c.getInt(c.getColumnIndex(KEY_MAPPED_CATEGORIES)) > 0;
        mappedPayees = c.getInt(c.getColumnIndex(KEY_MAPPED_PAYEES)) > 0;
        mappedMethods = c.getInt(c.getColumnIndex(KEY_MAPPED_METHODS)) > 0;
        hasTransfers = c.getInt(c.getColumnIndex(KEY_HAS_TRANSFERS)) > 0;
        getActivity().supportInvalidateOptionsMenu();
        break;
      case GROUPING_CURSOR:
        mGroupingCursor = c;
        //if the transactionscursor has been loaded before the grouping cursor, we need to refresh
        //in order to have accurate grouping values
        if (!indexesGroupingCalculated) {
          columnIndexGroupYear = c.getColumnIndex(KEY_YEAR);
          columnIndexGroupSecond = c.getColumnIndex(KEY_SECOND_GROUP);
          columnIndexGroupSumIncome = c.getColumnIndex(KEY_SUM_INCOME);
          columnIndexGroupSumExpense = c.getColumnIndex(KEY_SUM_EXPENSES);
          columnIndexGroupSumTransfer = c.getColumnIndex(KEY_SUM_TRANSFERS);
          columnIndexGroupMappedCategories = c.getColumnIndex(KEY_MAPPED_CATEGORIES);
          columnIndexGroupSumInterim = c.getColumnIndex(KEY_INTERIM_BALANCE);
          indexesGroupingCalculated = true;
        }
        if (mTransactionsCursor != null)
          mAdapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    switch (arg0.getId()) {
      case TRANSACTION_CURSOR:
        mTransactionsCursor = null;
        ((SimpleCursorAdapter) mAdapter).swapCursor(null);
        hasItems = false;
        break;
      case SUM_CURSOR:
        mappedCategories = false;
        mappedPayees = false;
        mappedMethods = false;
        break;
      case GROUPING_CURSOR:
        mGroupingCursor = null;
    }
  }

  public boolean isFiltered() {
    return !mFilter.isEmpty();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(PrefKey.UI_LANGUAGE.getKey()) ||
        key.equals(PrefKey.GROUP_MONTH_STARTS.getKey()) ||
        key.equals(PrefKey.GROUP_WEEK_STARTS.getKey())) {
      scheduledRestart = true;
    }
  }

  class AccountObserver extends ContentObserver {
    public AccountObserver(Handler handler) {
      super(handler);
    }

    public void onChange(boolean selfChange) {
      if (getActivity() == null || getActivity().isFinishing()) {
        return;
      }
      //if grouping has changed
      if (mAccount.grouping != mGrouping) {
        mGrouping = mAccount.grouping;
        if (mAdapter != null) {
          setGrouping();
          //we should not need to notify here, since setGrouping restarts
          //the loader and in onLoadFinished we notify
          //mAdapter.notifyDataSetChanged();
        }
        return;
      }
      if (mAccount.type != mType ||
          mAccount.currency.getCurrencyCode() != mCurrency) {
        mListView.setAdapter(mAdapter);
        mType = mAccount.type;
        mCurrency = mAccount.currency.getCurrencyCode();
      }
      if (!mAccount.openingBalance.getAmountMinor().equals(mOpeningBalance)) {
        restartGroupingLoader();
        mOpeningBalance = mAccount.openingBalance.getAmountMinor();
      }
    }
  }

  public class MyGroupedAdapter extends TransactionAdapter implements StickyListHeadersAdapter {
    LayoutInflater inflater;

    public MyGroupedAdapter(Context context, int layout, Cursor c, String[] from,
                            int[] to, int flags) {
      super(mAccount, context, layout, c, from, to, flags);
      inflater = LayoutInflater.from(getActivity());
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
      HeaderViewHolder holder;
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.header, parent, false);
        holder = new HeaderViewHolder();
        holder.sumExpense = (TextView) convertView.findViewById(R.id.sum_expense);
        holder.sumIncome = (TextView) convertView.findViewById(R.id.sum_income);
        convertView.setTag(holder);
      } else {
        holder = (HeaderViewHolder) convertView.getTag();
      }

      Cursor c = getCursor();
      c.moveToPosition(position);
      int year = c.getInt(getColumnIndexForYear());
      int second = -1;

      if (mGroupingCursor != null && mGroupingCursor.moveToFirst()) {
        //no grouping, we need the first and only row
        if (mAccount.grouping.equals(Grouping.NONE)) {
          fillSums(holder, mGroupingCursor);
        } else {
          traverseCursor:
          while (!mGroupingCursor.isAfterLast()) {
            if (mGroupingCursor.getInt(columnIndexGroupYear) == year) {
              switch (mAccount.grouping) {
                case YEAR:
                  fillSums(holder, mGroupingCursor);
                  break traverseCursor;
                case DAY:
                  second = c.getInt(columnIndexDay);
                  if (mGroupingCursor.getInt(columnIndexGroupSecond) != second)
                    break;
                  else {
                    fillSums(holder, mGroupingCursor);
                    break traverseCursor;
                  }
                case MONTH:
                  second = c.getInt(columnIndexMonth);
                  if (mGroupingCursor.getInt(columnIndexGroupSecond) != second)
                    break;
                  else {
                    fillSums(holder, mGroupingCursor);
                    break traverseCursor;
                  }
                case WEEK:
                  second = c.getInt(columnIndexWeek);
                  if (mGroupingCursor.getInt(columnIndexGroupSecond) != second)
                    break;
                  else {
                    fillSums(holder, mGroupingCursor);
                    break traverseCursor;
                  }
              }
            }
            mGroupingCursor.moveToNext();
          }
        }
        if (!mGroupingCursor.isAfterLast())
          mappedCategoriesPerGroup.put(position, mGroupingCursor.getInt(columnIndexGroupMappedCategories) > 0);
      }
      return convertView;
    }

    @SuppressLint("SetTextI18n")
    private void fillSums(HeaderViewHolder holder, Cursor mGroupingCursor) {
      Long sumExpense = DbUtils.getLongOr0L(mGroupingCursor, columnIndexGroupSumExpense);
      holder.sumExpense.setText("Out: - " + Utils.convAmount(
          sumExpense,
          mAccount.currency));
      Long sumIncome = DbUtils.getLongOr0L(mGroupingCursor, columnIndexGroupSumIncome);
      holder.sumIncome.setText("In: + " + Utils.convAmount(
          sumIncome,
          mAccount.currency));
    }

    @Override
    public long getHeaderId(int position) {
      if (mAccount.grouping.equals(Grouping.NONE))
        return 1;
      Cursor c = getCursor();
      c.moveToPosition(position);
      int year = c.getInt(getColumnIndexForYear());
      int month = c.getInt(columnIndexMonth);
      int week = c.getInt(columnIndexWeek);
      int day = c.getInt(columnIndexDay);
      switch (mAccount.grouping) {
        case DAY:
          return year * 1000 + day;
        case WEEK:
          return year * 1000 + week;
        case MONTH:
          return year * 1000 + month;
        case YEAR:
          return year * 1000;
        default:
          return 0;
      }
    }

    private int getColumnIndexForYear() {
      switch (mAccount.grouping) {
        case WEEK:
          return columnIndexYearOfWeekStart;
        case MONTH:
          return columnIndexYearOfMonthStart;
        default:
          return columnIndexYear;
      }
    }
  }

  class HeaderViewHolder {
    TextView sumIncome;
    TextView sumExpense;
  }

  @Override
  public void onHeaderClick(StickyListHeadersListView l, View header,
                            int itemPosition, long headerId, boolean currentlySticky) {
    if (mListView.isHeaderCollapsed(headerId)) {
      mListView.expand(headerId);
    } else {
      mListView.collapse(headerId);
    }
  }

  @Override
  public boolean onHeaderLongClick(StickyListHeadersListView l, View header,
                                   int itemPosition, long headerId, boolean currentlySticky) {
    MyExpenses ctx = (MyExpenses) getActivity();
    if (mappedCategoriesPerGroup.get(itemPosition)) {
      ctx.contribFeatureRequested(ContribFeature.DISTRIBUTION, headerId);
    } else {
      Toast.makeText(ctx, getString(R.string.no_mapped_transactions), Toast.LENGTH_LONG).show();
    }
    return true;
  }

  @Override
  protected void configureMenuLegacy(Menu menu, ContextMenuInfo menuInfo, int listId) {
    super.configureMenuLegacy(menu, menuInfo, listId);
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    configureMenuInternal(menu, isSplitAtPosition(info.position), isVoidAtPosition(info.position), 1);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  protected void configureMenu11(Menu menu, int count, AbsListView lv) {
    super.configureMenu11(menu, count, lv);
    SparseBooleanArray checkedItemPositions = lv.getCheckedItemPositions();
    boolean hasSplit = false, hasNotVoid = false;
    for (int i = 0; i < checkedItemPositions.size(); i++) {
      if (checkedItemPositions.valueAt(i) && isSplitAtPosition(checkedItemPositions.keyAt(i))) {
        hasSplit = true;
        break;
      }
    }
    for (int i = 0; i < checkedItemPositions.size(); i++) {
      if (checkedItemPositions.valueAt(i) && isVoidAtPosition(checkedItemPositions.keyAt(i))) {
        hasNotVoid = true;
        break;
      }
    }
    configureMenuInternal(menu, hasSplit, hasNotVoid, count);
  }

  private boolean isSplitAtPosition(int position) {
    if (mTransactionsCursor != null) {
      //templates for splits is not yet implemented
      if (mTransactionsCursor.moveToPosition(position) &&
          SPLIT_CATID.equals(DbUtils.getLongOrNull(mTransactionsCursor, KEY_CATID))) {
        return true;
      }
    }
    return false;
  }

  private boolean isVoidAtPosition(int position) {
    if (mTransactionsCursor != null) {
      if (mTransactionsCursor.moveToPosition(position)) {
        CrStatus status;
        try {
          status = CrStatus.valueOf(mTransactionsCursor.getString(columnIndexCrStatus));
        } catch (IllegalArgumentException ex) {
          status = CrStatus.UNRECONCILED;
        }
        if (status.equals(CrStatus.VOID)) {
          return true;
        }
      }
    }
    return false;
  }

  private void configureMenuInternal(Menu menu, boolean hasSplit, boolean hasVoid, int count) {
    menu.findItem(R.id.CREATE_TEMPLATE_COMMAND).setVisible(count == 1 && !hasSplit);
    menu.findItem(R.id.SPLIT_TRANSACTION_COMMAND).setVisible(!hasSplit && !hasVoid);
    menu.findItem(R.id.UNDELETE_COMMAND).setVisible(hasVoid);
    menu.findItem(R.id.EDIT_COMMAND).setVisible(count == 1 && !hasVoid);
  }

  @SuppressLint("NewApi")
  public void onDrawerOpened() {
    if (mActionMode != null) {
      mCheckedListItems = mListView.getWrappedList().getCheckedItemPositions().clone();
      mActionMode.finish();
    }
  }

  public void onDrawerClosed() {
    if (mCheckedListItems != null) {
      for (int i = 0; i < mCheckedListItems.size(); i++) {
        if (mCheckedListItems.valueAt(i)) {
          mListView.getWrappedList().setItemChecked(mCheckedListItems.keyAt(i), true);
        }
      }
    }
    mCheckedListItems = null;
  }

  public void addFilterCriteria(Integer id, Criteria c) {
    mFilter.put(id, c);
    SharedPreferencesCompat.apply(
        MyApplication.getInstance().getSettings().edit().putString(
            KEY_FILTER + "_" + c.columnName + "_" + mAccount.getId(), c.toStringExtra()));
    mManager.restartLoader(TRANSACTION_CURSOR, null, this);
    mManager.restartLoader(GROUPING_CURSOR, null, this);
    getActivity().supportInvalidateOptionsMenu();
  }

  /**
   * Removes a given filter
   *
   * @param id
   * @return true if the filter was set and succesfully removed, false otherwise
   */
  public boolean removeFilter(Integer id) {
    Criteria c = mFilter.get(id);
    boolean isFiltered = c != null;
    if (isFiltered) {
      SharedPreferencesCompat.apply(
          MyApplication.getInstance().getSettings().edit().remove(
              KEY_FILTER + "_" + c.columnName + "_" + mAccount.getId()));
      mFilter.remove(id);
      mManager.restartLoader(TRANSACTION_CURSOR, null, this);
      mManager.restartLoader(GROUPING_CURSOR, null, this);
      getActivity().supportInvalidateOptionsMenu();
    }
    return isFiltered;
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    if (mAccount == null || getActivity() == null) {
      //mAccount seen in report 3331195c529454ca6b25a4c5d403beda
      //getActivity seen in report 68a501c984bdfcc95b40050af4f815bf
      return;
    }
    MenuItem searchMenu = menu.findItem(R.id.SEARCH_COMMAND);
    if (searchMenu != null) {
      String title;
      Drawable searchMenuIcon = searchMenu.getIcon();
      if (!mFilter.isEmpty()) {
        if (searchMenuIcon != null) {
          searchMenuIcon.setColorFilter(Color.GREEN, PorterDuff.Mode.MULTIPLY);
        } else {
          AcraHelper.report(new Exception("Search menu icon not found"));
        }
        searchMenu.setChecked(true);
        title = mAccount.label + " ( " + mFilter.prettyPrint() + " )";
      } else {
        if (searchMenuIcon != null) {
          searchMenuIcon.setColorFilter(null);
        } else {
          AcraHelper.report(new Exception("Search menu icon not found"));
        }
        searchMenu.setChecked(false);
        title = mAccount.label;
      }
      ((MyExpenses) getActivity()).setTitle(title);
      SubMenu filterMenu = searchMenu.getSubMenu();
      for (int i = 0; i < filterMenu.size(); i++) {
        MenuItem filterItem = filterMenu.getItem(i);
        boolean enabled = true;
        switch (filterItem.getItemId()) {
          case R.id.FILTER_CATEGORY_COMMAND:
            enabled = mappedCategories;
            break;
          case R.id.FILTER_STATUS_COMMAND:
            enabled = !mAccount.type.equals(AccountType.CASH);
            break;
          case R.id.FILTER_PAYEE_COMMAND:
            enabled = mappedPayees;
            break;
          case R.id.FILTER_METHOD_COMMAND:
            enabled = mappedMethods;
            break;
          case R.id.FILTER_TRANSFER_COMMAND:
            enabled = hasTransfers;
            break;
        }
        Criteria c = mFilter.get(filterItem.getItemId());
        Utils.menuItemSetEnabledAndVisible(filterItem, enabled || c != null);
        if (c != null) {
          filterItem.setChecked(true);
          filterItem.setTitle(c.prettyPrint());
        }
      }
    } else {
      AcraHelper.report(new Exception("Search menu not found"));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSparseParcelableArray(KEY_FILTER, mFilter.getCriteria());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int command = item.getItemId();
    switch (command) {
      case R.id.FILTER_CATEGORY_COMMAND:
        if (!removeFilter(command)) {
          Intent i = new Intent(getActivity(), ManageCategories.class);
          i.setAction("myexpenses.intent.select_filter");
          startActivityForResult(i, ProtectedFragmentActivity.FILTER_CATEGORY_REQUEST);
        }
        return true;
      case R.id.FILTER_AMOUNT_COMMAND:
        if (!removeFilter(command)) {
          AmountFilterDialog.newInstance(mAccount.currency)
              .show(getActivity().getSupportFragmentManager(), "AMOUNT_FILTER");
        }
        return true;
      case R.id.FILTER_DATE_COMMAND:
        if (!removeFilter(command)) {
          DateFilterDialog.newInstance()
              .show(getActivity().getSupportFragmentManager(), "AMOUNT_FILTER");
        }
        return true;
      case R.id.FILTER_COMMENT_COMMAND:
        if (!removeFilter(command)) {
          Bundle args = new Bundle();
          args.putInt(EditTextDialog.KEY_REQUEST_CODE, ProtectedFragmentActivity.FILTER_COMMENT_REQUEST);
          args.putString(EditTextDialog.KEY_DIALOG_TITLE, getString(R.string.search_comment));
          EditTextDialog.newInstance(args).show(getActivity().getSupportFragmentManager(), "COMMENT_FILTER");
        }
        return true;
      case R.id.FILTER_STATUS_COMMAND:
        if (!removeFilter(command)) {
          SelectCrStatusDialogFragment.newInstance()
              .show(getActivity().getSupportFragmentManager(), "STATUS_FILTER");
        }
        return true;
      case R.id.FILTER_PAYEE_COMMAND:
        if (!removeFilter(command)) {
          SelectPayerDialogFragment.newInstance(mAccount.getId())
              .show(getActivity().getSupportFragmentManager(), "PAYER_FILTER");
        }
        return true;
      case R.id.FILTER_METHOD_COMMAND:
        if (!removeFilter(command)) {
          SelectMethodDialogFragment.newInstance(mAccount.getId())
              .show(getActivity().getSupportFragmentManager(), "METHOD_FILTER");
        }
        return true;
      case R.id.FILTER_TRANSFER_COMMAND:
        if (!removeFilter(command)) {
          SelectTransferAccountDialogFragment.newInstance(mAccount.getId())
              .show(getActivity().getSupportFragmentManager(), "TRANSFER_FILTER");
        }
        return true;
      case R.id.PRINT_COMMAND:
        MyExpenses ctx = (MyExpenses) getActivity();
        Result appDirStatus = Utils.checkAppDir();
        if (hasItems) {
          if (appDirStatus.success) {
            ctx.contribFeatureRequested(ContribFeature.PRINT, null);
          } else {
            Toast.makeText(getActivity(),
                appDirStatus.print(getActivity()),
                Toast.LENGTH_LONG)
                .show();
          }
        } else {
          MessageDialogFragment.newInstance(
              0,
              R.string.dialog_command_disabled_reset_account,
              MessageDialogFragment.Button.okButton(),
              null, null)
              .show(ctx.getSupportFragmentManager(), "BUTTON_DISABLED_INFO");
        }
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public SparseArray<Criteria> getFilterCriteria() {
    return mFilter.getCriteria();
  }

  private void restoreFilterFromPreferences() {
    SharedPreferences settings = MyApplication.getInstance().getSettings();
    String filter = settings.getString(KEY_FILTER + "_" + KEY_CATID + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_CATEGORY_COMMAND, CategoryCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_AMOUNT + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_AMOUNT_COMMAND, AmountCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_COMMENT + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_COMMENT_COMMAND, CommentCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_CR_STATUS + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_STATUS_COMMAND, CrStatusCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_PAYEEID + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_PAYEE_COMMAND, PayeeCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_METHODID + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_METHOD_COMMAND, MethodCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_DATE + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_DATE_COMMAND, DateCriteria.fromStringExtra(filter));
    }
    filter = settings.getString(KEY_FILTER + "_" + KEY_TRANSFER_ACCOUNT + "_" + mAccount.getId(), null);
    if (filter != null) {
      mFilter.put(R.id.FILTER_TRANSFER_COMMAND, TransferCriteria.fromStringExtra(filter));
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == ProtectedFragmentActivity.FILTER_CATEGORY_REQUEST &&
        resultCode != Activity.RESULT_CANCELED) {
      String label = intent.getStringExtra(KEY_LABEL);
      if (resultCode == Activity.RESULT_OK) {
        long catId = intent.getLongExtra(KEY_CATID, 0);
        addFilterCriteria(R.id.FILTER_CATEGORY_COMMAND, new CategoryCriteria(label, catId));
      }
      if (resultCode == Activity.RESULT_FIRST_USER) {
        long[] catIds = intent.getLongArrayExtra(KEY_CATID);
        addFilterCriteria(R.id.FILTER_CATEGORY_COMMAND, new CategoryCriteria(label, catIds));
      }
    }
  }
}
