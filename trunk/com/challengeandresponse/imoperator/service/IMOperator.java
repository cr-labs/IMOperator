package com.challengeandresponse.imoperator.service;

import java.util.Vector;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;

import com.challengeandresponse.appstack.AppStackException;
import com.challengeandresponse.eventlogger.EventLoggerException;
import com.challengeandresponse.eventlogger.TextfileEventLogger;
import com.challengeandresponse.imoperator.comm.*;
import com.challengeandresponse.imoperator.m2mobjects.MMObject;
import com.challengeandresponse.imoperator.universaliq.UniversalIQProvider;

public class IMOperator {

	private TextfileEventLogger el;
	private SimpleXMPPConnection xmppc;
	
	private boolean isRunning;
	
	
	
	/// local constants to move into a config file
	private static final String	LOG_PATH = "/tmp/imoperator.log";
	private static final String	XMPP_SERVER = "localhost";
	private static final String	OPERATOR_USERNAME = "operator@localhost";
	private static final String	OPERATOR_PASSWORD = "3x55y";
	private static final String	OPERATOR_RESOURCE = "operator";
	private static final String	ADMIN_USERNAME = "admin@localhost";
	private static final int 		LOOP_CYCLE_TIME = 10000;
	
	
	private static final Vector <String>	OPERATORS;
	static {
		OPERATORS = new Vector <String> ();
		OPERATORS.add(ADMIN_USERNAME);
	}
	

	public static final String	PRODUCT_SHORT = "IMOperator";
	public static final String	PRODUCT_LONG = "Challenge/Response IMOperator";
	public static final String	VERSION_SHORT = "0.14 alpha";
	public static final String	VERSION_LONG = PRODUCT_LONG + " " + VERSION_SHORT;
	public static final String	COPYRIGHT = "Copyright (c) 2007 Challenge/Response LLC, Cambridge, MA";

	
	
	private boolean startup() {
		try {
			el = new TextfileEventLogger(LOG_PATH,true);
			el.addEvent("IMOperator starting");

			el.addEvent("Opening connection to server "+XMPP_SERVER);

			// the new way 2008-04-14
			XMPPConfig config = new XMPPConfig(XMPP_SERVER, SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, OPERATOR_RESOURCE, null, OPERATOR_USERNAME, OPERATOR_PASSWORD);
			SimpleXMPPConnection xmppc = new SimpleXMPPConnection(config,true);
//			xmppc = new SimpleXMPPConnection(
//					XMPP_SERVER, SimpleXMPPConnection.XMPP_CLIENT_DEFAULT_PORT, null, true);

			el.addEvent("Logging in with username "+OPERATOR_USERNAME);
//			xmppc.secureConnect(OPERATOR_USERNAME,OPERATOR_PASSWORD,OPERATOR_RESOURCE);
			xmppc.secureConnect();

			el.addEvent("Configuring packet listeners and filters");
			// interactive CHAT processors
			ProcessorChatAdmin mpAdmin = new ProcessorChatAdmin(this, xmppc, el, OPERATORS);
			xmppc.addPacketListenerAndFilter(mpAdmin, mpAdmin);

			ProcessorChat mpChat = new ProcessorChat(this, xmppc, el, OPERATORS);
			mpChat.setDebugLevel(2);
			xmppc.addPacketListenerAndFilter(mpChat,mpChat);

			// IQ (M2M) processors
			ProcessorM2M mpm2m = new ProcessorM2M(this,el,xmppc);
			mpm2m.setDebugLevel(2);
			xmppc.addPacketListenerAndFilter(mpm2m,mpm2m);
			
			// IQ providers for XML-to-Object reconstitution of all supported objects.
			// One universal provider can handle them all
			ProviderManager pm = ProviderManager.getInstance();
			UniversalIQProvider uiqp = new UniversalIQProvider();
			pm.addIQProvider("query", MMObject.class.getName(), uiqp);

			el.addEvent("Logged in. Sending presence: available.");
			xmppc.sendPresence(Presence.Type.available,Presence.Mode.available);
		}
		catch (AppStackException ase) {
			el.addEvent("Exception configuring appstack: "+ase.getMessage());
			return false;
		}
		catch (SimpleXMPPException xmppe) {
			el.addEvent("Exception connecting and logging in: "+xmppe.getMessage());
			return false;
		}
		catch (EventLoggerException ele) {
			System.out.println("Error starting event logger");
		}
		
		return true;
	}
	
	
	
	
	public void shutdown() {
		this.isRunning = false;
	}
	

	
	
	public static void main(String[] args) {
		System.out.println("IMOperator starting");
		IMOperator imo = new IMOperator();
		
		if (! imo.startup()) {
			return;
		}
		
		imo.isRunning = true;
		while (imo.isRunning) {
			sleep(LOOP_CYCLE_TIME);
		}
		
		// sleep before terminating, to be sure all shutdown-related messages are delivered (or at least attempted)
		imo.el.addEvent("Disconnecting from server");
		sleep(2000);
		
		imo.xmppc.disconnect();
		imo.el.addEvent("Terminating");
	}
	
	
	
	
	private static void sleep(int msec) {
		try {
			Thread.sleep(msec);
		}
		catch (InterruptedException ie) {
		}
	}
	
	
	
	
}
