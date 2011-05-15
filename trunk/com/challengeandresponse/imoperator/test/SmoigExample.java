package com.challengeandresponse.imoperator.test;

import org.jivesoftware.smack.packet.Presence;

import com.challengeandresponse.imoperator.comm.*;
import com.challengeandresponse.imoperator.m2mobjects.MMObject;


/**
 * A class just for testing - this is an example of the simplest possible bot
 * NOT created by extending BaseAgent, that can send a message to another bot.
 * This one sends an IQ packet to the ExampleAgent.
 * username: smoig, password: smoig
 * 
 * @author jim
 *
 */
public class SmoigExample {

	private static final String	USERNAME = "smoig";
	private static final String	PASSWORD = "smoig";
	private static final String	RESOURCE = "smoig";
	private static final String SERVICE = null;
	private static final String	XMPP_SERVER = "localhost";

	
	public static void main(String[] args) {
		// the new way 2008-04-14
		XMPPConfig config = new XMPPConfig(XMPP_SERVER, SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, RESOURCE, SERVICE, USERNAME, PASSWORD);
		SimpleXMPPConnection xmppc = new SimpleXMPPConnection(config,true);
		
//		SimpleXMPPConnection xmppc = new SimpleXMPPConnection(
//				XMPP_SERVER, SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, null, true);
		
		try {
			xmppc.setVerbose(true);
			xmppc.secureConnect();
//			xmppc.secureConnect(USERNAME, PASSWORD, RESOURCE);
			// making the agent visible is essential, else it cannot get presence for others
			xmppc.sendPresence(Presence.Type.available,Presence.Mode.available);

			System.out.println("Sending the mmObject thingy 1");
			MMObject mmObject = new MMObject(new Integer("12"));
			xmppc.sendIQ("example@localhost/example",mmObject,true);

//			System.out.println("Sending the error thingy");
//			System.out.println("Making it into an ERROR packet!!!!");
//			mmObject.setType(IQ.Type.ERROR);
//			XMPPError xmpe = new XMPPError(XMPPError.Condition.bad_request,"Are you crazy? This is an error test!");
//			mmobject.setError(xmpe);
//			xmppc.sendIQ("test@localhost/test",mmObject);

			System.out.println("Sleeping 2000msec");
			Thread.sleep(2000);
		} catch (SimpleXMPPException e) {
			System.out.println("SimpleXMPPException: "+e.getMessage());
		}
		catch(InterruptedException ie) {
			
		}

		System.out.println("Terminating");
	}
}
