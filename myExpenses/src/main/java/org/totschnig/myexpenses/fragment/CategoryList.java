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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.interfaces.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.apache.commons.lang3.ArrayUtils;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.MyApplication.ThemeType;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ManageCategories;
import org.totschnig.myexpenses.activity.ManageCategories.HelpVariant;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.dialog.SelectMainCategoryDialogFragment;
import org.totschnig.myexpenses.dialog.TransactionListDialogFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Grouping;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.ui.SimpleCursorTreeAdapter;
import org.totschnig.myexpenses.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import static org.totschnig.myexpenses.activity.AmountActivity.EXPENSE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CATID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_EXCLUDE_FROM_TOTALS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL_NORMALIZED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAX_VALUE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SUM;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_MONTH;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_WEEK;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR_OF_WEEK_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TYPE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_ACCOUNTS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_CATEGORIES;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_TEMPLATES;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TABLE_TRANSACTIONS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.THIS_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.THIS_MONTH;
import static org.totschnig.myexpenses.provider.DatabaseConstants.THIS_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.VIEW_COMMITTED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_VOID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getMonth;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getThisWeek;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getThisYearOfWeekStart;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getWeek;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfMonthStart;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfWeekStart;

public class CategoryList extends SortableListFragment implements
    OnChildClickListener, OnGroupClickListener, LoaderManager.LoaderCallbacks<Cursor> {

  private static final String KEY_CHILD_COUNT = "child_count";
  private View mImportButton;

  protected int getMenuResource() {
    return R.menu.categorylist_context;
  }

  private static final int SUM_CURSOR = -2;
  private static final int DATEINFO_CURSOR = -3;

  private MyExpandableListAdapter mAdapter;
  private ExpandableListView mListView;
  private LoaderManager mManager;
  private TextView incomeSumTv, expenseSumTv;
  private View bottomLine;
  private PieChart mChart;
  public Grouping mGrouping;
  int mGroupingYear;
  int mGroupingSecond;
  int thisYear, thisMonth, thisWeek, thisDay, maxValue;

  private Account mAccount;
  private Cursor mGroupCursor;

  protected boolean mType = EXPENSE;
  private ArrayList<Integer> mMainColors, mSubColors;
  private int lastExpandedPosition = -1;

  boolean showChart = false;
  boolean aggregateTypes;
  boolean chartDisplaysSubs;

  String mFilter;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    aggregateTypes = PrefKey.DISTRIBUTION_AGGREGATE_TYPES.getBoolean(true);
    final ManageCategories ctx = (ManageCategories) getActivity();
    View v;
    Bundle extras = ctx.getIntent().getExtras();
    mManager = getLoaderManager();
    if (ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
      showChart = PrefKey.DISTRIBUTION_SHOW_CHART.getBoolean(true);
      mMainColors = new ArrayList<>();
      for (int col : ColorTemplate.PASTEL_COLORS)
        mMainColors.add(col);
      for (int col : ColorTemplate.JOYFUL_COLORS)
        mMainColors.add(col);
      for (int col : ColorTemplate.LIBERTY_COLORS)
        mMainColors.add(col);
      for (int col : ColorTemplate.VORDIPLOM_COLORS)
        mMainColors.add(col);
      for (int col : ColorTemplate.COLORFUL_COLORS)
        mMainColors.add(col);
      mMainColors.add(ColorTemplate.getHoloBlue());

      final long id = Utils.getFromExtra(extras, KEY_ACCOUNTID, 0);
      mAccount = Account.getInstanceFromDb(id);
      if (mAccount == null) {
        TextView tv = new TextView(ctx);
        //noinspection SetTextI18n
        tv.setText("Error loading distribution for account " + id);
        return tv;
      }
      Bundle b = savedInstanceState != null ? savedInstanceState : extras;

      mGrouping = (Grouping) b.getSerializable("grouping");
      if (mGrouping == null) mGrouping = Grouping.NONE;
      mGroupingYear = b.getInt("groupingYear");
      mGroupingSecond = b.getInt("groupingSecond");
      getActivity().supportInvalidateOptionsMenu();
      mManager.initLoader(SUM_CURSOR, null, this);
      mManager.initLoader(DATEINFO_CURSOR, null, this);
      v = inflater.inflate(R.layout.distribution_list, container, false);
      mChart = (PieChart) v.findViewById(R.id.chart1);
      mChart.setVisibility(showChart ? View.VISIBLE : View.GONE);
      mChart.setDescription("");

      TypedValue typedValue = new TypedValue();
      getActivity().getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, typedValue, true);
      int[] textSizeAttr = new int[]{android.R.attr.textSize};
      int indexOfAttrTextSize = 0;
      TypedArray a = getActivity().obtainStyledAttributes(typedValue.data, textSizeAttr);
      int textSize = a.getDimensionPixelSize(indexOfAttrTextSize, -1);
      a.recycle();
      mChart.setCenterTextSizePixels(textSize);
      mChart.setDrawSliceText(false);
      mChart.setDrawHoleEnabled(true);
      mChart.setDrawCenterText(true);
      mChart.setRotationEnabled(false);
      mChart.setTouchEnabled(true);
      mChart.setRotationAngle(0);
      mChart.setRotationEnabled(true);

      mChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {

        @Override
        public void onValueSelected(Entry e, int dataSetIndex) {
          int index = e.getXIndex();
          long packedPosition = (lastExpandedPosition == -1) ?
              ExpandableListView.getPackedPositionForGroup(index) :
              ExpandableListView.getPackedPositionForChild(lastExpandedPosition, index);
          int flatPosition = mListView.getFlatListPosition(packedPosition);
          mListView.setItemChecked(flatPosition, true);
          mListView.smoothScrollToPosition(flatPosition);
          setCenterText(index);
        }

        @Override
        public void onNothingSelected() {
          mListView.setItemChecked(mListView.getCheckedItemPosition(), false);
        }
      });
    } else {
      v = inflater.inflate(R.layout.categories_list, container, false);
      if (savedInstanceState!=null) {
        mFilter = savedInstanceState.getString("filter");
      }
    }
    incomeSumTv = (TextView) v.findViewById(R.id.sum_income);
    expenseSumTv = (TextView) v.findViewById(R.id.sum_expense);
    bottomLine = v.findViewById(R.id.BottomLine);
    updateColor();
    mListView = (ExpandableListView) v.findViewById(R.id.list);
    final View emptyView = v.findViewById(R.id.empty);
    mListView.setEmptyView(emptyView);
    mImportButton = emptyView.findViewById(R.id.importButton);
    mManager.initLoader(SORTABLE_CURSOR, null, this);
    String[] from;
    int[] to;
    if (mAccount != null) {
      from = new String[]{KEY_LABEL, KEY_SUM};
      to = new int[]{R.id.label, R.id.amount};
    } else {
      from = new String[]{KEY_LABEL};
      to = new int[]{R.id.label};
    }
    mAdapter = new MyExpandableListAdapter(ctx,
        null,
        R.layout.category_row, R.layout.category_row,
        from, to, from, to);
    mListView.setAdapter(mAdapter);
    if (ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
      mListView.setOnGroupExpandListener(new OnGroupExpandListener() {
        @Override
        public void onGroupExpand(int groupPosition) {
          if (showChart) {
            if (lastExpandedPosition != -1
                && groupPosition != lastExpandedPosition) {
              mListView.collapseGroup(lastExpandedPosition);
            }
          }
          lastExpandedPosition = groupPosition;
        }
      });
      mListView.setOnGroupCollapseListener(new OnGroupCollapseListener() {
        @Override
        public void onGroupCollapse(int groupPosition) {
          if (showChart) {
            lastExpandedPosition = -1;
            setData(mGroupCursor, mMainColors);
            highlight(groupPosition);
            long packedPosition = ExpandableListView
                .getPackedPositionForGroup(groupPosition);
            int flatPosition = mListView.getFlatListPosition(packedPosition);
            mListView.setItemChecked(flatPosition, true);
          }
        }
      });
      mListView.setOnChildClickListener(new OnChildClickListener() {

        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {
          if (showChart) {
            long packedPosition = ExpandableListView.getPackedPositionForChild(
                groupPosition, childPosition);
            highlight(childPosition);
            int flatPosition = mListView.getFlatListPosition(packedPosition);
            mListView.setItemChecked(flatPosition, true);
            return true;
          }
          return false;
        }
      });
      //the following is relevant when not in touch mode
      mListView.setOnItemSelectedListener(new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                                   int position, long id) {
          if (showChart) {
            long pos = mListView.getExpandableListPosition(position);
            int type = ExpandableListView.getPackedPositionType(pos);
            int group = ExpandableListView.getPackedPositionGroup(pos),
                child = ExpandableListView.getPackedPositionChild(pos);
            int highlightedPos;
            if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
              if (lastExpandedPosition != group) {
                mListView.collapseGroup(lastExpandedPosition);
              }
              highlightedPos = lastExpandedPosition == -1 ? group : -1;
            } else {
              highlightedPos = child;
            }
            highlight(highlightedPos);
          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      });

      mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
      registerForContextMenu(mListView);
    } else {
      registerForContextualActionBar(mListView);
    }
    return v;
  }

  @Override
  public boolean dispatchCommandMultiple(int command,
                                         SparseBooleanArray positions, Long[] itemIds) {
    ManageCategories ctx = (ManageCategories) getActivity();
    ArrayList<Long> idList;
    switch (command) {
      case R.id.DELETE_COMMAND:
        int mappedTransactionsCount = 0, mappedTemplatesCount = 0, hasChildrenCount = 0;
        idList = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
          Cursor c;
          if (positions.valueAt(i)) {
            boolean deletable = true;
            int position = positions.keyAt(i);
            long pos = mListView.getExpandableListPosition(position);
            int type = ExpandableListView.getPackedPositionType(pos);
            int group = ExpandableListView.getPackedPositionGroup(pos),
                child = ExpandableListView.getPackedPositionChild(pos);
            if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
              c = mAdapter.getChild(group, child);
              c.moveToPosition(child);
            } else {
              c = mGroupCursor;
              c.moveToPosition(group);
            }
            long itemId = c.getLong(c.getColumnIndex(KEY_ROWID));
            Bundle extras = ctx.getIntent().getExtras();
            if ((extras != null && extras.getLong(KEY_ROWID) == itemId) || c.getInt(c.getColumnIndex(DatabaseConstants.KEY_MAPPED_TRANSACTIONS)) > 0) {
              mappedTransactionsCount++;
              deletable = false;
            } else if (c.getInt(c.getColumnIndex(DatabaseConstants.KEY_MAPPED_TEMPLATES)) > 0) {
              mappedTemplatesCount++;
              deletable = false;
            }
            if (deletable) {
              if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP && c.getInt(c.getColumnIndex(KEY_CHILD_COUNT)) > 0) {
                hasChildrenCount++;
              }
              idList.add(itemId);
            }
          }
        }
        if (!idList.isEmpty()) {
          Long[] objectIds = idList.toArray(new Long[idList.size()]);
          if (hasChildrenCount > 0) {
            MessageDialogFragment.newInstance(
                R.string.dialog_title_warning_delete_main_category,
                getResources().getQuantityString(R.plurals.warning_delete_main_category, hasChildrenCount, hasChildrenCount),
                new MessageDialogFragment.Button(android.R.string.yes, R.id.DELETE_COMMAND_DO, objectIds),
                null,
                new MessageDialogFragment.Button(android.R.string.no, R.id.CANCEL_CALLBACK_COMMAND, null))
                .show(ctx.getSupportFragmentManager(), "DELETE_CATEGORY");
          } else {
            ctx.dispatchCommand(R.id.DELETE_COMMAND_DO, objectIds);
          }
        }
        if (mappedTransactionsCount > 0 || mappedTemplatesCount > 0) {
          String message = "";
          if (mappedTransactionsCount > 0)
            message += getResources().getQuantityString(
                R.plurals.not_deletable_mapped_transactions,
                mappedTransactionsCount,
                mappedTransactionsCount);
          if (mappedTemplatesCount > 0)
            message += getResources().getQuantityString(
                R.plurals.not_deletable_mapped_templates,
                mappedTemplatesCount,
                mappedTemplatesCount);
          Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
        }
        return true;
      case R.id.SELECT_COMMAND_MULTIPLE:
        ArrayList<String> labelList = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
          Cursor c;
          if (positions.valueAt(i)) {
            int position = positions.keyAt(i);
            long pos = mListView.getExpandableListPosition(position);
            int type = ExpandableListView.getPackedPositionType(pos);
            int group = ExpandableListView.getPackedPositionGroup(pos),
                child = ExpandableListView.getPackedPositionChild(pos);
            if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
              c = mAdapter.getChild(group, child);
              c.moveToPosition(child);
            } else {
              c = mGroupCursor;
              c.moveToPosition(group);
            }
            labelList.add(c.getString(c.getColumnIndex(KEY_LABEL)));
          }
        }
        Intent intent = new Intent();
        intent.putExtra(KEY_CATID, ArrayUtils.toPrimitive(itemIds));
        intent.putExtra(KEY_LABEL, TextUtils.join(",", labelList));
        ctx.setResult(ManageCategories.RESULT_FIRST_USER, intent);
        ctx.finish();
        return true;
      case R.id.MOVE_COMMAND:
        final Long[] excludedIds;
        final boolean inGroup = expandableListSelectionType == ExpandableListView.PACKED_POSITION_TYPE_GROUP;
        if (inGroup) {
          excludedIds = itemIds;
        } else {
          idList = new ArrayList<>();
          for (int i = 0; i < positions.size(); i++) {
            if (positions.valueAt(i)) {
              int position = positions.keyAt(i);
              long pos = mListView.getExpandableListPosition(position);
              int group = ExpandableListView.getPackedPositionGroup(pos);
              mGroupCursor.moveToPosition(group);
              idList.add(mGroupCursor.getLong(mGroupCursor.getColumnIndex(KEY_ROWID)));
            }
          }
          excludedIds = idList.toArray(new Long[idList.size()]);
        }
        Bundle args = new Bundle(3);
        args.putBoolean(SelectMainCategoryDialogFragment.KEY_WITH_ROOT, !inGroup);
        args.putLongArray(SelectMainCategoryDialogFragment.KEY_EXCLUDED_ID, ArrayUtils.toPrimitive(excludedIds));
        args.putLongArray(TaskExecutionFragment.KEY_OBJECT_IDS, ArrayUtils.toPrimitive(itemIds));
        SelectMainCategoryDialogFragment.newInstance(args)
            .show(getFragmentManager(), "SELECT_TARGET");
        return true;
    }
    return false;
  }

  @Override
  public boolean dispatchCommandSingle(int command, ContextMenu.ContextMenuInfo info) {
    ManageCategories ctx = (ManageCategories) getActivity();
    ExpandableListContextMenuInfo elcmi = (ExpandableListContextMenuInfo) info;
    int type = ExpandableListView.getPackedPositionType(elcmi.packedPosition);
    Cursor c;
    boolean isMain;
    int group = ExpandableListView.getPackedPositionGroup(elcmi.packedPosition),
        child = ExpandableListView.getPackedPositionChild(elcmi.packedPosition);
    if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
      c = mAdapter.getChild(group, child);
      isMain = false;
    } else {
      c = mGroupCursor;
      isMain = true;
    }
    if (c == null || c.getCount() == 0) {
      //observed on Blackberry Z10
      return false;
    }
    String label = c.getString(c.getColumnIndex(KEY_LABEL));
    switch (command) {
      case R.id.EDIT_COMMAND:
        ctx.editCat(label, elcmi.id);
        return true;
      case R.id.SELECT_COMMAND:
        if (!isMain &&
            ctx.helpVariant.equals(ManageCategories.HelpVariant.select_mapping)) {
          mGroupCursor.moveToPosition(group);
          label = mGroupCursor.getString(mGroupCursor.getColumnIndex(KEY_LABEL))
              + TransactionList.CATEGORY_SEPARATOR
              + label;
        }
        doSelection(elcmi.id, label, isMain);
        finishActionMode();
        return true;
      case R.id.CREATE_COMMAND:
        ctx.createCat(elcmi.id);
        return true;
    }
    return super.dispatchCommandSingle(command, info);
  }

  /**
   * Mapping the categories table into the ExpandableList
   *
   * @author Michael Totschnig
   */
  public class MyExpandableListAdapter extends SimpleCursorTreeAdapter {
    private int colorExpense;
    private int colorIncome;

    public MyExpandableListAdapter(Context context, Cursor cursor, int groupLayout,
                                   int childLayout, String[] groupFrom, int[] groupTo, String[] childrenFrom,
                                   int[] childrenTo) {
      super(context, cursor, groupLayout, groupFrom, groupTo, childLayout, childrenFrom,
          childrenTo);
      colorIncome = ((ProtectedFragmentActivity) context).getColorIncome();
      colorExpense = ((ProtectedFragmentActivity) context).getColorExpense();
    }

    /* (non-Javadoc)
     * returns a cursor with the subcategories for the group
     * @see android.widget.CursorTreeAdapter#getChildrenCursor(android.database.Cursor)
     */
    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
      // Given the group, we return a cursor for all the children within that group
      long parentId = groupCursor.getLong(groupCursor.getColumnIndexOrThrow(KEY_ROWID));
      Bundle bundle = new Bundle();
      bundle.putLong("parent_id", parentId);
      int groupPos = groupCursor.getPosition();
      if (mManager.getLoader(groupPos) != null && !mManager.getLoader(groupPos).isReset()) {
        try {
          mManager.restartLoader(groupPos, bundle, CategoryList.this);
        } catch (NullPointerException e) {
          // a NPE is thrown in the following scenario:
          //1)open a group
          //2)orientation change
          //3)open the same group again
          //in this scenario getChildrenCursor is called twice, second time leads to error
          //maybe it is trying to close the group that had been kept open before the orientation change
          e.printStackTrace();
        }
      } else {
        mManager.initLoader(groupPos, bundle, CategoryList.this);
      }
      return null;
    }

    @Override
    public void setViewText(TextView v, String text) {
      switch (v.getId()) {
        case R.id.amount:
          v.setTextColor(Long.valueOf(text) < 0 ? colorExpense : colorIncome);
          text = Utils.convAmount(text, mAccount.currency);
      }
      super.setViewText(v, text);
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
      convertView = super.getGroupView(groupPosition, isExpanded, convertView, parent);
      View colorView = convertView.findViewById(R.id.color1);
      if (showChart) {
        colorView.setBackgroundColor(mMainColors.get(groupPosition % mMainColors.size()));
      } else {
        colorView.setVisibility(View.GONE);
      }
      return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
      convertView = super.getChildView(groupPosition, childPosition, isLastChild,
          convertView, parent);
      View colorView = convertView.findViewById(R.id.color1);
      if (showChart) {
        colorView.setBackgroundColor(mSubColors.get(childPosition % mSubColors.size()));
      } else {
        colorView.setVisibility(View.GONE);
      }
      return convertView;
    }
  }

  private String buildGroupingClause() {
    String year = YEAR + " = " + mGroupingYear;
    switch (mGrouping) {
      case YEAR:
        return year;
      case DAY:
        return year + " AND " + DAY + " = " + mGroupingSecond;
      case WEEK:
        return getYearOfWeekStart() + " = " + mGroupingYear + " AND " + getWeek() + " = " + mGroupingSecond;
      case MONTH:
        return getYearOfMonthStart() + " = " + mGroupingYear + " AND " + getMonth() + " = " + mGroupingSecond;
      default:
        return null;
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    if (getActivity() == null)
      return null;
    if (id == SUM_CURSOR) {
      Builder builder = TransactionProvider.TRANSACTIONS_SUM_URI.buildUpon();
      if (mAccount.getId() < 0) {
        builder.appendQueryParameter(KEY_CURRENCY, mAccount.currency.getCurrencyCode());
      } else {
        builder.appendQueryParameter(KEY_ACCOUNTID, String.valueOf(mAccount.getId()));
      }
      return new CursorLoader(
          getActivity(),
          builder.build(),
          null,
          buildGroupingClause(),
          null,
          null);
    }
    if (id == DATEINFO_CURSOR) {
      ArrayList<String> projectionList = new ArrayList<>(Arrays.asList(
              new String[]{
                      getThisYearOfWeekStart() + " AS " + KEY_THIS_YEAR_OF_WEEK_START,
                      THIS_YEAR + " AS " + KEY_THIS_YEAR,
                      THIS_MONTH + " AS " + KEY_THIS_MONTH,
                      getThisWeek() + " AS " + KEY_THIS_WEEK,
                      THIS_DAY + " AS " + KEY_THIS_DAY
              }
      ));
      //if we are at the beginning of the year we are interested in the max of the previous year
      int yearToLookUp = mGroupingSecond == 1 ? mGroupingYear - 1 : mGroupingYear;
      switch (mGrouping) {
        case DAY:
          projectionList.add(String.format(Locale.US, "strftime('%%j','%d-12-31') AS " + KEY_MAX_VALUE, yearToLookUp));
          break;
        case WEEK:
          projectionList.add(String.format(Locale.US, "strftime('%%W','%d-12-31') AS " + KEY_MAX_VALUE, yearToLookUp));
          break;
        case MONTH:
          projectionList.add("12 as " + KEY_MAX_VALUE);
          break;
        default:
          projectionList.add("0 as " + KEY_MAX_VALUE);
      }
      if (mGrouping.equals(Grouping.WEEK)) {
        //we want to find out the week range when we are given a week number
        //we find out the first Monday in the year, which is the beginning of week 1 and than
        //add (weekNumber-1)*7 days to get at the beginning of the week
        projectionList.add(DbUtils.weekStartFromGroupSqlExpression(mGroupingYear, mGroupingSecond));
        projectionList.add(DbUtils.weekEndFromGroupSqlExpression(mGroupingYear, mGroupingSecond));
      }
      return new CursorLoader(getActivity(),
          TransactionProvider.DUAL_URI,
          projectionList.toArray(new String[projectionList.size()]),
          null, null, null);
    }
    //SORTABLE_CURSOR
    long parentId;
    String selection = "", accountSelector = "", sortOrder = null;
    String[] selectionArgs, projection;
    String CATTREE_WHERE_CLAUSE = KEY_CATID + " IN (SELECT " +
        TABLE_CATEGORIES + "." + KEY_ROWID +
        " UNION SELECT " + KEY_ROWID + " FROM "
        + TABLE_CATEGORIES + " subtree WHERE " + KEY_PARENTID + " = " + TABLE_CATEGORIES + "." + KEY_ROWID + ")";
    String CHILD_COUNT_SELECT = "(select count(*) FROM " + TABLE_CATEGORIES
        + " subtree where " + KEY_PARENTID + " = " + TABLE_CATEGORIES + "." + KEY_ROWID + ") as "
        + KEY_CHILD_COUNT;
    String catFilter;
    if (mAccount != null) {
      //Distribution
      String accountSelection;
      if (mAccount.getId() < 0) {
        accountSelection = " IN " +
            "(SELECT " + KEY_ROWID + " from " + TABLE_ACCOUNTS + " WHERE " + KEY_CURRENCY + " = ? AND " +
            KEY_EXCLUDE_FROM_TOTALS + " = 0 )";
        accountSelector = mAccount.currency.getCurrencyCode();
      } else {
        accountSelection = " = ?";
        accountSelector = String.valueOf(mAccount.getId());
      }
      catFilter = "FROM " + VIEW_COMMITTED +
          " WHERE " + WHERE_NOT_VOID + " AND " + KEY_ACCOUNTID + accountSelection;
      if (!aggregateTypes) {
        catFilter += " AND " + KEY_AMOUNT + (mType == EXPENSE ? "<" : ">") + "0";
      }
      if (!mGrouping.equals(Grouping.NONE)) {
        catFilter += " AND " + buildGroupingClause();
      }
      //we need to include transactions mapped to children for main categories
      if (bundle == null) {
        catFilter += " AND " + CATTREE_WHERE_CLAUSE;
      } else {
        catFilter += " AND " + KEY_CATID + "  = " + TABLE_CATEGORIES + "." + KEY_ROWID;
      }
      selection = " AND exists (SELECT 1 " + catFilter + ")";
      projection = new String[]{
          KEY_ROWID,
          KEY_LABEL,
          CHILD_COUNT_SELECT,
          "(SELECT sum(amount) " + catFilter + ") AS " + KEY_SUM
      };
      sortOrder = "abs(" + KEY_SUM + ") DESC";
    } else {
      //manage, select
      if (bundle == null) {
        catFilter = CATTREE_WHERE_CLAUSE;
      } else {
        catFilter = KEY_CATID + "  = " + TABLE_CATEGORIES + "." + KEY_ROWID;
      }
      projection = new String[]{
          KEY_ROWID,
          KEY_LABEL,
          CHILD_COUNT_SELECT,
          //here we do not filter out void transactinos since they need to be considered as mapped
          "(select 1 FROM " + TABLE_TRANSACTIONS + " WHERE " + catFilter + ") AS " + DatabaseConstants.KEY_MAPPED_TRANSACTIONS,
          "(select 1 FROM " + TABLE_TEMPLATES + " WHERE " + catFilter + ") AS " + DatabaseConstants.KEY_MAPPED_TEMPLATES
      };
    }
    boolean isFiltered = !TextUtils.isEmpty(mFilter);
    String filterSelection = KEY_LABEL_NORMALIZED + " LIKE ?";
    String[] filterSelectArgs = {"%" + mFilter + "%", "%" + mFilter + "%"};
    if (bundle == null) {
      //group cursor
      selection = KEY_PARENTID + " is null" + selection;
      if (isFiltered) {
        selection += " AND (" + filterSelection + " OR EXISTS (SELECT 1 FROM " + TABLE_CATEGORIES +
            " subtree WHERE " + KEY_PARENTID + " = " + TABLE_CATEGORIES + "." + KEY_ROWID + " AND ("
            + filterSelection + " )))";
      }
      selectionArgs = mAccount != null ? new String[]{accountSelector, accountSelector} :
          (isFiltered ? filterSelectArgs : null);
    } else {
      //child cursor
      parentId = bundle.getLong(KEY_PARENTID);
      selection = KEY_PARENTID + " = ?" + selection;
      if (isFiltered) {
        selection += " AND (" + filterSelection + " OR (SELECT " + KEY_LABEL_NORMALIZED + " FROM " +
            TABLE_CATEGORIES + " parent WHERE " + KEY_ROWID + " = " + TABLE_CATEGORIES + "." +
            KEY_PARENTID + ") LIKE ?)";
      }
      selectionArgs = mAccount != null ?
          new String[]{accountSelector, String.valueOf(parentId), accountSelector} :
          (isFiltered ? Utils.joinArrays(new String[]{String.valueOf(parentId)}, filterSelectArgs) :
              new String[]{String.valueOf(parentId)});
    }
    return new CursorLoader(getActivity(), TransactionProvider.CATEGORIES_URI, projection,
        selection, selectionArgs, sortOrder);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
    if (getActivity() == null)
      return;
    int id = loader.getId();
    ProtectedFragmentActivity ctx = (ProtectedFragmentActivity) getActivity();
    ActionBar actionBar = ctx.getSupportActionBar();
    switch (id) {
      case SUM_CURSOR:
        boolean[] seen = new boolean[2];
        c.moveToFirst();
        while (c.isAfterLast() == false) {
          int type = c.getInt(c.getColumnIndex(KEY_TYPE));
          updateSum(type > 0 ? "+ " : "- ",
              type > 0 ? incomeSumTv : expenseSumTv,
              c.getLong(c.getColumnIndex(KEY_SUM)));
          c.moveToNext();
          seen[type] = true;
        }
        //if we have no income or expense, there is no row in the cursor
        if (!seen[1]) updateSum("+ ", incomeSumTv, 0);
        if (!seen[0]) updateSum("- ", expenseSumTv, 0);
        break;
      case DATEINFO_CURSOR:
        c.moveToFirst();
        actionBar.setSubtitle(mGrouping.getDisplayTitle(ctx,
            mGroupingYear, mGroupingSecond, c));
        thisYear = c.getInt(c.getColumnIndex(KEY_THIS_YEAR));
        thisMonth = c.getInt(c.getColumnIndex(KEY_THIS_MONTH));
        thisWeek = c.getInt(c.getColumnIndex(KEY_THIS_WEEK));
        thisDay = c.getInt(c.getColumnIndex(KEY_THIS_DAY));
        maxValue = c.getInt(c.getColumnIndex(KEY_MAX_VALUE));
        break;
      case SORTABLE_CURSOR:
        mGroupCursor = c;
        mAdapter.setGroupCursor(c);
        if (ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
          if (c.getCount() > 0) {
            if (lastExpandedPosition == -1) {
              mChart.setVisibility(showChart ? View.VISIBLE : View.GONE);
              setData(c, mMainColors);
              highlight(0);
              if (showChart)
                mListView.setItemChecked(mListView.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(0)), true);
            }
          } else {
            mChart.setVisibility(View.GONE);
          }
        }
        if (mAccount != null) {
          actionBar.setTitle(mAccount.label);
        }
        invalidateCAB(); //only need to do this for group since children's cab does not depnd on cursor
        break;
      default:
        //check if group still exists
        if (mAdapter.getGroupId(id) != 0) {
          mAdapter.setChildrenCursor(id, c);
          if (ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
            long packedPosition;
            if (c.getCount() > 0) {
              mSubColors = getSubColors(mMainColors.get(id % mMainColors.size()));
              setData(c, mSubColors);
              highlight(0);
              packedPosition =
                  ExpandableListView.getPackedPositionForChild(id, 0);
            } else {
              packedPosition =
                  ExpandableListView.getPackedPositionForGroup(id);
              if (!chartDisplaysSubs) {//check if a loader running concurrently has already switched to subs
                highlight(id);
              }
            }
            if (showChart && id == lastExpandedPosition) {
              //if user has expanded a new group before loading is finished, getFlatListPosition
              //would run in an NPE
              mListView.setItemChecked(mListView.getFlatListPosition(packedPosition), true);
            }
          }
        }
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    int id = loader.getId();
    if (id == SORTABLE_CURSOR) {
      mGroupCursor = null;
      mAdapter.setGroupCursor(null);
    } else if (id > 0) {
      // child cursor
      try {
        mAdapter.setChildrenCursor(id, null);
      } catch (NullPointerException e) {
        Log.w("TAG", "Adapter expired, try again on the next query: "
            + e.getMessage());
      }
    }
  }

  @Override
  protected PrefKey getSortOrderPrefKey() {
    return PrefKey.SORT_ORDER_CATEGORIES;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    ManageCategories ctx = (ManageCategories) getActivity();
    if (ctx == null) return;
    
    if (!ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
      inflater.inflate(R.menu.search, menu);
      SearchManager searchManager =
          (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
      MenuItem searchMenuItem = menu.findItem(R.id.search);
      SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);

      searchView.setSearchableInfo(searchManager.
          getSearchableInfo(getActivity().getComponentName()));
      //searchView.setIconifiedByDefault(true);
      searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
          return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
          if (TextUtils.isEmpty(newText)) {
            mFilter = "";
            mImportButton.setVisibility(View.VISIBLE);
          } else {
            mFilter = Utils.esacapeSqlLikeExpression(Utils.normalize(newText));
            // if a filter results in an empty list,
            // we do not want to show the setup default categories button
            mImportButton.setVisibility(View.GONE);
          }
          collapseAll();
          mManager.restartLoader(SORTABLE_CURSOR, null, CategoryList.this);
          return true;
        }
      });
    }
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    if (mGrouping != null) {
      Utils.configureGroupingMenu(menu.findItem(R.id.GROUPING_COMMAND).getSubMenu(), mGrouping);
      boolean grouped = !mGrouping.equals(Grouping.NONE);
      Utils.menuItemSetEnabledAndVisible(menu.findItem(R.id.FORWARD_COMMAND), grouped);
      Utils.menuItemSetEnabledAndVisible(menu.findItem(R.id.BACK_COMMAND), grouped);
    }
    MenuItem m = menu.findItem(R.id.TOGGLE_CHART_COMMAND);
    if (m != null) {
      m.setChecked(showChart);
    }
    m = menu.findItem(R.id.TOGGLE_AGGREGATE_TYPES);
    if (m != null) {
      m.setChecked(aggregateTypes);
      Utils.menuItemSetEnabledAndVisible(menu.findItem(R.id.switchId), !aggregateTypes);
    }

    MenuItem searchMenuItem = menu.findItem(R.id.search);
    if (searchMenuItem != null && mFilter != null) {
      SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
      searchView.setQuery(mFilter, false);
      searchView.setIconified(false);
      searchView.clearFocus();
    }
  }

  public void back() {
    if (mGrouping.equals(Grouping.YEAR))
      mGroupingYear--;
    else {
      mGroupingSecond--;
      if (mGroupingSecond < 1) {
        mGroupingYear--;
        mGroupingSecond = maxValue;
      }
    }
    reset();
  }

  public void forward() {
    if (mGrouping.equals(Grouping.YEAR))
      mGroupingYear++;
    else {
      mGroupingSecond++;
      if (mGroupingSecond > maxValue) {
        mGroupingYear++;
        mGroupingSecond = 1;
      }
    }
    reset();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (handleGrouping(item)) return true;
    switch (item.getItemId()) {
      case R.id.BACK_COMMAND:
        back();
        return true;
      case R.id.FORWARD_COMMAND:
        forward();
        return true;
      case R.id.TOGGLE_CHART_COMMAND:
        showChart = !showChart;
        PrefKey.DISTRIBUTION_SHOW_CHART.putBoolean(showChart);
        mChart.setVisibility(showChart ? View.VISIBLE : View.GONE);
        if (showChart) {
          collapseAll();
        } else {
          mListView.setItemChecked(mListView.getCheckedItemPosition(), false);
        }
        return true;
      case R.id.TOGGLE_AGGREGATE_TYPES:
        aggregateTypes = !aggregateTypes;
        PrefKey.DISTRIBUTION_AGGREGATE_TYPES.putBoolean(aggregateTypes);
        getActivity().supportInvalidateOptionsMenu();
        reset();
        return true;
    }
    return handleSortOption(item);
  }

  private boolean handleGrouping(MenuItem item) {
    Grouping newGrouping = Utils.getGroupingFromMenuItemId(item.getItemId());
    if (newGrouping != null) {
      if (!item.isChecked()) {
        setGrouping(newGrouping);
      }
      return true;
    }
    return false;
  }

  public void collapseAll() {
    int count = mAdapter.getGroupCount();
    for (int i = 0; i < count; i++)
      mListView.collapseGroup(i);
  }

  /*     (non-Javadoc)
   * return the sub cat to the calling activity
   * @see android.app.ExpandableListActivity#onChildClick(android.widget.ExpandableListView, android.view.View, int, int, long)
*/
  @Override
  public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
    if (super.onChildClick(parent, v, groupPosition, childPosition, id))
      return true;
    ManageCategories ctx = (ManageCategories) getActivity();
    if (ctx == null || ctx.helpVariant.equals(ManageCategories.HelpVariant.manage)) {
      return false;
    }
    String label = ((TextView) v.findViewById(R.id.label)).getText().toString();
    if (ctx.helpVariant.equals(ManageCategories.HelpVariant.select_mapping)) {
      mGroupCursor.moveToPosition(groupPosition);
      label = mGroupCursor.getString(mGroupCursor.getColumnIndex(KEY_LABEL))
          + TransactionList.CATEGORY_SEPARATOR
          + label;
    }
    doSelection(id, label, false);
    return true;
  }

  @Override
  public boolean onGroupClick(ExpandableListView parent, View v,
                              int groupPosition, long id) {
    if (super.onGroupClick(parent, v, groupPosition, id))
      return true;
    ManageCategories ctx = (ManageCategories) getActivity();
    if (ctx == null || ctx.helpVariant.equals(ManageCategories.HelpVariant.manage)) {
      return false;
    }
    long cat_id = id;
    mGroupCursor.moveToPosition(groupPosition);
    if (mGroupCursor.getInt(mGroupCursor.getColumnIndex(KEY_CHILD_COUNT)) > 0)
      return false;
    String label = ((TextView) v.findViewById(R.id.label)).getText().toString();
    doSelection(cat_id, label, true);
    return true;
  }

  private void doSelection(long cat_id, String label, boolean isMain) {
    ManageCategories ctx = (ManageCategories) getActivity();
    if (ctx.helpVariant.equals(ManageCategories.HelpVariant.distribution)) {
      TransactionListDialogFragment.newInstance(
          mAccount.getId(), cat_id, isMain, mGrouping, buildGroupingClause(), label)
          .show(getFragmentManager(), TransactionListDialogFragment.class.getName());
      return;
    }
    Intent intent = new Intent();
    intent.putExtra(KEY_CATID, cat_id);
    intent.putExtra(KEY_LABEL, label);
    ctx.setResult(ManageCategories.RESULT_OK, intent);
    ctx.finish();
  }

  public void setGrouping(Grouping grouping) {
    mGrouping = grouping;
    mGroupingYear = thisYear;
    switch (grouping) {
      case NONE:
        mGroupingYear = 0;
        break;
      case DAY:
        mGroupingSecond = thisDay;
        break;
      case WEEK:
        mGroupingSecond = thisWeek;
        break;
      case MONTH:
        mGroupingSecond = thisMonth;
        break;
      case YEAR:
        mGroupingSecond = 0;
        break;
    }
    getActivity().supportInvalidateOptionsMenu();
    reset();
  }

  public void reset() {
//TODO: would be nice to retrieve the same open groups on the next or previous group
//the following does not work since the groups will not necessarily stay the same
//      if (mListView.isGroupExpanded(i)) {
//        mGroupCursor.moveToPosition(i);
//        long parentId = mGroupCursor.getLong(mGroupCursor.getColumnIndexOrThrow(KEY_ROWID));
//        Bundle bundle = new Bundle();
//        bundle.putLong("parent_id", parentId);
//        mManager.restartLoader(i, bundle, CategoryList.this);
//      }
    collapseAll();
    mManager.restartLoader(SORTABLE_CURSOR, null, this);
    mManager.restartLoader(SUM_CURSOR, null, this);
    mManager.restartLoader(DATEINFO_CURSOR, null, this);
  }

  private void updateSum(String prefix, TextView tv, long amount) {
    if (tv != null) {
      //noinspection SetTextI18n
      tv.setText(prefix + Utils.formatCurrency(
          new Money(mAccount.currency, amount)));
    }
  }

  private void updateColor() {
    if (bottomLine != null)
      bottomLine.setBackgroundColor(mAccount.color);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable("grouping", mGrouping);
    outState.putInt("groupingYear", mGroupingYear);
    outState.putInt("groupingSecond", mGroupingSecond);
    if (!TextUtils.isEmpty(mFilter)) {
      outState.putString("filter", mFilter);
    }
  }

  @Override
  protected void configureMenu(Menu menu, int count, int listId) {
    ManageCategories ctx = (ManageCategories) getActivity();
    if (ctx == null) {
      return;
    }
    boolean inGroup = expandableListSelectionType == ExpandableListView.PACKED_POSITION_TYPE_GROUP;
    boolean inFilterOrDistribution = ctx.helpVariant.equals(HelpVariant.select_filter) ||
        ctx.helpVariant.equals(HelpVariant.distribution);
    menu.findItem(R.id.EDIT_COMMAND).setVisible(count == 1 && !inFilterOrDistribution);
    menu.findItem(R.id.DELETE_COMMAND).setVisible(!inFilterOrDistribution);
    menu.findItem(R.id.MOVE_COMMAND).setVisible(!inFilterOrDistribution);
    MenuItem menuItem = menu.findItem(R.id.SELECT_COMMAND);
    menuItem.setVisible(count == 1 &&
        (ctx.helpVariant.equals(HelpVariant.distribution) || ctx.helpVariant.equals(HelpVariant.select_mapping)));
    if (ctx.helpVariant.equals(HelpVariant.distribution)) {
      menuItem.setTitle(R.string.menu_show_transactions);
    }
    menu.findItem(R.id.SELECT_COMMAND_MULTIPLE).setVisible(ctx.helpVariant.equals(HelpVariant.select_filter));
    menu.findItem(R.id.CREATE_COMMAND).setVisible(inGroup && count == 1 && !inFilterOrDistribution);
  }

  @Override
  protected void configureMenuLegacy(Menu menu, ContextMenu.ContextMenuInfo menuInfo, int listId) {
    super.configureMenuLegacy(menu, menuInfo, listId);
    if (expandableListSelectionType == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
      ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
      int groupPos = ExpandableListView.getPackedPositionGroup(info.packedPosition);
      configureMenuInternal(menu, hasChildren(groupPos));
    }
  }

  @Override
  protected void configureMenu11(Menu menu, int count, AbsListView lv) {
    super.configureMenu11(menu, count, lv);
    if (expandableListSelectionType == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
      SparseBooleanArray checkedItemPositions = mListView.getCheckedItemPositions();
      boolean hasChildren = false;
      for (int i = 0; i < checkedItemPositions.size(); i++) {
        if (checkedItemPositions.valueAt(i)) {
          int position = checkedItemPositions.keyAt(i);
          long pos = mListView.getExpandableListPosition(position);
          int groupPos = ExpandableListView.getPackedPositionGroup(pos);
          if (hasChildren(groupPos)) {
            hasChildren = true;
            break;
          }
        }
      }
      configureMenuInternal(menu, hasChildren);
    }
  }

  private boolean hasChildren(int position) {
    if (position != -1 && mGroupCursor != null) {
      mGroupCursor.moveToPosition(position);
      return mGroupCursor.getInt(mGroupCursor.getColumnIndex(KEY_CHILD_COUNT)) > 0;
    }
    return false;
  }

  private void configureMenuInternal(Menu menu, boolean hasChildren) {
    menu.findItem(R.id.MOVE_COMMAND).setVisible(!hasChildren);
  }

  public void setType(boolean isChecked) {
    mType = isChecked;
    reset();
  }

  private void setData(Cursor c, ArrayList<Integer> colors) {
    chartDisplaysSubs = c != mGroupCursor;
    ArrayList<Entry> entries1 = new ArrayList<>();
    ArrayList<String> xVals = new ArrayList<>();
    if (c != null && c.moveToFirst()) {
      do {
        long sum = c.getLong(c.getColumnIndex(DatabaseConstants.KEY_SUM));
        xVals.add(c.getString(c.getColumnIndex(DatabaseConstants.KEY_LABEL)));
        entries1.add(
            new Entry(
                (float) sum,
                c.getPosition()));
      } while (c.moveToNext());
      PieDataSet ds1 = new PieDataSet(entries1, "");

      ds1.setColors(colors);
      ds1.setSliceSpace(2f);
      ds1.setDrawValues(false);
      mChart.setData(new PieData(xVals, ds1));
      mChart.getLegend().setEnabled(false);
      // undo all highlights
      mChart.highlightValues(null);
      mChart.invalidate();
    } else {
      mChart.clear();
    }
  }

  private ArrayList<Integer> getSubColors(int color) {
    //inspired by http://highintegritydesign.com/tools/tinter-shader/scripts/shader-tinter.js
    return MyApplication.getThemeType().equals(ThemeType.dark) ?
        getTints(color) : getShades(color);

  }

  private ArrayList<Integer> getShades(int color) {
    ArrayList<Integer> result = new ArrayList<>();
    int red = Color.red(color);
    int redDecrement = (int) Math.round(red * 0.1);
    int green = Color.green(color);
    int greenDecrement = (int) Math.round(green * 0.1);
    int blue = Color.blue(color);
    int blueDecrement = (int) Math.round(blue * 0.1);
    for (int i = 0; i < 10; i++) {
      red = red - redDecrement;
      if (red <= 0) {
        red = 0;
      }
      green = green - greenDecrement;
      if (green <= 0) {
        green = 0;
      }
      blue = blue - blueDecrement;
      if (blue <= 0) {
        blue = 0;
      }
      result.add(Color.rgb(red, green, blue));
    }
    result.add(Color.BLACK);
    return result;
  }

  private ArrayList<Integer> getTints(int color) {
    ArrayList<Integer> result = new ArrayList<>();
    int red = Color.red(color);
    int redIncrement = (int) Math.round((255 - red) * 0.1);
    int green = Color.green(color);
    int greenIncrement = (int) Math.round((255 - green) * 0.1);
    int blue = Color.blue(color);
    int blueIncrement = (int) Math.round((255 - blue) * 0.1);
    for (int i = 0; i < 10; i++) {
      red = red + redIncrement;
      if (red >= 255) {
        red = 255;
      }
      green = green + greenIncrement;
      if (green >= 255) {
        red = 255;
      }
      blue = blue + blueIncrement;
      if (blue >= 255) {
        red = 255;
      }
      result.add(Color.rgb(red, green, blue));
    }
    result.add(Color.WHITE);
    return result;
  }

  private void highlight(int position) {
    mChart.highlightValue(position, 0);
    if (position != -1)
      setCenterText(position);
  }

  private void setCenterText(int position) {
    PieData data = mChart.getData();

    String description = data.getXVals().get(position);

    String value = data.getDataSet().getValueFormatter().getFormattedValue(
        Math.abs(mChart.getPercentOfTotal(data.getDataSet().getEntryForXIndex(position).getVal())))
        + " %";

    mChart.setCenterText(
        description + "\n" +
            value
    );
  }
}
