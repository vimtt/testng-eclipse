package org.testng.eclipse.ui;

import static org.testng.eclipse.ui.Images.IMG_SUITE;
import static org.testng.eclipse.ui.Images.IMG_SUITE_FAIL;
import static org.testng.eclipse.ui.Images.IMG_SUITE_OK;
import static org.testng.eclipse.ui.Images.IMG_SUITE_RUN;
import static org.testng.eclipse.ui.Images.IMG_SUITE_SKIP;
import static org.testng.eclipse.ui.Images.IMG_TEST;
import static org.testng.eclipse.ui.Images.IMG_TEST_FAIL;
import static org.testng.eclipse.ui.Images.IMG_TEST_HIERARCHY;
import static org.testng.eclipse.ui.Images.IMG_TEST_OK;
import static org.testng.eclipse.ui.Images.IMG_TEST_RUN;
import static org.testng.eclipse.ui.Images.IMG_TEST_SKIP;

import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.testng.eclipse.TestNGPlugin;
import org.testng.eclipse.collections.Maps;
import org.testng.eclipse.collections.Sets;
import org.testng.eclipse.ui.tree.BaseTreeItem;
import org.testng.eclipse.ui.tree.ClassTreeItem;
import org.testng.eclipse.ui.tree.ITreeItem;
import org.testng.eclipse.ui.tree.SuiteTreeItem;
import org.testng.eclipse.ui.tree.TestMethodTreeItem;
import org.testng.eclipse.ui.tree.TestTreeItem;
import org.testng.eclipse.util.ResourceUtil;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class is responsible for the tree display in the runner view part. It
 * has two subclasses, SuccessTab and FailureTab. Whenever a new test result
 * message arrives from RemoteTestNG, updateTestResult() is invoked, which then
 * creates or updates the corresponding node in the tree.
 *
 * @author Cedric Beust <cedric@beust.com>
 */
abstract public class AbstractTab extends TestRunTab implements IMenuListener {
  private final Image m_suiteIcon = Images.getImage(IMG_SUITE);
  private final Image m_suiteOkeIcon = Images.getImage(IMG_SUITE_OK);
  private final Image m_suiteSkipIcon = Images.getImage(IMG_SUITE_SKIP);
  private final Image m_suiteFailIcon = Images.getImage(IMG_SUITE_FAIL);
  private final Image m_suiteRunIcon = Images.getImage(IMG_SUITE_RUN);

  private final Image m_testHierarchyIcon = Images.getImage(IMG_TEST_HIERARCHY); 
  private final Image m_testIcon = Images.getImage(IMG_TEST);
  private final Image m_testOkeIcon = Images.getImage(IMG_TEST_OK);
  private final Image m_testSkipIcon = Images.getImage(IMG_TEST_SKIP);
  private final Image m_testFailIcon = Images.getImage(IMG_TEST_FAIL);
  private final Image m_testRunIcon = Images.getImage(IMG_TEST_RUN);
  private final Image m_stackViewIcon = TestNGPlugin.getImageDescriptor("eview16/stackframe.gif")
      .createImage(); //$NON-NLS-1$

  /** The component that displays the stack trace when an item is selected */
  private FailureTrace m_failureTraceComponent;

  private Tree m_tree;
  private TestRunnerViewPart m_testRunnerPart;

  @Override
  public String getSelectedTestId() {
    TreeItem[] treeItems = m_tree.getSelection();
    if (treeItems == null || treeItems.length == 0) {
      return null;
    } else {
      return BaseTreeItem.getTreeItem(treeItems[0]).getRunInfo().getId();
    }
  }

  @Override
  public void createTabControl(CTabFolder tabFolder, TestRunnerViewPart runner) {
    m_testRunnerPart = runner;

    CTabItem hierarchyTab = new CTabItem(tabFolder, SWT.NONE);
    hierarchyTab.setText(getName());
    hierarchyTab.setImage(m_testHierarchyIcon);

    Composite treeComposite = new Composite(tabFolder, SWT.NONE);
    GridLayout gridLayout = new GridLayout();
    gridLayout.marginHeight = 0;
    gridLayout.marginWidth = 0;
    gridLayout.numColumns = 3;
    treeComposite.setLayout(gridLayout);

    GridData gridData = new GridData(GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL);
    treeComposite.setLayoutData(gridData);

    hierarchyTab.setControl(treeComposite);
    hierarchyTab.setToolTipText(ResourceUtil.getString(getTooltipKey())); //$NON-NLS-1$

    // The sash is the parent of both the tree and the stack trace component
    SashForm m_sashForm = new SashForm(treeComposite, SWT.HORIZONTAL);
    m_sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    //
    // Tree
    //
    m_tree = new Tree(m_sashForm, SWT.V_SCROLL | SWT.SINGLE);

    //
    // Stack trace (FailureComponent)
    //
    ViewForm stackTraceForm = new ViewForm(m_sashForm, SWT.NONE);
    stackTraceForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    CLabel label = new CLabel(stackTraceForm, SWT.NONE);
    label.setText(ResourceUtil.getString("TestRunnerViewPart.label.failure")); //$NON-NLS-1$
    label.setImage(m_stackViewIcon);
    stackTraceForm.setTopLeft(label);

    ToolBar failureToolBar = new ToolBar(stackTraceForm, SWT.FLAT | SWT.WRAP);
    stackTraceForm.setTopCenter(failureToolBar);
    m_failureTraceComponent = new FailureTrace(stackTraceForm, m_testRunnerPart, failureToolBar);
    stackTraceForm.setContent(m_failureTraceComponent.getComposite());

    m_sashForm.setWeights(new int[] { 50, 50 });

    initMenu();
    addListeners();
  }

  private void initMenu() {
    MenuManager menuMgr = new MenuManager();
    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(this);

    Menu menu = menuMgr.createContextMenu(m_tree);
    m_tree.setMenu(menu);
  }
  
  private void addListeners() {
    m_tree.addSelectionListener(new SelectionListener() {
      public void widgetSelected(SelectionEvent e) {
        activate();
      }

      public void widgetDefaultSelected(SelectionEvent e) {
        activate();
      }
    });

    m_tree.addDisposeListener(new DisposeListener() {
      public void widgetDisposed(DisposeEvent e) {
        disposeIcons();
      }
    });

    m_tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent e) {
        handleDoubleClick(e);
      }
    });
  }

  void disposeIcons() {
    m_suiteIcon.dispose();
    m_suiteOkeIcon.dispose();
    m_suiteFailIcon.dispose();
    m_suiteSkipIcon.dispose();
    m_suiteRunIcon.dispose();

    m_testHierarchyIcon.dispose();
    m_testIcon.dispose();
    m_testOkeIcon.dispose();
    m_testFailIcon.dispose();
    m_testSkipIcon.dispose();
    m_testRunIcon.dispose();

    m_stackViewIcon.dispose();
}

  void handleDoubleClick(MouseEvent e) {
    ITreeItem testInfo = getSelectedTreeItem();

    if (null == testInfo) {
      return;
    }

    if (testInfo instanceof TestMethodTreeItem) {
      OpenTestAction action = new OpenTestAction(m_testRunnerPart, testInfo.getRunInfo());
      
      if(action.isEnabled()) {
        action.run();
      }
    }
  }

  /**
   * @return the RunInfo associated with the current selection, or null it not applicable.
   */
  private ITreeItem getSelectedTreeItem() {
    TreeItem[] treeItems= m_tree.getSelection();
    
    return treeItems.length == 0 ? null
        : BaseTreeItem.getTreeItem(treeItems[0]);
  }

  /**
   * @see org.eclipse.jface.action.IMenuListener#menuAboutToShow(org.eclipse.jface.action.IMenuManager)
   */
  public void menuAboutToShow(IMenuManager manager) {
    if(m_tree.getSelectionCount() > 0) {
      TreeItem treeItem = m_tree.getSelection()[0];
      RunInfo testInfo = BaseTreeItem.getTreeItem(treeItem).getRunInfo();

      manager.add(new OpenTestAction(m_testRunnerPart, testInfo));
      manager.add(new Separator());
      manager.add(new QuickRunAction(m_testRunnerPart.getLaunchedProject(), 
          m_testRunnerPart.getLastLaunch(),
          testInfo,
          ILaunchManager.RUN_MODE));
      manager.add(new QuickRunAction(m_testRunnerPart.getLaunchedProject(),
          m_testRunnerPart.getLastLaunch(),
          testInfo,
          ILaunchManager.DEBUG_MODE));
      manager.add(new Separator());
      manager.add(new ExpandAllAction());
    }
  }

  protected abstract String getTooltipKey();
  
  private Map<String, ITreeItem> m_treeItemMap = Maps.newHashMap();
  private Set<RunInfo> m_runInfos = Sets.newHashSet();
  private String m_searchFilter = "";

  private String getId(RunInfo runInfo) {
    return runInfo.getSuiteName() + "." + runInfo.getTestName() + "." + runInfo.getClassName()
        + "." + runInfo.getMethodName();
  }

  @Override
  public void updateTestResult(RunInfo runInfo) {
    m_runInfos.add(runInfo);
    privateUpdateTestResult(runInfo);
  }

  private void privateUpdateTestResult(RunInfo runInfo) {
    // Check the search filter here as well so that results that are still coming
    // in from RemoteTestNG get tested against it as well
    if (acceptTestResult(runInfo) && matchesSearchFilter(runInfo)) {
      p("New result: " + runInfo);
      String id = getId(runInfo);
      ITreeItem iti = m_treeItemMap.get(id);
      TreeItem ti;
      TreeItem parentItem = null;
      if (iti == null) {
        parentItem = maybeCreateParents(runInfo).getTreeItem();
        iti = new TestMethodTreeItem(parentItem, runInfo);
        ti = iti.getTreeItem();
        registerTreeItem(id, iti);
      } else {
        ti = iti.getTreeItem();
        parentItem = ti.getParentItem();
      }
      propagateTestResult(parentItem, runInfo);
    } else {
      p("Excluded " + runInfo + " reason:" + acceptTestResult(runInfo) + " "
          + matchesSearchFilter(runInfo));
    }

    postExpandAll();
  }

  @Override
  public void updateSearchFilter(String text) {
    m_tree.removeAll();
    m_treeItemMap.clear();
    m_searchFilter = text;
    for (final RunInfo runInfo : m_runInfos) {
      if (matchesSearchFilter(runInfo)) {
        privateUpdateTestResult(runInfo);
      }
    }
  }

  private boolean matchesSearchFilter(RunInfo runInfo) {
    if ("".equals(m_searchFilter)) return true;
    else return Pattern.matches(".*" + m_searchFilter + ".*", runInfo.getMethodDisplay());
  }

  /**
   * Override in subclasses to filter out nodes.
   */
  protected boolean acceptTestResult(RunInfo runInfo) {
    return true;
  }

  private void propagateTestResult(TreeItem ti, RunInfo trm) {
    ITreeItem treeItem = BaseTreeItem.getTreeItem(ti);
    treeItem.addToCumulatedTime(trm.getTime());
    treeItem.update(trm);

    if (ti.getParentItem() != null) {
      propagateTestResult(ti.getParentItem(), trm);
    }
  }

  private void p(String string) {
    if (false) {
      System.out.println("[AbstractTab] " + string);
    }
  }

  /**
   * @return the parent tree item for this ResultInfo, possibly creating all the
   * other parents if they don't exist yet.
   */
  private ITreeItem maybeCreateParents(RunInfo trm) {
    String suiteId = trm.getSuiteName();
    ITreeItem suiteTreeItem = m_treeItemMap.get(suiteId);
    if (suiteTreeItem == null) {
      suiteTreeItem = new SuiteTreeItem(m_tree, trm);
      registerTreeItem(suiteId, suiteTreeItem);
    }

    String testId = suiteId + "." + trm.getTestName();
    ITreeItem testTreeItem = m_treeItemMap.get(testId);
    if (testTreeItem == null) {
      testTreeItem = new TestTreeItem(suiteTreeItem.getTreeItem(), trm);
      registerTreeItem(testId, testTreeItem);
    }

    String classId = testId + "." + trm.getClassName();
    ITreeItem classTreeItem = m_treeItemMap.get(classId);
    if (classTreeItem == null) {
      classTreeItem = new ClassTreeItem(testTreeItem.getTreeItem(), trm);
      registerTreeItem(classId, classTreeItem);
    }

    return classTreeItem;
  }

  @Override
  public void aboutToStart() {
    m_tree.removeAll();
    m_treeItemMap = new Hashtable<String, ITreeItem>();
    m_failureTraceComponent.clear();
  }

  @Override
  public void activate() {
    testSelected();
  }

  @Override
  public void setFocus() {
    m_tree.setFocus();
  }

  /**
   * Expand all the nodes in the tree.
   */
  private class ExpandAllAction extends Action {
    public ExpandAllAction() {
      setText(ResourceUtil.getString("ExpandAllAction.text")); //$NON-NLS-1$
      setToolTipText(ResourceUtil.getString("ExpandAllAction.tooltip")); //$NON-NLS-1$
    }

    @Override
    public void run() {
      expandAll();
    }
  }

  protected void expandAll() {
    TreeItem[] treeItems = m_tree.getItems();
    m_tree.setRedraw(false);
    for(int i = 0; i < treeItems.length; i++) {
      expandAll(treeItems[i]);
    }
    m_tree.setRedraw(true);
  }

  private void expandAll(TreeItem item) {
    item.setExpanded(true);

    TreeItem[] items = item.getItems();
    for(int i = 0; i < items.length; i++) {
      expandAll(items[i]);
    }
  }

  private RunInfo getSelectedRunInfo() {
    TreeItem[] treeItems = m_tree.getSelection();
    if (treeItems.length == 0) {
      return null;
    } else {
      ITreeItem iti = BaseTreeItem.getTreeItem(treeItems[0]);
      return iti.getRunInfo();
    }
  }

  private void testSelected() {
    postSyncRunnable(new Runnable() {
    public void run() {
      m_failureTraceComponent.showFailure(getSelectedRunInfo());
    }
  });
  }

  private void postSyncRunnable(Runnable r) {
    m_tree.getDisplay().syncExec(r);
  }

  private void registerTreeItem(String id, ITreeItem treeItem) {
    m_treeItemMap.put(id, treeItem);
  }

  /**
   * Called after an item has been updated, meant to be overridden by subclasses
   */
  protected void onPostUpdate(TreeItem ti, int state) {
  }

  private void postExpandAll() {
    Runnable expandRunnable = new Runnable() {
      public void run() {
        m_tree.selectAll();
        expandAll();
      };
    };

    m_tree.getDisplay().syncExec(expandRunnable);
  }
}
