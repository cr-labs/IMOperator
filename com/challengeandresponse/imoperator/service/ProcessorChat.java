package com.challengeandresponse.imoperator.service;

import java.util.Date;
import java.util.List;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import com.challengeandresponse.appstack.*;
import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.imoperator.comm.SimpleXMPPConnection;
import com.challengeandresponse.imoperator.comm.SimpleXMPPException;
import com.challengeandresponse.utils.ChatUtils;


/**
 * The main chat message processor for IMOperator communications with people via chat
 * Implements:
 * <li>the PacketFilter and PacketListener interfaces, as required by the SimpleXMPPConnection class.
 * 
 * @author jim
 * @deprecated Used BaseAgent instead and follow that model to IM-enable code
 *
 */
public class ProcessorChat
extends AppStack
implements PacketFilter, PacketListener
{
	private IMOperator imo;
	private EventLoggerI el;
	private SimpleXMPPConnection xmppc;
	private List operators;

	
	private int debugLevel = 0; // no debug
	
//	private String XHTML_EXTENSION_NS = "http://jabber.org/protocol/xhtml-im";

	/**
	 * @param imo the controlling IMOperator object
	 * @param xmppc a live XMPPCommunicator that this class can use for talking-back to clients
	 * @param el an EventLogger to write interesting events to
	 * @param operators the usernames (name@host) that are operators of this server and can access the admin service
	 */
	public ProcessorChat(IMOperator imo, SimpleXMPPConnection xmppc, EventLoggerI el, List operators)
	throws AppStackException {
		this.imo = imo;
		this.xmppc = xmppc;
		this.el = el;
		this.operators = operators;

		el.addEvent("Building AppStack");
		try {
			addMethod("date","date");
			addMethod("time","date");
			addMethod("version","version");
			addMethod("help","help");
		}
		catch (AppStackException ase) {
			el.addEvent("Exception configuring appstack: "+ase.getMessage());
			throw new AppStackException(ase.getMessage());
		}

	}

	
	// APPSTACK commands 
	public String date(AppStackPathI aspi) {
		return (new Date()).toString();
	}

	public String version(AppStackPathI aspi) {
		return IMOperator.VERSION_LONG;
	}

	public String help(AppStackPathI aspi) {
		return "date, version, help";
	}
	
	
	/**
	 * Set the debugging level for diagnostic output to the log
	 * @param level the debug level. 0 = no debug. 1..infinity = debug with output levels set in the code below
	 */
	public void setDebugLevel(int level) {
		this.debugLevel = level;
	}
	

	// FILTER  -- filter a JABBER message
	public boolean accept(Packet packet) {
		if (debugLevel >= 2) {
			el.addEvent("Examining packet from:"+packet.getFrom());
			el.addEvent("Packet as string: "+packet.toString());
		}
		// packet must be a message, and the message must be of type 'chat'
		if ( (packet instanceof Message) && (((Message) packet).getType() == Message.Type.chat) ) {
			if (debugLevel >= 2)
				el.addEvent("returning TRUE from ProcessorChat.accept");
			return true;
		}
		if (debugLevel >= 2)
			el.addEvent("returning FALSE from ProcessorChat.accept");
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
		if (debugLevel >= 2)
			el.addEvent("in JABBER processPacket, message from "+packet.getFrom());
		CommContext cc= new CommContext("com.challengeandresponse.imoperator.service.ProcessorChat");
		cc.setProperty(CommContext.PROP_SERVICE,CommContext.VALUE_SERVICE_XMPP);
		cc.setProperty(CommContext.PROP_SERVICE_USERNAME,packet.getFrom());
		cc.setProperty(CommContext.PROP_MESSAGE_TO,packet.getTo());
		cc.setProperty(CommContext.PROP_SERVICE_USERNAME_NO_RESOURCE,
				packet.getFrom().contains("/") ? packet.getFrom().substring(0,packet.getFrom().indexOf("/")) : packet.getFrom());
		String message = ((Message) packet).getBody();
		if (debugLevel >= 2)
			el.addEvent("calling handleMessage. First message record:"+message);
		handleMessage(cc,message);
	}

	
	
	
	/**
	 * Right now the handler just echoes the message back.
	 * @param cc A comm context, with info about the message
	 * @param message A vector of message lines found in an incoming message
	 */
	private void handleMessage(CommContext cc, String message) {
		String lcMessage = message.toLowerCase().trim();
		try {
			// interpret what was typed, including figuring out the currently attached service
			if (lcMessage.equals("date")) {
				execute(cc.getStringProperty(CommContext.PROP_SERVICE_USERNAME,""),"date");
			}
			else if (lcMessage.equals("time")) {
				execute(cc.getStringProperty(CommContext.PROP_SERVICE_USERNAME,""),"time");
			}
			else if (lcMessage.equals("version")) {
				execute(cc.getStringProperty(CommContext.PROP_SERVICE_USERNAME,""),"version");
			}
			else if (lcMessage.equals("help")) {
				execute(cc.getStringProperty(CommContext.PROP_SERVICE_USERNAME,""),"help");
			}
			else {
				xmppc.sendMessage(cc.getStringProperty(CommContext.PROP_SERVICE_USERNAME, ""),message);
			}
		}
		catch (SimpleXMPPException xmppe) {
			el.addEvent("Exception sending message: "+xmppe.getMessage());
		}
	}

	
	private void execute(String to, String slashPath) {
		try {
			Object result = get(new AppStackDelimitedPath(slashPath.trim()));
			xmppc.sendMessage(to, ChatUtils.objectToString(result,"\n"));
		}
		catch (AppStackException ase) {
			xmppc.sendNoExceptionMessage(to,"Problem: "+ase.getMessage());
			el.addEvent("ProcessorAdminDirect AppStackException from "+to+" "+ase.getMessage());
		}
		catch (SimpleXMPPException sxe) {
			xmppc.sendNoExceptionMessage(to,"Problem: "+sxe.getMessage());
			el.addEvent("ProcessorAdminDirect XMPPException from "+to+" "+sxe.getMessage());
		}
	}

	
	
	


	/*
	// this is the code to pull out the XHTML chunk if an XHTML message has arrived
	Iterator ii = packet.getExtensions().iterator();
	while (ii.hasNext()) {
		PacketExtension pe = (PacketExtension) ii.next();
		// a chat message
		if (pe instanceof XHTMLExtension) {
			Iterator i3 = ((XHTMLExtension) pe).getBodies();
			while (i3.hasNext()) {
				// strip tags
				String s = i3.next().toString().replaceAll("<br/>", "\n").trim();
				s = s.replaceAll("<.*?>", "").trim();
				message.add(s);
			}
		}
	}
*/


}
