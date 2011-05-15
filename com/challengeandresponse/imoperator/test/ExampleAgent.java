package com.challengeandresponse.imoperator.test;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;

import com.challengeandresponse.appstack.AppStackException;
import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.eventlogger.StdoutEventLogger;
import com.challengeandresponse.imoperator.agent.BaseAgent;
import com.challengeandresponse.imoperator.comm.SimpleXMPPConnection;
import com.challengeandresponse.imoperator.m2mobjects.MMObject;
import com.challengeandresponse.utils.ChatUtils;

public class ExampleAgent 
extends BaseAgent {
	
	public ExampleAgent(EventLoggerI el, String hostname, int portNum, String servicename, String username, String password, String resource) 
	throws AppStackException {
		super(el,hostname,portNum,servicename, username,password,resource);
	}

	public ExampleAgent(EventLoggerI el, String servicename, String username, String password, String resource) 
	throws AppStackException {
		super(el,null,-1,servicename,username,password,resource);
	}
	
	@Override
	public void init() {
		addPCRecognizedClass(MMObject.class);
		System.out.println("ExampleAgent's init running");
	}

	@Override
	public void destroy(PacketCollector pc) {
		processIQ(pc);
	}

	@Override
	public void processIQ(PacketCollector pc) {
		System.out.println("processIQ() is running");

		Packet p = pc.pollResult();
		while (p != null) {
			IQ iq = (IQ) p;
			System.out.println("processIQ processing packet from: "+iq.getFrom());
			if (iq instanceof MMObject) {
				System.out.println("MMObject: "+ChatUtils.objectToString( ((MMObject) iq).getObject(),"\n") );
			}
			
			// get the next one, if there is a next one
			p = pc.pollResult();
		}
		System.out.println("processIQ is exiting");
	}
	
	

	
	/**
	 * FOR TESTING
	 */
	public static void main(String[] args) {
		try {
			EventLoggerI el = new StdoutEventLogger();
			el.addEvent("Opening connection to server localhost");

			ExampleAgent ea = new ExampleAgent(
					el, 
					"localhost", 
					SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, 
					null, 
					"example", 
					"example", 
					"example");
			
			ThreadGroup tg = new ThreadGroup("test");
		    Thread t = new Thread(tg,ea);
		    t.start();
		}
		catch (Exception e) {
			System.out.println("Exception: "+e.getMessage());
			e.printStackTrace();
		}
	}
	

	
}
