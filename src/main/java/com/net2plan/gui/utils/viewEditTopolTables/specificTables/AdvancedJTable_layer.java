/*******************************************************************************
 * Copyright (c) 2015 Pablo Pavon Mariño.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Contributors:
 * Pablo Pavon Mariño - initial API and implementation
 ******************************************************************************/


package com.net2plan.gui.utils.viewEditTopolTables.specificTables;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.table.TableModel;

import com.google.common.collect.Sets;
import com.net2plan.gui.utils.ClassAwareTableModel;
import com.net2plan.gui.utils.CurrentAndPlannedStateTableSorter;
import com.net2plan.gui.utils.IVisualizationControllerCallback;
import com.net2plan.gui.utils.topologyPane.TopologyPanel;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.internal.Constants.NetworkElementType;
import com.net2plan.internal.ErrorHandling;
import com.net2plan.utils.Constants.RoutingType;
import com.net2plan.utils.StringUtils;

/**
 */
@SuppressWarnings("unchecked")
public class AdvancedJTable_layer extends AdvancedJTableNetworkElement {
    public static final String netPlanViewTabName = "Layers";
    public static final String[] netPlanViewTableHeader = StringUtils.arrayOf("Unique identifier", "Index", "Name", "Routing type", "Number of links",
            "Number of demands", "Number of multicast demands", "Number of routes", "Number of forwarding rules", "Number of backup routes",
            "Number of multicast trees", "Description", "Link capacity units name", "Demand traffic units name", "Attributes");
    public static final String[] netPlanViewTableTips = StringUtils.arrayOf("Unique identifier (never repeated in the same netPlan object, never changes, long)", "Index (consecutive integer starting in zero)", "Name", "Routing type", "Number of links", "Number of demands", "Number of multicast demands", "Number of routes", "Number of forwarding rules", "Number of routes that are designated as backup of other route", "Number of multicast trees", "Description", "Link capacity units name", "Demand traffic units name", "Attributes");
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_INDEX = 1;
    public static final int COLUMN_NAME = 2;
    public static final int COLUMN_ROUTINGTYPE = 3;
    public static final int COLUMN_NUMLINKS = 4;
    public static final int COLUMN_NUMDEMANDS = 5;
    public static final int COLUMN_NUMMULTICASTDEMANDS = 6;
    public static final int COLUMN_NUMROUTES = 7;
    public static final int COLUMN_NUMFORWARDINRULES = 8;
    public static final int COLUMN_NUMSEGMENTS = 9;
    public static final int COLUMN_NUMTREES = 10;
    public static final int COLUMN_DESCRIPTION = 11;
    public static final int COLUMN_LINKCAPUNITS = 12;
    public static final int COLUMN_DEMANDTRAFUNITS = 13;
    public static final int COLUMN_ATTRIBUTES = 14;


    public AdvancedJTable_layer(final IVisualizationControllerCallback networkViewer) {
        super(createTableModel(networkViewer), networkViewer, NetworkElementType.LAYER, false);
        setDefaultCellRenderers(networkViewer);
        setSpecificCellRenderers();
        this.getTableHeader().setReorderingAllowed(false);

        if (networkViewer.inOnlineSimulationMode()) setRowSorter(new CurrentAndPlannedStateTableSorter(getModel()));
        else setAutoCreateRowSorter(true);
    }


    public List<Object[]> getAllData(NetPlan currentState, NetPlan initialState, ArrayList<String> attributesColumns) {
        NetworkLayer layer = currentState.getNetworkLayerDefault();
        List<Object[]> allLayerData = new LinkedList<Object[]>();
        for (NetworkLayer auxLayer : currentState.getNetworkLayers()) {
            RoutingType routingType_thisLayer = currentState.getRoutingType(auxLayer);
            Object[] layerData = new Object[netPlanViewTableHeader.length];
            layerData[COLUMN_ID] = auxLayer.getId();
            layerData[COLUMN_INDEX] = auxLayer.getIndex();
            layerData[2] = auxLayer.getName();
            layerData[3] = currentState.getRoutingType(auxLayer);
            layerData[4] = currentState.getNumberOfLinks(auxLayer);
            layerData[5] = currentState.getNumberOfDemands(auxLayer);
            layerData[6] = currentState.getNumberOfMulticastDemands(auxLayer);
            layerData[7] = routingType_thisLayer == RoutingType.SOURCE_ROUTING ? currentState.getNumberOfRoutes(auxLayer) : 0;
            layerData[8] = routingType_thisLayer == RoutingType.HOP_BY_HOP_ROUTING ? currentState.getNumberOfForwardingRules(auxLayer) : 0;
            layerData[9] = routingType_thisLayer == RoutingType.SOURCE_ROUTING ? currentState.getRoutesAreBackup(auxLayer).size() : 0;
            layerData[10] = currentState.getNumberOfMulticastTrees(auxLayer);
            layerData[11] = auxLayer.getDescription();
            layerData[12] = currentState.getLinkCapacityUnitsName(auxLayer);
            layerData[13] = currentState.getDemandTrafficUnitsName(auxLayer);
            layerData[14] = StringUtils.mapToString(auxLayer.getAttributes());
            allLayerData.add(layerData);

            if (initialState != null && initialState.getNetworkLayerFromId(auxLayer.getId()) != null) {
                auxLayer = initialState.getNetworkLayerFromId(auxLayer.getId());
                RoutingType routingType_thisLayer_initialNetPlan = initialState.getRoutingType(auxLayer);
                Object[] layerData_initialNetPlan = new Object[netPlanViewTableHeader.length];
                layerData_initialNetPlan[0] = null;
                layerData_initialNetPlan[1] = null;
                layerData_initialNetPlan[2] = auxLayer.getName();
                layerData_initialNetPlan[3] = initialState.getRoutingType(auxLayer);
                layerData_initialNetPlan[4] = initialState.getNumberOfLinks(auxLayer);
                layerData_initialNetPlan[5] = initialState.getNumberOfDemands(auxLayer);
                layerData_initialNetPlan[6] = initialState.getNumberOfMulticastDemands(auxLayer);
                layerData_initialNetPlan[7] = routingType_thisLayer_initialNetPlan == RoutingType.SOURCE_ROUTING ? initialState.getNumberOfRoutes(auxLayer) : 0;
                layerData_initialNetPlan[8] = routingType_thisLayer_initialNetPlan == RoutingType.HOP_BY_HOP_ROUTING ? initialState.getNumberOfForwardingRules(auxLayer) : 0;
                layerData_initialNetPlan[9] = routingType_thisLayer_initialNetPlan == RoutingType.SOURCE_ROUTING ? initialState.getRoutesAreBackup(auxLayer).size() : 0;
                layerData_initialNetPlan[10] = initialState.getNumberOfMulticastTrees(auxLayer);
                layerData_initialNetPlan[11] = auxLayer.getDescription();
                layerData_initialNetPlan[12] = initialState.getLinkCapacityUnitsName(auxLayer);
                layerData_initialNetPlan[13] = initialState.getDemandTrafficUnitsName(auxLayer);
                layerData_initialNetPlan[14] = StringUtils.mapToString(auxLayer.getAttributes());
                allLayerData.add(layerData_initialNetPlan);
            }
        }
        return allLayerData;
    }

    public String getTabName() {
        return netPlanViewTabName;
    }

    public String[] getTableHeaders() {
        return netPlanViewTableHeader;
    }

    public String[] getCurrentTableHeaders(){
        ArrayList<String> attColumnsHeaders = getAttributesColumnsHeaders();
        String[] headers = new String[netPlanViewTableHeader.length + attColumnsHeaders.size()];
        for(int i = 0; i < headers.length ;i++)
        {
            if(i<netPlanViewTableHeader.length)
            {
                headers[i] = netPlanViewTableHeader[i];
            }
            else{
                headers[i] = "Att: "+attColumnsHeaders.get(i - netPlanViewTableHeader.length);
            }
        }


        return headers;
    }
    public String[] getTableTips() {
        return netPlanViewTableTips;
    }

    public boolean hasElements(NetPlan np) {
        return true;
    }

    @Override
    public int getAttributesColumnIndex()
    {
        return COLUMN_ATTRIBUTES;
    }

    public int[] getColumnsOfSpecialComparatorForSorting() {
        return new int[]{};
    }

    private static TableModel createTableModel(final IVisualizationControllerCallback networkViewer) {
        TableModel layerTableModel = new ClassAwareTableModel(new Object[1][netPlanViewTableHeader.length], netPlanViewTableHeader) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex >= netPlanViewTableHeader.length;
            }

            @Override
            public void setValueAt(Object newValue, int row, int column) {
                Object oldValue = getValueAt(row, column);
                /* If value doesn't change, exit from function */
                if (newValue != null && newValue.equals(oldValue)) return;
                /* Set new value */
                super.setValueAt(newValue, row, column);
            }
        };

        return layerTableModel;
    }

    private void setDefaultCellRenderers(final IVisualizationControllerCallback networkViewer) {
    }

    private void setSpecificCellRenderers() {
    }

    public void setColumnRowSorting(boolean allowShowInitialNetPlan) {
    }

    public int getNumFixedLeftColumnsInDecoration() {
        return 2;
    }

    @Override
    public ArrayList<String> getAttributesColumnsHeaders()
    {
        return new ArrayList<String>();
    }


    @Override
    public void doPopup(MouseEvent e, int row, final Object itemId) {
        JPopupMenu popup = new JPopupMenu();

        if (callback.getVisualizationState().isNetPlanEditable()) {
            popup.add(getAddOption());
            for (JComponent item : getExtraAddOptions())
                popup.add(item);
        }

        if (!isTableEmpty()) {
            if (callback.getVisualizationState().isNetPlanEditable()) {
                if (row != -1) {
                    if (popup.getSubElements().length > 0) popup.addSeparator();

                    if (networkElementType == NetworkElementType.LAYER && callback.getDesign().getNumberOfLayers() == 1) {

                    } else {
                        JMenuItem removeItem = new JMenuItem("Remove " + networkElementType);

                        removeItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                NetPlan netPlan = callback.getDesign();

                                try {
                                    netPlan.removeNetworkLayer(netPlan.getNetworkLayerFromId((long) itemId));
                                    callback.updateVisualizationAfterChanges(Sets.newHashSet(NetworkElementType.LAYER));
                                } catch (Throwable ex) {
                                    ErrorHandling.addErrorOrException(ex, getClass());
                                    ErrorHandling.showErrorDialog("Unable to remove " + networkElementType);
                                }
                            }
                        });

                        popup.add(removeItem);
                    }

                    addPopupMenuAttributeOptions(e, row, itemId, popup);
                }
                List<JComponent> extraOptions = getExtraOptions(row, itemId);
                if (!extraOptions.isEmpty()) {
                    if (popup.getSubElements().length > 0) popup.addSeparator();
                    for (JComponent item : extraOptions) popup.add(item);
                }
            }

            List<JComponent> forcedOptions = getForcedOptions();
            if (!forcedOptions.isEmpty()) {
                if (popup.getSubElements().length > 0) popup.addSeparator();
                for (JComponent item : forcedOptions) popup.add(item);
            }
        }

        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    @Override
    public void showInCanvas(MouseEvent e, Object itemId) {
        return;
    }

    private boolean isTableEmpty() {
        return false;
    }

    private JMenuItem getAddOption() {
        JMenuItem addItem = new JMenuItem("Add " + networkElementType);

        addItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                NetPlan netPlan = callback.getDesign();

                try {
                    netPlan.addLayer("Layer " + netPlan.getNumberOfLayers(), null, null, null, null);
                    callback.updateVisualizationAfterChanges(Sets.newHashSet(NetworkElementType.LAYER));
                } catch (Throwable ex) {
                    ErrorHandling.showErrorDialog(ex.getMessage(), "Unable to add " + networkElementType);
                }
            }
        });

        return addItem;
    }

    private List<JComponent> getExtraAddOptions() {
        return new LinkedList<JComponent>();
    }

    private List<JComponent> getExtraOptions(final int row, final Object itemId) {
        return new LinkedList<JComponent>();
    }

    private List<JComponent> getForcedOptions() {
        return new LinkedList<JComponent>();
    }
}

