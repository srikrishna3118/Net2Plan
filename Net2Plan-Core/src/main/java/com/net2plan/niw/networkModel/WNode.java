/*******************************************************************************
 * This program and the accompanying materials are made available under the terms of the MIT License available at
 * https://opensource.org/licenses/MIT
 *******************************************************************************/

package com.net2plan.niw.networkModel;

import java.awt.geom.Point2D;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Resource;
import com.net2plan.niw.networkModel.WNetConstants.WTYPE;

/**
 * This class represents a node in the network, capable of initiating or ending IP and WDM links, as well as lightpaths
 * and service chains
 */
/**
 * @author Pablo
 *
 */
public class WNode extends WAbstractNetworkElement
{
	public enum OPTICALSWITCHTYPE
	{
		ROADM (
				e->new TreeSet<> (Arrays.asList(e)) , // add
				e->new TreeSet<> (), // drop
				(e1,e2)->new TreeSet<> (Arrays.asList(e2)), // express
				e->new TreeSet<> () // unavoidable: propagates whatever you do
				) 
		, FILTERLESS_DROPANDWASTENOTDIRECTIONLESS (
				e->new TreeSet<> (Arrays.asList(e)), // add
				e->e.getB().getOutgoingFibers().stream().filter(ee->e.isBidirectional()? !ee.equals(e.getBidirectionalPair())    : true).collect(Collectors.toCollection(TreeSet::new)), // drop
				(e1,e2)->e1.getB().getOutgoingFibers().stream().filter(ee->e1.isBidirectional()? !ee.equals(e1.getBidirectionalPair())    : true).collect(Collectors.toCollection(TreeSet::new)), // express
				e->e.getB().getOutgoingFibers().stream().filter(ee->e.isBidirectional()? !ee.equals(e.getBidirectionalPair())    : true).collect(Collectors.toCollection(TreeSet::new)) // unavoidable: propagates whatever you do
				);

		private final Function<WFiber , SortedSet<WFiber>> outFibersIfAddToOutputFiber;
		private final Function<WFiber , SortedSet<WFiber>> outFibersIfDropFromInputFiber;
		private final BiFunction<WFiber , WFiber , SortedSet<WFiber>> outFibersIfExpressFromInputToOutputFiber;
		private final Function<WFiber , SortedSet<WFiber>> outFibersUnavoidablePropagationFromInputFiber;
		
		private OPTICALSWITCHTYPE(Function<WFiber, SortedSet<WFiber>> outFibersIfAddToOutputFiber, Function<WFiber, SortedSet<WFiber>> outFibersIfDropFromInputFiber, BiFunction<WFiber, WFiber, SortedSet<WFiber>> outFibersIfExpressFromInputToOutputFiber,
				Function<WFiber, SortedSet<WFiber>> outFibersUnavoidablePropagationFromInputFiber)
		{
			this.outFibersIfAddToOutputFiber = outFibersIfAddToOutputFiber;
			this.outFibersIfDropFromInputFiber = outFibersIfDropFromInputFiber;
			this.outFibersIfExpressFromInputToOutputFiber = outFibersIfExpressFromInputToOutputFiber;
			this.outFibersUnavoidablePropagationFromInputFiber = outFibersUnavoidablePropagationFromInputFiber;
		}
		public static OPTICALSWITCHTYPE getDefault () { return ROADM; }
		public boolean isRoadm () { return this == ROADM; }
		public String getShortName () { return isRoadm()? "ROADM" : "Filterless"; }
		public boolean isDropAndWaste () { return this == OPTICALSWITCHTYPE.FILTERLESS_DROPANDWASTENOTDIRECTIONLESS; }
		public Function<WFiber, SortedSet<WFiber>> getOutFibersIfAddToOutputFiber()
		{
			return this.outFibersIfAddToOutputFiber;
		}
		public Function<WFiber, SortedSet<WFiber>> getOutFibersIfDropFromInputFiber()
		{
			return this.outFibersIfDropFromInputFiber;
		}
		public BiFunction<WFiber, WFiber, SortedSet<WFiber>> getOutFibersIfExpressFromInputToOutputFiber()
		{
			return this.outFibersIfExpressFromInputToOutputFiber;
		}
		public Function<WFiber, SortedSet<WFiber>> getOutFibersUnavoidablePropagationFromInputFiber()
		{
			return this.outFibersUnavoidablePropagationFromInputFiber;
		}
	}

	
	private static final String ATTNAMECOMMONPREFIX = "Node_";
	private static final String ATTNAMESUFFIX_TYPE = "type";
	private static final String ATTNAMESUFFIX_ISCONNECTEDTOCORE = "isConnectedToNetworkCore";
	private static final String RESOURCETYPE_CPU = WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER + "CPU";
	private static final String RESOURCETYPE_RAM = WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER + "RAM";
	private static final String RESOURCETYPE_HD = WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER + "HD";
	private static final String ATTNAMESUFFIX_ARBITRARYPARAMSTRING = "ArbitraryString";
	private static final String ATTNAME_OPTICALSWITCHTYPE = "ATTNAME_OPTICALSWITCHTYPE";
	private static final String ATTNAMESUFFIX_ADD_NOISEFIGUREDB = "addNoiseFigure_db";
	private static final String ATTNAMESUFFIX_ADD_GAINDB = "addGain_db";
	private static final String ATTNAMESUFFIX_ADD_PMD_PS = "addPmd_ps";
	private static final String ATTNAMESUFFIX_DROP_NOISEFIGUREDB = "dropNoiseFigure_db";
	private static final String ATTNAMESUFFIX_DROP_GAINDB = "dropGain_db";
	private static final String ATTNAMESUFFIX_DROP_PMD_PS = "dropPmd_ps";
	private static final String ATTNAMESUFFIX_EXPRESS_NOISEFIGUREDB = "expressNoiseFigure_db";
	private static final String ATTNAMESUFFIX_EXPRESS_GAINDB = "expressGain_db";
	private static final String ATTNAMESUFFIX_EXPRESS_PMD_PS = "expressPmd_ps";
	private static final String ATTNAMESUFFIX_ISOUTPUTSPECTRUMEQUALIZED = "isOutputSpectrumEqualized";
	private static final String ATTNAMESUFFIX_EQUALIZATIONTARGET_OUTPUTDSP_MWPERGHZ = "equalizationTarget_mwPerGhz";

	
	/** Returns the spectral density for the output optical channels of this OADM, to be enforced, if the node is configered to equalize the output power
	 * @return see above
	 */
	public Optional<Double> getOutputSpectrumEqualizationTarget_mwPerGhz ()
	{
		if (getNe().getAttributeAsDouble (ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ISOUTPUTSPECTRUMEQUALIZED, 0.0).equals (0.0)) return Optional.empty();
		final Double val = getNe().getAttributeAsDouble (ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EQUALIZATIONTARGET_OUTPUTDSP_MWPERGHZ, null);
		return Optional.ofNullable(val);
	}

	/** Indicates if the node is configured to equalize the output power of the output (express and add) optical channels
	 * @return see above
	 */
	public boolean isConfiguredToEqualizeOutput ()
	{
		return getOutputSpectrumEqualizationTarget_mwPerGhz().isPresent();
	}

	/** Sets the spectral density for the output optical channels of this OADM, to be enforced. If an optional empty is passed, the node is set to NOT equalize the output power
	 * @return see above
	 */
	public void setOutputSpectrumEqualizationTarget_mwPerGhz (Optional<Double> valInMwPerGhz)
	{
		if (valInMwPerGhz.isPresent())
		{
			getNe().setAttribute (ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ISOUTPUTSPECTRUMEQUALIZED, 1);
			getNe().setAttribute (ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EQUALIZATIONTARGET_OUTPUTDSP_MWPERGHZ, valInMwPerGhz.get());
		} else
		{
			getNe().setAttribute (ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ISOUTPUTSPECTRUMEQUALIZED, 0);
		}
	}
	
	public void setArbitraryParamString(String s)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ARBITRARYPARAMSTRING, s);
	}

	public String getArbitraryParamString()
	{
		return getNe().getAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ARBITRARYPARAMSTRING, "");
	}

	private final Node n;

	public WNode(Node n)
	{
		super(n, Optional.empty());
		this.n = n;
	}

	Resource getCpuBaseResource()
	{
		final Set<Resource> cpuResources = n.getResources(RESOURCETYPE_CPU);
		assert cpuResources.size() < 2;
		if (cpuResources.isEmpty()) setTotalNumCpus(0);
		assert n.getResources(RESOURCETYPE_CPU).size() == 1;
		return n.getResources(RESOURCETYPE_CPU).iterator().next();
	}

	Resource getRamBaseResource()
	{
		final Set<Resource> ramResources = n.getResources(RESOURCETYPE_RAM);
		assert ramResources.size() < 2;
		if (ramResources.isEmpty()) setTotalRamGB(0);
		assert n.getResources(RESOURCETYPE_RAM).size() == 1;
		return n.getResources(RESOURCETYPE_RAM).iterator().next();
	}

	Resource getHdBaseResource()
	{
		final Set<Resource> hdResources = n.getResources(RESOURCETYPE_HD);
		assert hdResources.size() < 2;
		if (hdResources.isEmpty()) setTotalHdGB(0);
		assert n.getResources(RESOURCETYPE_HD).size() == 1;
		return n.getResources(RESOURCETYPE_HD).iterator().next();
	}

	public boolean isVirtualNode()
	{
		return n.hasTag(WNetConstants.TAGNODE_INDICATIONVIRTUALORIGINNODE) || n.hasTag(WNetConstants.TAGNODE_INDICATIONVIRTUALDESTINATIONNODE);
	}

	public boolean isRegularNode()
	{
		return !isVirtualNode();
	}

	@Override
	public Node getNe()
	{
		return (Node) associatedNpElement;
	}

	/**
	 * Returns the node name, which must be unique among all the nodes
	 * @return see above
	 */
	public String getName()
	{
		return n.getName();
	}

	/**
	 * Sets the node name, which must be unique among all the nodes
	 * @param name see above
	 */
	public void setName(String name)
	{
		if (name == null) WNet.ex("Names cannot be null");
		if (name.contains(WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER)) throw new Net2PlanException("Names cannot contain the character: " + WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER);
		if (getNet().getNodes().stream().anyMatch(n -> n.getName().equals(name))) WNet.ex("Names cannot be repeated");
		if (name.contains(" ")) throw new Net2PlanException("Names cannot contain spaces");
		n.setName(name);
	}

	
	
	/** Returns the noise factor observed by the added channels, in dB. Defaults to 5.0 dB
	 * @return see above
	 */
	public double getAddNoiseFactor_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_NOISEFIGUREDB, 5.0);
	}
	/** Returns the noise factor observed by the dropped channels, in dB. Defaults to 5.0 dB
	 * @return see above
	 */
	public double getDropNoiseFactor_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_NOISEFIGUREDB, 5.0);
	}
	/** Returns the noise factor observed by the express channels, in dB. Defaults to 5.0 dB
	 * @return see above
	 */
	public double getExpressNoiseFactor_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_NOISEFIGUREDB, 5.0);
	}
	/** Returns the gain observed by the added channels, in dB. Defaults to20.0 dB
	 * @return see above
	 */
	public double getAddGain_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_GAINDB, 20.0);
	}
	/** Returns the gain observed by the dropped channels, in dB. Defaults to20.0 dB
	 * @return see above
	 */
	public double getDropGain_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_GAINDB, 20.0);
	}
	/** Returns the gain observed by the express channels, in dB. Defaults to20.0 dB
	 * @return see above
	 */
	public double getExpressGain_dB ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_GAINDB, 20.0);
	}
	/** Returns the PMD added to the added channels, in ps. Defaults to 0 ps
	 * @return see above
	 */
	public double getAddPmd_ps ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_PMD_PS, 0.0);
	}
	/** Returns the PMD added to the drop channels, in ps. Defaults to 0 ps
	 * @return see above
	 */
	public double getDropPmd_ps ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_PMD_PS, 0.0);
	}
	/** Returns the PMD added to the express channels, in ps. Defaults to 0 ps
	 * @return see above
	 */
	public double getExpressPmd_ps ()
	{
		return getNe().getAttributeAsDouble(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_PMD_PS, 0.0);
	}

	/** Sets the noise factor observed by the added channels, in dB. 
	 * @return see above
	 */
	public void setAddNoiseFactor_dB (double noiseFactor_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_NOISEFIGUREDB, noiseFactor_dB);
	}
	/** Sets the noise factor observed by the dropped channels, in dB. 
	 * @return see above
	 */
	public void setDropNoiseFactor_dB (double noiseFactor_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_NOISEFIGUREDB, noiseFactor_dB);
	}
	/** Sets the noise factor observed by the express channels, in dB. 
	 * @return see above
	 */
	public void setExpressNoiseFactor_dB (double noiseFactor_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_NOISEFIGUREDB, noiseFactor_dB);
	}
	/** Sets the gain observed by the added channels, in dB. 
	 * @return see above
	 */
	public void setAddGain_dB (double gain_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_GAINDB, gain_dB);
	}
	/** Sets the gain observed by the dropped channels, in dB. 
	 * @return see above
	 */
	public void setDropGain_dB (double gain_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_GAINDB, gain_dB);
	}
	/** Sets the gain observed by the express channels, in dB. 
	 * @return see above
	 */
	public void setExpressGain_dB (double gain_dB)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_GAINDB, gain_dB);
	}
	/** Sets the PMD added observed by the added channels, in ps. 
	 * @return see above
	 */
	public void setAddPmd_ps (double pmd_ps)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ADD_PMD_PS, pmd_ps);
	}
	/** Sets the PMD added observed by the dropped channels, in ps. 
	 * @return see above
	 */
	public void setDropPmd_ps (double pmd_ps)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_DROP_PMD_PS, pmd_ps);
	}
	/** Sets the PMD added observed by the express channels, in ps. 
	 * @return see above
	 */
	public void setExpressPmd_ps (double pmd_ps)
	{
		getNe().setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_EXPRESS_PMD_PS, pmd_ps);
	}
	
	
	
	
	/**
	 * Sets the icon to show in Net2Plan GUI for node at the WDM layer, and the relative size respect to other nodes.
	 * @param urlIcon see above
	 * @param relativeSize see above
	 */
	public void setWdmIcon(URL urlIcon, double relativeSize)
	{
		getNe().setUrlNodeIcon(getWdmNpLayer(), urlIcon, relativeSize);
	}

	/**
	 * Sets the icon to show in Net2Plan GUI for node at the IP layer, and the relative size respect to other nodes.
	 * @param urlIcon see above
	 * @param relativeSize see above
	 */
	public void setIpIcon(URL urlIcon, double relativeSize)
	{
		getNe().setUrlNodeIcon(getIpNpLayer(), urlIcon, relativeSize);
	}

	/**
	 * Returns the url of the icon specified by the user for the WDM layer, or null if none
	 * @return the url
	 */
	public URL getUrlNodeIconWdm()
	{
		return getNe().getUrlNodeIcon(getWdmNpLayer());
	}

	/**
	 * Returns the url of the icon specified by the user for the WDM layer, or null if none
	 * @return the url
	 */
	public URL getUrlNodeIconIp()
	{
		return getNe().getUrlNodeIcon(getIpNpLayer());
	}

	/**
	 * Returns the relative size in GUI Net2Plan visualization for the icon in the WDM layer fos this node
	 * @return see above
	 */
	public double getIconRelativeSizeInWdm()
	{
		return getNe().getNodeIconRelativeSize(getWdmNpLayer());
	}

	/**
	 * Returns the relative size in GUI Net2Plan visualization for the icon in the WDM layer fos this node
	 * @return see above
	 */
	public double getIconRelativeSizeInIp()
	{
		return getNe().getNodeIconRelativeSize(getIpNpLayer());
	}

	/**
	 * Returns the (X,Y) node position
	 * @return see above
	 */
	public Point2D getNodePositionXY()
	{
		return n.getXYPositionMap();
	}

	/**
	 * Sets the (X,Y) node position
	 * @param position see above
	 */
	public void setNodePositionXY(Point2D position)
	{
		n.setXYPositionMap(position);
	}

	/**
	 * Returns the user-defined node type
	 * @return see above
	 */
	public String getType()
	{
		return getAttributeOrDefault(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_TYPE, "");
	}

	/**
	 * Sets the user-defined node type
	 * @param type see above
	 */
	public void setType(String type)
	{
		n.setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_TYPE, type);
	}

	/**
	 * Returns if this node is connected to a core node (core nodes are not in the design)
	 * @return see above
	 */
	public boolean isConnectedToNetworkCore()
	{
		return getAttributeAsBooleanOrDefault(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ISCONNECTEDTOCORE, WNetConstants.WNODE_DEFAULT_ISCONNECTEDTOCORE);
	}

	/**
	 * Sets if this node is assumed to be connected to a core node (core nodes are not in the design)
	 * @param isConnectedToCore see above
	 */
	public void setIsConnectedToNetworkCore(boolean isConnectedToCore)
	{
		n.setAttribute(ATTNAMECOMMONPREFIX + ATTNAMESUFFIX_ISCONNECTEDTOCORE, new Boolean(isConnectedToCore).toString());
	}

	/**
	 * Returns the user-defined node population
	 * @return see above
	 */
	public double getPopulation()
	{
		return n.getPopulation();
	}

	/**
	 * Sets the user-defined node population
	 * @param population see above
	 */
	public void setPoputlation(double population)
	{
		n.setPopulation(population);
	}

	/**
	 * Returns the number of CPUs available in the node for instantiation of VNFs
	 * @return see above
	 */
	public double getTotalNumCpus()
	{
		return n.getResources(RESOURCETYPE_CPU).stream().mapToDouble(r -> r.getCapacity()).sum();
	}

	/**
	 * Sets the number of CPUs available in the node for instantiation of VNFs
	 * @param totalNumCpus see above
	 */
	public void setTotalNumCpus(double totalNumCpus)
	{
		final Set<Resource> res = n.getResources(RESOURCETYPE_CPU);
		if (res.size() > 1) throw new Net2PlanException("Format error");
		if (res.isEmpty()) n.getNetPlan().addResource(RESOURCETYPE_CPU, RESOURCETYPE_CPU, Optional.of(n), totalNumCpus, RESOURCETYPE_CPU, new HashMap<>(), 0.0, null);
		else res.iterator().next().setCapacity(totalNumCpus, new HashMap<>());
	}

	/**
	 * Returns the total RAM (in GBytes) available in the node for instantiation of VNFs
	 * @return see above
	 */
	public double getTotalRamGB()
	{
		return n.getResources(RESOURCETYPE_RAM).stream().mapToDouble(r -> r.getCapacity()).sum();
	}

	/**
	 * Sets the total RAM (in GBytes) available in the node for instantiation of VNFs
	 * @param totalRamGB see above
	 */
	public void setTotalRamGB(double totalRamGB)
	{
		final Set<Resource> res = n.getResources(RESOURCETYPE_RAM);
		if (res.size() > 1) throw new Net2PlanException("Format error");
		if (res.isEmpty()) res.add(n.getNetPlan().addResource(RESOURCETYPE_RAM, RESOURCETYPE_RAM, Optional.of(n), totalRamGB, "GB", new HashMap<>(), 0.0, null));
		else res.iterator().next().setCapacity(totalRamGB, new HashMap<>());
	}

	/**
	 * Returns the total hard disk size (in GBytes) available in the node for instantiation of VNFs
	 * @return see above
	 */
	public double getTotalHdGB()
	{
		return n.getResources(RESOURCETYPE_HD).stream().mapToDouble(r -> r.getCapacity()).sum();
	}

	/**
	 * Sets the total hard disk size (in GBytes) available in the node for instantiation of VNFs
	 * @param totalHdGB see above
	 */
	public void setTotalHdGB(double totalHdGB)
	{
		final Set<Resource> res = n.getResources(RESOURCETYPE_HD);
		if (res.size() > 1) throw new Net2PlanException("Format error");
		if (res.isEmpty()) res.add(n.getNetPlan().addResource(RESOURCETYPE_HD, RESOURCETYPE_HD, Optional.of(n), totalHdGB, "GB", new HashMap<>(), 0.0, null));
		else res.iterator().next().setCapacity(totalHdGB, new HashMap<>());
	}

	/**
	 * Returns the current number of occupied CPUs by the instantiated VNFs
	 * @return see above
	 */
	public double getOccupiedCpus()
	{
		return getCpuBaseResource().getOccupiedCapacity();
	}

	/**
	 * Returns the current amount of occupied hard-disk (in giga bytes) by the instantiated VNFs
	 * @return see above
	 */
	public double getOccupiedHdGB()
	{
		return getHdBaseResource().getOccupiedCapacity();
	}

	/**
	 * Returns the current amount of occupied RAM (in giga bytes) by the instantiated VNFs
	 * @return see above
	 */
	public double getOccupiedRamGB()
	{
		return getRamBaseResource().getOccupiedCapacity();
	}

	/**
	 * Indicates if the node is up or down (failed)
	 * @return see above
	 */
	public boolean isUp()
	{
		return n.isUp();
	}

	/**
	 * Returns the set of nodes, for which there is a fiber form this node to them
	 * @return see above
	 */
	public SortedSet<WNode> getNeighborNodesViaOutgoingFibers()
	{
		return getOutgoingFibers().stream().map(e -> e.getB()).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of nodes, for which there is a fiber from them to this node
	 * @return see above
	 */
	public SortedSet<WNode> getNeighborNodesViaIncomingFibers()
	{
		return getIncomingFibers().stream().map(e -> e.getA()).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing fibers of the node
	 * @return see above
	 */
	public SortedSet<WFiber> getOutgoingFibers()
	{
		return n.getOutgoingLinks(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isWFiber(); }).
				map(ee -> new WFiber(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming fibers to the node
	 * @return see above
	 */
	public SortedSet<WFiber> getIncomingFibers()
	{
		return n.getIncomingLinks(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isWFiber(); }).
				map(ee -> new WFiber(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing IP links of the node
	 * @return see above
	 */
	public SortedSet<WIpLink> getOutgoingIpLinks()
	{
		return n.getOutgoingLinks(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isIpLink(); }).
				map(ee -> new WIpLink(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/** Returns the IP connections, realizing IP unicast demands, that are initiated in this node 
	 * @return see above
	 */
	public SortedSet<WMplsTeTunnel> getOutgoingIpConnections ()
	{
		return n.getOutgoingRoutes(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isMplsTeTunnel(); }).
				map(ee -> new WMplsTeTunnel(ee)).collect(Collectors.toCollection(TreeSet::new));
	}
	
	/** Returns the IP connections, realizing IP unicast demands, that are ended in this node 
	 * @return see above
	 */
	public SortedSet<WMplsTeTunnel> getIncomingIpConnections ()
	{
		return n.getIncomingRoutes(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isMplsTeTunnel(); }).
				map(ee -> new WMplsTeTunnel(ee)).collect(Collectors.toCollection(TreeSet::new));
	}
	
	
	/**
	 * Returns the set of incoming IP links to the node
	 * @return see above
	 */
	public SortedSet<WIpLink> getIncomingIpLinks()
	{
		return n.getIncomingLinks(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isIpLink(); }).
				map(ee -> new WIpLink(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing lightpath requests of the node
	 * @return see above
	 */
	public SortedSet<WLightpathRequest> getOutgoingLigtpathRequests()
	{
		return n.getOutgoingDemands(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpathRequest(); }).
				map(ee -> new WLightpathRequest(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming lightpath requests to the node
	 * @return see above
	 */
	public SortedSet<WLightpathRequest> getIncomingLigtpathRequests()
	{
		return n.getIncomingDemands(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpathRequest(); }).
				map(ee -> new WLightpathRequest(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing lightpaths of the node
	 * @return see above
	 */
	public SortedSet<WLightpath> getOutgoingLigtpaths()
	{
		return n.getOutgoingRoutes(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpath(); }).
				map(ee -> new WLightpath(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming, outgoing and traversing lightpaths to the node
	 * @return see above
	 */
	public SortedSet<WLightpath> getInOutOrTraversingLigtpaths()
	{
		return n.getAssociatedRoutes(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpath(); }).
				map(ee -> new WLightpath(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming, outgoing and traversing lightpaths to the node
	 * @return see above
	 */
	public SortedSet<WLightpath> getExpressSwitchedLightpaths()
	{
		return n.getAssociatedRoutes(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpath(); }).
				map(ee -> new WLightpath(ee)).filter(lp->lp.getNodesWhereThisLightpathIsExpressOpticallySwitched().contains(this)).collect(Collectors.toCollection(TreeSet::new));
	}

	
	/**
	 * Returns the set of incoming lightpaths to the node
	 * @return see above
	 */
	public SortedSet<WLightpath> getIncomingLigtpaths()
	{
		return n.getIncomingRoutes(getNet().getWdmLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isLightpath(); }).
				map(ee -> new WLightpath(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing service chain requests of the node: those which have the node as a potential injection
	 * node
	 * @return see above
	 */
	public SortedSet<WServiceChainRequest> getOutgoingServiceChainRequests()
	{
		return getNet().getServiceChainRequests().stream().filter(sc -> sc.getPotentiallyValidOrigins().contains(this)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing unicast IP demands of the node: those which have the node as origin
	 * @return see above
	 */
	public SortedSet<WIpUnicastDemand> getOutgoingIpUnicastDemands ()
	{
		return getNe().getOutgoingDemands(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isIpUnicastDemand(); }).
				map(d->new WIpUnicastDemand(d)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming unicast IP demands of the node: those which have the node as destination
	 * @return see above
	 */
	public SortedSet<WIpUnicastDemand> getIncomingIpUnicastDemands ()
	{
		return getNe().getIncomingDemands(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isIpUnicastDemand(); }).
				map(d->new WIpUnicastDemand(d)).collect(Collectors.toCollection(TreeSet::new));
	}


	
	/**
	 * Returns the set of incoming service chain requests of the node: those which have the node as a potential end node
	 * @return see above
	 */
	public SortedSet<WServiceChainRequest> getIncomingServiceChainRequests()
	{
		return getNet().getServiceChainRequests().stream().
				filter(sc -> sc.getPotentiallyValidDestinations().contains(this)).
				collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of outgoing service chains of the node, including those starting in a VNF in the node
	 * @return see above
	 */
	public SortedSet<WServiceChain> getOutgoingServiceChains()
	{
		return n.getAssociatedRoutes(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isServiceChain(); }).
				map(ee -> new WServiceChain(ee)).
				filter(sc -> sc.getA().equals(this)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming service chains to the node, including those ended in a VNF in the node
	 * @return see above
	 */
	public SortedSet<WServiceChain> getIncomingServiceChains()
	{
		return n.getAssociatedRoutes(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isServiceChain(); }).
				map(ee -> new WServiceChain(ee)).filter(sc -> sc.getB().equals(this)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns the set of incoming, outgoing and traversing service chains to the node.
	 * @return see above
	 */
	public SortedSet<WServiceChain> getInOutOrTraversingServiceChains()
	{
		return n.getAssociatedRoutes(getNet().getIpLayer().getNe()).stream().
				filter(d->{ WTYPE t = getNet().getWType(d).orElse(null); return t == null? false : t.isServiceChain(); }).
				map(ee -> new WServiceChain(ee)).collect(Collectors.toCollection(TreeSet::new));
	}

	Link getIncomingLinkFromAnycastOrigin()
	{
		return n.getNetPlan().getNodePairLinks(getNet().getAnycastOriginNode().getNe(), n, false, getIpNpLayer()).stream().findFirst().orElseThrow(() -> new RuntimeException());
	}

	Link getOutgoingLinkToAnycastDestination()
	{
		return n.getNetPlan().getNodePairLinks(n, getNet().getAnycastDestinationNode().getNe(), false, getIpNpLayer()).stream().findFirst().orElseThrow(() -> new RuntimeException());
	}

	/**
	 * Sets the node as up (working, non-failed)
	 */
	public void setAsUp()
	{
		n.setFailureState(true);
		final SortedSet<WLightpathRequest> affectedDemands = new TreeSet<>();
		getOutgoingFibers().forEach(f -> affectedDemands.addAll(f.getTraversingLpRequestsInAtLeastOneLp()));
		getIncomingFibers().forEach(f -> affectedDemands.addAll(f.getTraversingLpRequestsInAtLeastOneLp()));
		for (WLightpathRequest lpReq : affectedDemands)
			lpReq.updateNetPlanObjectAndPropagateUpwards();
	}

	/**
	 * Sets the node as down (failed), so traversing IP links or lightpaths become down
	 */
	public void setAsDown()
	{
		n.setFailureState(false);
		final SortedSet<WLightpathRequest> affectedDemands = new TreeSet<>();
		getOutgoingFibers().forEach(f -> affectedDemands.addAll(f.getTraversingLpRequestsInAtLeastOneLp()));
		getIncomingFibers().forEach(f -> affectedDemands.addAll(f.getTraversingLpRequestsInAtLeastOneLp()));
		for (WLightpathRequest lpReq : affectedDemands)
			lpReq.updateNetPlanObjectAndPropagateUpwards();
	}

	/**
	 * Removes this node, and all the ending and initiated links, or traversing lightpaths or service chains
	 * 
	 */
	public void remove()
	{
		this.setAsDown();
		n.remove();
	}

	@Override
	public String toString()
	{
		return "Node " + getName();
	}

	/**
	 * Returns all the VNF instances in this node, of any type
	 * @return see above
	 */
	public SortedSet<WVnfInstance> getAllVnfInstances()
	{
		return n.getResources().stream().filter(r -> !r.getType().contains(WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER)).map(r -> new WVnfInstance(r)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns all the VNF instances in this node, of the given type
	 * @param type see above
	 * @return see above
	 */
	public SortedSet<WVnfInstance> getVnfInstances(String type)
	{
		if (type.contains(WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER)) throw new Net2PlanException("Names cannot contain the character: " + WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER);
		return n.getResources().stream().filter(r -> !r.getType().contains(WNetConstants.LISTSEPARATORANDINVALIDNAMECHARACTER)).map(r -> new WVnfInstance(r)).filter(v -> v.getType().equals(type)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Returns all the VNF instances in this node, of the given type
	 * @param type see above
	 * @return see above
	 */
	public SortedSet<WVnfInstance> getVnfInstances()
	{
		return n.getResources().stream().map(r -> new WVnfInstance(r)).collect(Collectors.toCollection(TreeSet::new));
	}

	/** Indicates if this node has a filterless brodcast operation with the add, dropped and express lightpaths
	 * @return
	 */
	public OPTICALSWITCHTYPE getOpticalSwitchType ()
	{
		try
		{
			return OPTICALSWITCHTYPE.valueOf(this.getNe().getAttribute(ATTNAME_OPTICALSWITCHTYPE, null));
		} catch (Exception exc) 
		{
			return OPTICALSWITCHTYPE.getDefault();
		}
	}
	
	public void setOpticalSwitchType (OPTICALSWITCHTYPE type)
	{
		getNe().setAttribute(ATTNAME_OPTICALSWITCHTYPE, type.name());
	}
	
	@Override
	void checkConsistency()
	{
		if (this.wasRemoved()) return;
		assert getIncomingFibers().stream().allMatch(e->e.getB().equals(this));
		assert getOutgoingFibers().stream().allMatch(e->e.getA().equals(this));
		assert getIncomingIpConnections().stream().allMatch(e->e.getB().equals(this));
		assert getOutgoingIpConnections().stream().allMatch(e->e.getA().equals(this));
		assert getIncomingLigtpathRequests().stream().allMatch(e->e.getB().equals(this));
		assert getOutgoingLigtpathRequests().stream().allMatch(e->e.getA().equals(this));
		assert getIncomingLigtpaths().stream().allMatch(e->e.getB().equals(this));
		assert getOutgoingLigtpaths().stream().allMatch(e->e.getA().equals(this));
		assert getIncomingServiceChainRequests().stream().allMatch(e->e.getPotentiallyValidDestinations().contains(this));
		assert getOutgoingServiceChainRequests().stream().allMatch(e->e.getPotentiallyValidOrigins().contains(this));
		assert getIncomingServiceChains().stream().allMatch(e->e.getB().equals(this));
		assert getOutgoingServiceChains().stream().allMatch(e->e.getA().equals(this));
		assert getInOutOrTraversingLigtpaths().stream().allMatch(e->e.getSeqNodes().contains(this));
		assert getInOutOrTraversingServiceChains().stream().allMatch(e->e.getSequenceOfTraversedIpNodesWithoutConsecutiveRepetitions().contains(this));
		assert getNeighborNodesViaIncomingFibers().stream().allMatch(n->n.getNeighborNodesViaOutgoingFibers().contains(this));
		assert getNeighborNodesViaOutgoingFibers().stream().allMatch(n->n.getNeighborNodesViaIncomingFibers().contains(this));
		assert getVnfInstances().stream().allMatch(v->v.getHostingNode().equals(this));
	}


	/** Returns the SRGs that this node belongs to, i.e. the ones that make this node fail
	 * @return see above
	 */
	public SortedSet<WSharedRiskGroup> getSrgsThisElementIsAssociatedTo ()
	{
		return getNe().getSRGs().stream().map(s->new WSharedRiskGroup(s)).collect(Collectors.toCollection(TreeSet::new));
	}

	@Override
	public WTYPE getWType() { return WTYPE.WNode; }

}
