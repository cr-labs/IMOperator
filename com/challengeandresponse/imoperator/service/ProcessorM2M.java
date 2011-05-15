package com.challengeandresponse.imoperator.service;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.*;

import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.imoperator.comm.SimpleXMPPConnection;
import com.challengeandresponse.imoperator.universaliq.UniversalIQRPC;


/**
 * The message processor for IMOperator IQ messages sent between services, for example
 * messages to the WX service to bring weather info back to a chat user
 * Implements:
 * <li>the PacketFilter and PacketListener interfaces, as required by the SimpleXMPPConnection class.
 * 
 * @author jim
 * @deprecated Used BaseAgent instead and follow that model to IM-enable code
 *
 */
public class ProcessorM2M
implements PacketFilter, PacketListener
{

	private IMOperator imo;
	private EventLoggerI el;
	private SimpleXMPPConnection xmppc;

	private int debugLevel = 0; // no debug



	/**
	 * @param imo the controlling IMOperator object
	 * @param el an EventLogger to write interesting events to. May be null, in which case interesting events/exceptions are not logged
	 * @param xmppc a live XMPPCommunicator that this class can use for talking-back to clients
	 */
	public ProcessorM2M(IMOperator imo, EventLoggerI el, SimpleXMPPConnection xmppc) {
		this.imo = imo;
		this.el = el;
		this.xmppc = xmppc;
	}


	/**
	 * Set the debugging level for diagnostic output to the log
	 * @param level the debug level. 0 = no debug. 1..infinity = debug with output levels set in the code below
	 */
	public void setDebugLevel(int level) {
		this.debugLevel = level;
	}


	// FILTER 
	public boolean accept(Packet packet) {
		if (debugLevel > 2)
			el.addEvent("ProcessorM2M evaluating packet from "+packet.getFrom());

		if (packet instanceof IQ) {
			if (debugLevel > 1)
				el.addEvent("ProcessorM2M accepted the packet");
			return true;
		}
		if (debugLevel > 1)
			el.addEvent("IQ packet is not of type GET/RESULT/ERROR. Not accepted.");
		return false;
	}




	/**
	 * PROCESSOR --- prep and process a JABBER packet. This method is called
	 * for every Jabber packet. It standardizes the message, then passes it
	 * to the handlers for actual processing.
	 * 
	 * @param packet the message packet
	 */
	public void processPacket(Packet packet) {
		if (debugLevel > 1)
			el.addEvent("in processPacket of ProcessorM2M, message from "+packet.getFrom());
		CommContext cc= new CommContext("com.challengeandresponse.imoperator.service.ProcessorM2M");
		cc.setProperty(CommContext.PROP_SERVICE,CommContext.VALUE_SERVICE_XMPP);
		cc.setProperty(CommContext.PROP_SERVICE_USERNAME,packet.getFrom());
		cc.setProperty(CommContext.PROP_MESSAGE_TO,packet.getTo());
		cc.setProperty(CommContext.PROP_SERVICE_USERNAME_NO_RESOURCE,
				packet.getFrom().contains("/") ? packet.getFrom().substring(0,packet.getFrom().indexOf("/")) : packet.getFrom());

		// unpack the object, then call the appropriate method on it
		// -- that's handleMessage() - how is that divided between processPacket and handleMessage?

		IQ iq = (IQ) packet;

		System.out.println("IQ type: "+iq.getType());
		System.out.println(iq);

		XMPPError xmppe = iq.getError();
		System.out.println("Error: "+xmppe);
		System.out.println("Error code: "+((xmppe != null) ? xmppe.getCode() : "n/a"));

		if (iq instanceof UniversalIQRPC) {
			System.out.println("it's an IQQuery object");
			System.out.println("methodCall is "+((UniversalIQRPC)iq).getMethodName());
		}
		//		sendMessage(cc,message.get(0));
	}



}
