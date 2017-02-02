// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.plugins.mapillary.MapillaryAbstractImage;
import org.openstreetmap.josm.plugins.mapillary.MapillaryLayer;
import org.openstreetmap.josm.plugins.mapillary.MapillaryLocationChangeset;
import org.openstreetmap.josm.plugins.mapillary.actions.MapillarySubmitCurrentChangesetAction;
import org.openstreetmap.josm.plugins.mapillary.history.MapillaryRecord;
import org.openstreetmap.josm.plugins.mapillary.history.commands.MapillaryCommand;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryChangesetListener;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Toggle dialog that shows you the latest {@link MapillaryCommand} done and
 * allows the user to revert them.
 *
 * @see MapillaryRecord
 * @see MapillaryCommand
 */
public final class MapillaryChangesetDialog extends ToggleDialog implements MapillaryChangesetListener {
  private static final long serialVersionUID = -3019715241209349372L;
  private static MapillaryChangesetDialog instance;

  private final DefaultTreeModel changesetTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
  private final JTree changesetTree = new JTree(this.changesetTreeModel);

  private final JSeparator separator = new JSeparator();
  private final Component spacer = Box.createRigidArea(new Dimension(0, 3));

  private final SideButton submitButton = new SideButton(new MapillarySubmitCurrentChangesetAction(this));
  private final JPanel uploadPendingPanel = new JPanel();
  private final JProgressBar uploadPendingProgress = new JProgressBar();

  private final ConcurrentHashMap<Object, MapillaryAbstractImage> map;

  private final Container rootComponent = new JPanel(new BorderLayout());

  /**
   * Destroys the unique instance of the class.
   */
  public static void destroyInstance() {
    MapillaryChangesetDialog.instance = null;
  }

  private MapillaryChangesetDialog() {
    super(
      tr("Current Mapillary changeset"),
      "mapillary-upload",
      tr("Open Mapillary changeset dialog"),
      Shortcut.registerShortcut(
        tr("Mapillary changeset"), tr("Open Mapillary changeset dialog"), KeyEvent.VK_9, Shortcut.NONE
      ),
      200
    );
    createLayout(rootComponent, false, Collections.singletonList(submitButton));

    this.map = new ConcurrentHashMap<>();

    this.changesetTree.expandRow(0);
    this.changesetTree.setShowsRootHandles(true);
    this.changesetTree.setRootVisible(false);
    this.changesetTree.setCellRenderer(new MapillaryImageTreeCellRenderer());
    this.changesetTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    JPanel treesPanel = new JPanel(new GridBagLayout());
    treesPanel.add(this.spacer, GBC.eol());
    treesPanel.add(this.changesetTree, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
    treesPanel.add(this.separator, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
    treesPanel.add(Box.createRigidArea(new Dimension(0, 0)), GBC.std().weight(0, 1));
    rootComponent.add(new JScrollPane(treesPanel), BorderLayout.CENTER);

    uploadPendingProgress.setIndeterminate(true);
    uploadPendingProgress.setString(tr("Submitting changeset to server…"));
    uploadPendingProgress.setStringPainted(true);

    setUploadPending(false);
  }

  /**
   * Returns the unique instance of the class.
   *
   * @return The unique instance of the class.
   */
  public static synchronized MapillaryChangesetDialog getInstance() {
    if (instance == null) {
      instance = new MapillaryChangesetDialog();
    }
    return instance;
  }

  private void buildTree() {
    MapillaryLocationChangeset changeset = MapillaryLayer.getInstance().getLocationChangeset();
    submitButton.setEnabled(!changeset.isEmpty());
    DefaultMutableTreeNode changesetRoot = new DefaultMutableTreeNode();

    this.map.clear();
    changeset.parallelStream().filter(command -> command != null).forEach(command -> {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(command.toString());
      this.map.put(node, command);
      changesetRoot.add(node);
    });

    this.spacer.setVisible(changeset.isEmpty());

    this.changesetTreeModel.setRoot(changesetRoot);
  }

  public void setUploadPending(final boolean isUploadPending) {
    if (isUploadPending) {
      rootComponent.add(uploadPendingProgress, BorderLayout.SOUTH);
    } else {
      rootComponent.remove(uploadPendingProgress);
    }
    submitButton.setEnabled(!isUploadPending && !MapillaryLayer.getInstance().getLocationChangeset().isEmpty());
    rootComponent.revalidate();
    rootComponent.repaint();
  }

  @Override
  public void changesetChanged() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::buildTree);
    } else {
      buildTree();
    }
  }
}