package org.deltaproject.channelagent.fuzz;

import org.deltaproject.channelagent.pkthandle.PktListener;
import org.projectfloodlight.openflow.exceptions.OFParseError;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMessageReader;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.U16;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public class SeedPackets {
	private ArrayList<SeedPacket> seedList;

	private OFFactory factory;
	private OFMessageReader<OFMessage> reader;
	private byte ofversion;

	private boolean sendHello;

	public SeedPackets(OFFactory f) {
		factory = f;
		reader = factory.getReader();

		sendHello = false;

		seedList = new ArrayList<SeedPacket>();
		setSeedList();
	}

	public void setSeedList() {
		seedList.add(new SeedPacket(OFType.PACKET_IN));
		seedList.add(new SeedPacket(OFType.FEATURES_REPLY));
		seedList.add(new SeedPacket(OFType.GET_CONFIG_REPLY));
		seedList.add(new SeedPacket(OFType.BARRIER_REPLY));
		seedList.add(new SeedPacket(OFType.STATS_REPLY));
		seedList.add(new SeedPacket(OFType.ECHO_REPLY));
		seedList.add(new SeedPacket(OFType.EXPERIMENTER));
		seedList.add(new SeedPacket(OFType.PORT_STATUS));
		seedList.add(new SeedPacket(OFType.HELLO));
	}

	public SeedPacket getSeedList(OFType t) {
		for (SeedPacket l : seedList) {
			if (l.getOFType() == t)
				return l;
		}
		return null;
	}

	// public void recordNormalPacket(byte[] payload) {
	// byte type = payload[1];
	// if (type < typeLength) {
	// seedList.get(type).add(payload);
	// }
	// }

	public boolean getSeedPkts(byte[] recv, int len) throws OFParseError {
		// for OpenFlow Message
		byte[] rawMsg = new byte[len];
		System.arraycopy(recv, 0, rawMsg, 0, len);
		ByteBuf bb = Unpooled.copiedBuffer(rawMsg);

		int totalLen = bb.readableBytes();
		int offset = bb.readerIndex();

		while (offset < totalLen) {
			bb.readerIndex(offset);

			byte version = bb.readByte();
			bb.readByte();
			int length = U16.f(bb.readShort());
			bb.readerIndex(offset);

			if (length < PktListener.MINIMUM_LENGTH)
				throw new OFParseError(
						"Wrong length: Expected to be >= " + PktListener.MINIMUM_LENGTH + ", was: " + length);

			try {
				OFMessage message = reader.readFrom(bb);
				long xid = message.getXid();

				getSeedList(message.getType()).putData(message, length);
				
				if (message.getType() == OFType.HELLO) {
					if (sendHello)
						clearSeedPkts();
					else
						sendHello = true;
					
					getSeedList(message.getType()).putData(message, length);
				}
			} catch (OFParseError e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			offset += length;
		}

		bb.clear();
		return true;
	}

	public void clearSeedPkts() {
		seedList.clear();
		setSeedList();
	}
}