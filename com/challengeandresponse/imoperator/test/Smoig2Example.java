package com.challengeandresponse.imoperator.test;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;

import com.challengeandresponse.appstack.AppStackException;
import com.challengeandresponse.eventlogger.*;
import com.challengeandresponse.imoperator.agent.BaseAgent;
import com.challengeandresponse.imoperator.comm.SimpleXMPPConnection;
import com.challengeandresponse.imoperator.comm.SimpleXMPPException;
import com.challengeandresponse.imoperator.m2mobjects.MMObject;
import com.challengeandresponse.utils.ChatUtils;

/**
 * A class just for testing - this is a bot that can send messages to the other bots.
 * This is a BaseAgent-based version of the simpler SmoigExample. 
 * username: smoig, password: smoig
 * 
 * @author jim
 */
public class Smoig2Example extends BaseAgent {

	private static final String	USERNAME = "smoig";
	private static final String	PASSWORD = "smoig";
	private static final String	RESOURCE = "smoig";
	private static final String	XMPP_SERVER = "localhost";

	public Smoig2Example(EventLoggerI el, String hostname, int portNum, String servicename, String username, String password, String resource) 
	throws AppStackException {
		super(el, hostname, portNum, servicename, username, password, resource);
	}
	
	private boolean oneshot = true;


	@Override
	public void init() {
		addPCRecognizedClass(MMObject.class);
	}


	@Override
	public void destroy(PacketCollector pc) {
		// just make one more pass over the PacketCollector at shutdown
		processIQ(pc);
	}

	
	@Override
	public void processIQ(PacketCollector pc) {
		// the test code
		if (oneshot) {
			System.out.println("Sending the whois thingy 1");
			MMObject mmObject = new MMObject(new Integer("12"));
			try {
				getXmppConnection().sendIQ("example@localhost/example",mmObject,true);
			}
			catch (SimpleXMPPException sxe) {
				System.out.println("Exception sending: "+sxe.getMessage());
			}
			oneshot = false;
		}
		
		
		Packet p = pc.pollResult();
		while (p != null) {
			IQ iq = (IQ) p;
			System.out.println("processIQ processing packet from: "+iq.getFrom());
			if (iq instanceof MMObject) {
				System.out.println("MMObject: "+ChatUtils.objectToString( ((MMObject) iq).getObject(),"\n") );
			}
			p = pc.pollResult();
		}
	}



	/**
	 * For testing
	 * @param args
	 */
	public static void main(String[] args) {
		try { 
			EventLoggerI el = new StdoutEventLogger();
			Smoig2Example sm = new Smoig2Example(el,XMPP_SERVER,SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, null,USERNAME,PASSWORD,RESOURCE);			

			ThreadGroup tg = new ThreadGroup("test");
			Thread t = new Thread(tg,sm);
			t.start();
		} 
		catch (AppStackException ase) {
			System.out.println("AppStackException: "+ase.getMessage());
		}
	}


	
}
