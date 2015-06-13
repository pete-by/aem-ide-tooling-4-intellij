package com.headwire.aem.tooling.intellij.explorer;

import com.headwire.aem.tooling.intellij.communication.MessageManager;
import com.headwire.aem.tooling.intellij.communication.ServerConnectionManager;
import com.headwire.aem.tooling.intellij.config.ServerConfigurationManager;
import com.intellij.execution.RunManagerAdapter;
import com.intellij.execution.RunManagerEx;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

/**
 * Created by schaefa on 6/12/15.
 */
public class ServerTreeManager
    implements ProjectComponent, Disposable
{

    private Tree tree;
    private ServerExplorerTreeBuilder myBuilder;
    private ServerConfigurationManager myConfig;
    private KeyMapListener myKeyMapListener;

    private final TreeExpander myTreeExpander = new TreeExpander() {
        public void expandAll() {
            myBuilder.expandAll();
        }

        public boolean canExpand() {
            final ServerConfigurationManager config = myConfig;
            return config != null && config.serverConfigurationSize() > 0;
        }

        public void collapseAll() {
            myBuilder.collapseAll();
        }

        public boolean canCollapse() {
            return canExpand();
        }
    };


    public ServerTreeManager(@NotNull Project project) {
        final MessageManager messageManager = ServiceManager.getService(project, MessageManager.class);
        final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
        tree = new Tree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new NodeRenderer());
        ServerTreeSelectionHandler selectionHandler = ServiceManager.getService(project, ServerTreeSelectionHandler.class);
        selectionHandler.init(tree);
        ServerConnectionManager serverConnectionManager = ServiceManager.getService(project, ServerConnectionManager.class);
        serverConnectionManager.init(selectionHandler);
        myConfig = ServiceManager.getService(project, ServerConfigurationManager.class);
//        serverConnectionManager = new ServerConnectionManager(project, selectionHandler);
        myBuilder = new ServerExplorerTreeBuilder(project, tree, model);
        TreeUtil.installActions(tree);
        new TreeSpeedSearch(tree);
        tree.addMouseListener(new PopupHandler() {
            public void invokePopup(final Component comp, final int x, final int y) {
                popupInvoked(comp, x, y);
            }
        });
//AS On the original SlingServerExplorer the runSelection() does nothing
//        new DoubleClickListener() {
//            @Override
//            protected boolean onDoubleClick(MouseEvent e) {
//                final int eventY = e.getY();
//                final int row = tree.getClosestRowForLocation(e.getX(), eventY);
//                if(row >= 0) {
//                    final Rectangle bounds = tree.getRowBounds(row);
//                    if(bounds != null && eventY > bounds.getY() && eventY < bounds.getY() + bounds.getHeight()) {
//                        runSelection(DataManager.getInstance().getDataContext(tree));
//                        return true;
//                    }
//                }
//                return false;
//            }
//        }.installOn(tree);
//
//        tree.registerKeyboardAction(new AbstractAction() {
//            public void actionPerformed(ActionEvent e) {
//                runSelection(DataManager.getInstance().getDataContext(tree));
//            }
//        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);
        tree.setLineStyleAngled();

//AS This needs to be done on the Tool Window
//        setToolbar(createToolbarPanel());
//        setContent(ScrollPaneFactory.createScrollPane(tree));
        ToolTipManager.sharedInstance().registerComponent(tree);

        tree.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent containerEvent) {
                messageManager.sendDebugNotification("Container Event: " + containerEvent);
            }

            @Override
            public void componentRemoved(ContainerEvent containerEvent) {
                messageManager.sendDebugNotification("Container Event: " + containerEvent);
            }
        });
        DomManager.getDomManager(project).addDomEventListener(new DomEventListener() {
            public void eventOccured(DomEvent event) {
                myBuilder.queueUpdate();
            }
        }, this);
        RunManagerEx myRunManager = RunManagerEx.getInstanceEx(project);
        myRunManager.addRunManagerListener(
            new RunManagerAdapter() {
                public void beforeRunTasksChanged() {
                    myBuilder.queueUpdate();
                }
            }
        );
        myKeyMapListener = new KeyMapListener();
    }

    public Tree getTree() {
        return tree;
    }

    private void popupInvoked(final Component comp, final int x, final int y) {
        Object userObject = null;
        final TreePath path = tree.getSelectionPath();
        if(path != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if(node != null) {
                userObject = node.getUserObject();
            }
        }
        ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup group = new DefaultActionGroup();
//        final DefaultActionGroup group = new DefaultActionGroup();
        if(
            userObject instanceof SlingServerNodeDescriptor ||
                userObject instanceof SlingServerModuleNodeDescriptor
            ) {
            group.add(actionManager.getAction("AEM.Connection.Popup"));
//            group.add(new RemoveAction());
//            group.add(new EditAction());
//            group.add(new CheckAction());
//            group.add(new DebugAction());
//            group.add(new StopAction());
//            group.add(new DeployAction());
//            group.add(new ForceDeployAction());
//            group.add(new BuildConfigureAction());
        } else {
            group.add(actionManager.getAction("AEM.Root.Popup"));
//            group.add(new AddAction());
        }
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.ANT_EXPLORER_POPUP, group);
        popupMenu.getComponent().show(comp, x, y);
    }

    @Override
    public void dispose() {
        final ServerExplorerTreeBuilder builder = myBuilder;
        if(builder != null) {
            Disposer.dispose(builder);
            myBuilder = null;
        }

        final Tree aTree = tree;
        if(aTree != null) {
            ToolTipManager.sharedInstance().unregisterComponent(aTree);
            for(KeyStroke keyStroke : aTree.getRegisteredKeyStrokes()) {
                aTree.unregisterKeyboardAction(keyStroke);
            }
            tree = null;
        }

        final KeyMapListener listener = myKeyMapListener;
        if(listener != null) {
            myKeyMapListener = null;
            listener.stopListen();
        }
    }

    @Override
    public void projectOpened() {

    }

    @Override
    public void projectClosed() {

    }

    @Override
    public void initComponent() {

    }

    @Override
    public void disposeComponent() {

    }

    @NotNull
    @Override
    public String getComponentName() {
        return "Sling Server Tree Manager";
    }

    private class KeyMapListener implements KeymapManagerListener, Keymap.Listener {
        private Keymap myCurrentKeyMap = null;

        public KeyMapListener() {
            final KeymapManagerEx keyMapManager = KeymapManagerEx.getInstanceEx();
            final Keymap activeKeymap = keyMapManager.getActiveKeymap();
            listenTo(activeKeymap);
            keyMapManager.addKeymapManagerListener(this);
        }

        public void activeKeymapChanged(Keymap keyMap) {
            listenTo(keyMap);
            updateTree();
        }

        private void listenTo(Keymap keyMap) {
            if(myCurrentKeyMap != null) {
                myCurrentKeyMap.removeShortcutChangeListener(this);
            }
            myCurrentKeyMap = keyMap;
            if(myCurrentKeyMap != null) {
                myCurrentKeyMap.addShortcutChangeListener(this);
            }
        }

        private void updateTree() {
            myBuilder.updateFromRoot();
        }

        public void onShortcutChanged(String actionId) {
            updateTree();
        }

        public void stopListen() {
            listenTo(null);
            KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(this);
        }
    }
}