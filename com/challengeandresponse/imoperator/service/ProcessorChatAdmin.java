package com.challengeandresponse.imoperator.service;

import java.util.Vector;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import com.challengeandresponse.appstack.*;
import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.imoperator.comm.SimpleXMPPConnection;
import com.challengeandresponse.imoperator.comm.SimpleXMPPException;
import com.challengeandresponse.imoperator.service.IMOperator;
import com.challengeandresponse.utils.ChatUtils;


/**
 * This message processor recognizes only direct commands ("exec:path/to/command/...") via chat
 * messages from service operator accounts only, and calls back to the commands via the appstack,
 * It is the comm and appstack servicer for the IMOperator bot itself and this has a reference
 * back to the IMOperator for calling, e.g., its shutdown() method.
 * 
 * @author jim
 * @deprecated Used BaseAgent instead and follow that model to IM-enable code
 *
 */
public class ProcessorChatAdmin 
extends AppStack
implements PacketFilter, PacketListener
{
	private IMOperator imo;
	private SimpleXMPPConnection xmppc;
	private Vector operators;
	private EventLoggerI el;
	
//	private String XHTML_EXTENSION_NS = "http://jabber.org/protocol/xhtml-im";

	
	/**
	 * The usernames of the operators who can issue shutdown and other commands
	 * @param imo the controlling IMOperator object
	 * @param xmppc a live XMPPCommunicator that this class can use for talking-back to clients
	 * @param el a TextFileEventLogger to write interesting events to
	 * @param operators the usernames (name@host) that are operators of this server
	 */
	
	public ProcessorChatAdmin(IMOperator imo, SimpleXMPPConnection xmppc, EventLoggerI el, Vector operators)
	throws AppStackException {
		this.imo = imo;
		this.xmppc = xmppc;
		this.el = el;
		this.operators = operators;
		el.addEvent("ProcessorAdminDirect initialized");

		el.addEvent("Building AppStack");
		try {
			addMethod("tail","tail");
			addMethod("shutdown","shutdown");
		}
		catch (AppStackException ase) {
			el.addEvent("Exception configuring appstack: "+ase.getMessage());
			throw new AppStackException(ase.getMessage());
		}
	}


	/// APPSTACK commands for the IMOperator
	
	// tail the log file, the path should indicate how many lines
	public String tail(AppStackPathI aspi) {
		int howmany = 1;
		String s  = aspi.popNext();
		if (s != null) {
			try {
			howmany = Integer.valueOf(s);
			}
			catch (Exception e) { }
		}
		return ChatUtils.objectToString(el.getLastN(howmany),"\n");
	}
	
	
	/** 
	 * shutdown is implemented in IMOperator, not here. We just call it if we were told when to call it ("now") is the only arg supported presently
	 */
	public String shutdown(AppStackPathI aspi) {
		String nextStr = aspi.popNext();
		if ("now".equals(nextStr)) {
			imo.shutdown();
			return "Shutting down";
		}
		else if ("?".equals(nextStr))
			return "now";
		else
			return null;
	}


	
	
	// FILTER - this one accepts packets from operators only
	public boolean accept(Packet packet) {
		// packet must be a message, and the message must be of type 'chat'
		// also we want to see the xhtml-im extension
		if (! (packet instanceof Message))
			return false;
		if ( ((Message) packet).getType() != Message.Type.chat)
			return false;
		// this processor only examines packets from system operators
		String from = packet.getFrom();
		String fromNoresource = from.toLowerCase().contains("/") ? from.substring(0,from.indexOf("/")) : from;
		return (operators.contains(fromNoresource));
	}



	// PROCESSOR
	public void processPacket(Packet packet) {
		String msg = ((Message) packet).getBody();
	
		if (msg.startsWith("exec:")) {
			try {
				xmppc.sendMessage(packet.getFrom(),"running: "+msg.substring(5));
				Object result = get(new AppStackDelimitedPath(msg.substring(5).trim()));
				xmppc.sendMessage(packet.getFrom(), ChatUtils.objectToString(result,"\n"));
			}
			catch (AppStackException ase) {
				xmppc.sendNoExceptionMessage(packet.getFrom(),"Problem: "+ase.getMessage());
				el.addEvent("ProcessorAdminDirect AppStackException from "+packet.getFrom()+" "+ase.getMessage());
			}
			catch (SimpleXMPPException sxe) {
				xmppc.sendNoExceptionMessage(packet.getFrom(),"Problem: "+sxe.getMessage());
				el.addEvent("ProcessorAdminDirect XMPPException from "+packet.getFrom()+" "+sxe.getMessage());
			}
		}
	}


}
