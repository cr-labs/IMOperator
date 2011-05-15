package com.challengeandresponse.imoperator.agent;

import java.util.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;

import com.challengeandresponse.appstack.*;
import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.imoperator.comm.*;
import com.challengeandresponse.imoperator.universaliq.UniversalIQProvider;
import com.challengeandresponse.utils.ChatUtils;
import com.challengeandresponse.utils.PropertyThang;

/**
 * This is a base class to make a standard agent in IMOpperator-land. 
 * This class can be extended to make other agents, or use the source as a model for from-scratch agents.
 * 
 * <p>See the documentation for processIQ() and processFinalIQ() below for more information
 * about how the BaseAgent's run loop calls out to a child agent's message processor.</p>
 * 
 * <p>The agent has the following incoming communication channels:<br />
 * - a PacketCollector, receives IQ packets that match registered classes, for processing in the main loop<br />
 * - a PacketListener that calls processPacket() for incoming chat packets. the intent is that chat packets will be a command channel for the agent.<br />
 * </p>
 * <p>Regarding chat messages, the BaseAgent's processPacket() does an Appstack.get() on the text of 
 * the incoming packet and returns the result of the get() to the sender. The text of the incoming
 * message must be a slash-separated app path for anything to happen. The GETALL and CATALOG
 * characters ("*" and "?" respectively, by default) are supported. Implementations may override
 * processPacket(Packet p) to do something else with incoming chat messages if desired.</p>
 * 
 * <p>When the agent is unavailable:<br />
 * The Smack library's PacketCollector user here CONTINUES receiving IQ messages from the server
 * even if cancel() has been called on it, and even if its Presence is "unavailable". Thus,
 * packets sent to a disinterested agent based on this class will be LOST. The SimpleXMPPConnection
 * does test for presence and throws an exception if the addressee's Presence (as of the last
 * update) is "unavailable".</p>
 * 
 * <p>When extending this class:<br />
 * Set up the filter completely before calling connect() (from within the code
 * here) or run() (which calls connect() on its own)... that is, the filter
 * should be set once and locked down. The effects of changes while running
 * are an implementation detail of Smack, not of this library.
 * </p>
 * <p>
 * The child agent's init() method is the place to put AT LEAST one call to
 * addRecognizedClass() -- if you don't do this, the agent won't hear any
 * IQ messages. Then, in its processIQ method, handle the incoming messages.
 * Only classes registered with addRecognizedClass() will be passed to processIQ().
 * </p>
 * 
 * <p>The core class provides control methods:<br />
 * <li>to terminate the agent, call its shutdown("now") method - run() will terminate (text msg: shutdown/now)</li>
 * <li>? retrieves the catalog, as usual in Appstack</li>
 * <li>* retrieves "everything" (where it makes sens to do so, e.g. getStatus(new AppStackSlashPath("*")))</li>
 * </p>
 * 
 * 
 * @author jim
 */
public abstract class BaseAgent
extends AppStack
implements Runnable, PacketListener, PacketFilter, ConnectionListener  {
	
	private String hostname;
	private int portnum;
	private String servicename;
	private String username;
	private String password;
	private String resource;
	private EventLoggerI el;
	
	// IQ XML -> OBJECT translation via the universal provider
	private ProviderManager pm;
	private UniversalIQProvider uiqp;

	private boolean running;
	private int loopSleepTime;
	private int shutdownSleepTime;
	
	private PropertyThang status;
	private SimpleXMPPConnection xmppc;
	
	private PacketCollector iqPc; 		// collects IQ packets containing M2M traffic with classes matching those in the iqPcf
	private CompositeFilter iqCompositeFilter;  // composite filter for the PacketCollector
	
	public static final String BASE_NAMESPACE = "com.challengeandresponse.imoperator.agent.BaseAgent";
	
	/**
	 * After calling the constructor. you must call addRecognizedClass() with at least
	 * one class to finish defining the agent, otherwise it won't see any IQ traffic. The only packets
	 * that reach it are those whose classes were declared via the addRecognizedClass() methods (or both). 
	 * Classes should all be instances of IQ
	 * 
	 * @param el An eventlogger to write events to 
	 * @param hostname the hostname to connect to, or null if only using a servicename (preferred)
	 * @param portnum the port number to connect to on the host (ignored if hostname is null)
	 * @param servicename the name of the service to connect to (preferred way to connect)
	 * @param username the username to login as
	 * @param password the password for that username
	 * @param resource the resource name to use, completing this agent's JID (username@host/resource)
	 * @throws AppStackException
	 */
	public BaseAgent(
			EventLoggerI el, 
			String hostname, 
			int portnum, 
			String servicename,
			String username, 
			String password, 
			String resource)
	throws AppStackException {
		this.el = el;
		this.hostname = hostname;
		this.portnum = portnum;
		this.servicename = servicename;
		this.username = username;
		this.password = password;
		this.resource = resource;
		
		this.status = new PropertyThang(BASE_NAMESPACE);
		
		this.xmppc = null;

		iqCompositeFilter = new CompositeFilter();
		iqPc = null;
		pm = ProviderManager.getInstance();
		uiqp = new UniversalIQProvider();
		
		this.loopSleepTime = status.getIntProperty("loopSleepTime", 1000);
		this.shutdownSleepTime = status.getIntProperty("shutdownSleepTime", 2000);
		this.running = false;

		// add the appStack methods
		this.addMethod(Vocabulary.Methods.SHUTDOWN, "shutdown");
		this.addMethod(Vocabulary.Methods.GET_STATUS,"getStatus");
		this.addMethod(Vocabulary.Methods.DATE,"date");
	}

		
	/**
	 * Add a "recognized class" for this agent's PacketCollector
	 * This method adds the class to the IQ packet filter for the agent, and also
	 * registers the UniversalIQProvider to marshal IQ's into local objects.
	 * Classes should be descendants of UniversalIQ.
	 * @param recognizedClass the class to add
	 */
	@SuppressWarnings("unchecked")
	public void addPCRecognizedClass(Class recognizedClass) {
		addRecognizedClass(recognizedClass);
		iqCompositeFilter.addIncludeFilter(new PacketClassFilter(recognizedClass));
	}
	
	
	/**
	 * Add a "recognized class" for this agent, but DO NOT add it to the included,
	 * recognized classes for the agent's PacketCollector. This is useful for the case
	 * that a separate PacketCollector is created, to receive instances of a class,
	 * and the BaseAgent's provided PacketCollector should ignore those messages.
	 * As with addPCRecognizedClass, classes should be descendants of UniversalIQ.
	 * 
	 * @param recognizedClass the class to add
	 */
	@SuppressWarnings("unchecked")
	public void addRecognizedClass(Class recognizedClass) {
		if (recognizedClass != null)
			pm.addIQProvider("query", recognizedClass.getName(), uiqp);
	}
	
	
	/**
	 * Add a collection of "recognized classes" for this agent's PacketCollector
	 * This method adds all the Listed classes to the IQ packet filter for the agent, and also
	 * registers the UniversalIQProvider to marshall IQ's into local objects.
	 * Classes should be descendants of UniversalIQ.
	 * @param recognizedClass a List of Classes that this agent can service
	 */
	@SuppressWarnings("unchecked")
	public void addPCRecognizedClass(List <Class> recognizedClass) {
		Iterator <Class> it = recognizedClass.iterator();
		while (it.hasNext()) {
			addPCRecognizedClass(it.next());
		}
	}

	/**
	 * Add a collection of "recognized classes" for the agent's UniversalIQProvider,
	 * but DO NOT register the class with the BaseAgent's standard PacketCollector.
	 * See addRecognizeClass(Class) for use case discussion.
	 * 
	 * As with addPCRecognizedClass, classes should be descendants of UniversalIQ.
	 * 
	 * @param recognizedClasses a List of Classes that this agent can service
	 */
	@SuppressWarnings("unchecked")
	public void addRecognizedClass(List <Class> recognizedClasses) {
		Iterator <Class> it = recognizedClasses.iterator();
		while (it.hasNext()) {
			addRecognizedClass(it.next());
		}
	}

	
	
	/**
	 * Inclusion is controlled by addRecognizedClass() methods...
	 * exclusion is more fine-grained and any PacketFilter 
	 * @param pf
	 */
	public void addPCIgnoreFilter(PacketFilter pf) {
		if (pf != null)
			iqCompositeFilter.addExcludeFilter(pf);
	}
	
	public void removePCIgnoreFilter(PacketFilter pf) {
		if (pf != null)
			iqCompositeFilter.removeExcludeFilter(pf);
	}
	
	
	/**
	 * Subclasses can call getStatus() to get a view of the parent's 
	 * PropertyThang status structure, but with a custom namespace.
	 * The namespace cannot be the same as the base agent's,
	 * which is found in this class's member called BASE_NAMESPACE.
	 * Any other label (short or long) is valid. The advantage of 
	 * stashing subclass status properties this way is that the base agent
	 * will eventually persist its status structure, so agents can have 
	 * limited persistence by storing their state in the 'status' structure.
	 * </p>
	 * Sample calling:<br />
	 * PropertyThang myPt = super.getStatus("myNamespace");<br />
	 * pt.set...<br />
	 * 
	 * @return a hook to the base agent's status PropertyThang, filtered for the provided namespace. To see the base agent's namespace, use BaseAgent.BASE_NAMESPACE
	 */
	public PropertyThang getStatus(String namespace) {
			return new PropertyThang(status,namespace);
	}

	
	
	/**
	 * Retrieve a single status object for this Agent, or null if the named status object does not exist
	 * @return the property, if a name matches the next name on the stack, the status object if the next item is the ALL symbol, and null if there was no match
	 */
	public Object getStatus(AppStackPathI aspi, Object o) {
		if (! aspi.hasNext()) {
			return null;
		}
		String s = aspi.popNext();
		if (AppStack.getGetAllSymbol().equals(s))
			return status.getPropertyKeysAndValues();
		else if (AppStack.getGetParamsSymbol().equals(s))
			return status.getPropertyKeys();
		else
			return status.getProperty(s,null);
	}

	
	public String shutdown(AppStackPathI aspi, Object o) {
		String s = aspi.popNext();
		if (AppStack.getGetParamsSymbol().equals(s))
			return "now";
		else if ("now".equals(s)) {
			xmppc.sendPresence(Presence.Type.unavailable, Presence.Mode.away);
			iqPc.cancel(); // stop additional packets from being collected
			this.running = false;
			return "Shutting down";
		}
		else
			return null;
	}

	/**
	 * Just returns the current date and time. aspi is ignored.
	 * @param aspi
	 * @return the current date and time
	 */
	public String date(AppStackPathI aspi, Object o) {
		return (new Date()).toString();
	}

	/**
	 * Get the XMPPConnection that's used by this agent.
	 * Note that the connection could be null, and that no promises are made about the 
	 * state of the connection (open, closed, logged in or not, etc).
	 * @return the XMPPConnection that's used by this agent... could be null.
	 */
	public SimpleXMPPConnection getXmppConnection() {
		return xmppc;
	}
	
	
	
	////// METHODS FOR THE *CHAT* INTERFACE TO THIS AGENT, FOR ADMIN -- these are all directly executed by the AppStack ///////
	public boolean accept(Packet packet) {
		// packet must be a message, and the message must be of type 'chat'
		if ( (packet instanceof Message) && (((Message) packet).getType() == Message.Type.chat) ) {
			return true;
		}
		return false;
	}
	/**
	 * The provided processPacket() method expects that all incoming chat messages are 
	 * AppStackSlash commands (such as "shutdown") and just calls AppStack.get() with
	 * the messages as the argument. Any returned valued from the get() call is 
	 * written back to the sender as a chat message, using the ChatUtils.objectToString 
	 * method to make a best-effort at converting various return types to reasonable hat-compatible text.
	 */
	public void processPacket(Packet packet) {
		String message = ((Message) packet).getBody();
		try {
			xmppc.sendMessage(packet.getFrom(), ChatUtils.objectToString(get(new AppStackDelimitedPath(message),null),"\n"));
		}
		catch (AppStackException ase) {
			xmppc.sendNoExceptionMessage(packet.getFrom(), "Exception: "+ase.getMessage());
		}
		catch (SimpleXMPPException sxe) {
		}
	}


	
	/**
	 * run() doesn't do anything special and could be overridden in a subclass...
	 * a basic always-alive loop is provided here to make things easier.
	 * <p>run() also gathers a few statistics and self-adjusts the between-loops
	 * idle time.
	 */
	public void run() {
		// TODO: retrieve stashed state from somewhere ?
		
		status.setProperty(Vocabulary.Status.AGENT_START_TIME,System.currentTimeMillis());
		status.setProperty(Vocabulary.Status.RUNTIME_LAST_PASS,0);
		status.setProperty(Vocabulary.Status.RUNTIME_TOTAL,0);
		status.setProperty(Vocabulary.Status.RUNTIME_NRUNS,0);
		status.setProperty(Vocabulary.Status.RUNTIME_MEAN,0);
		
		el.addEvent("Connecting agent to server");
		try {
			el.addEvent("Configuring SimpleXMPPConnection. Host:"+hostname+" Port:"+portnum+" Service name: "+servicename);
			XMPPConfig config = new XMPPConfig(hostname, portnum, resource, servicename, username, password);
			xmppc = new SimpleXMPPConnection(config,true);
			xmppc.secureConnect();
// OLD: the 3 lines above use the revised version of SimpleXMPPConnection
//			if (hostname != null)
//				xmppc = new SimpleXMPPConnection(hostname, portnum, servicename, true);
//			else 
//				xmppc = new SimpleXMPPConnection(null, -1, servicename, true);
//			xmppc.secureConnect(username,password,resource);

			status.setProperty(Vocabulary.Status.AGENT_JID,xmppc.getXMPPConnection().getUser());
			// the agent has connectionListener methods to allow it to monitor its connection status
			xmppc.getXMPPConnection().addConnectionListener(this);
			/// CHAT PACKETS - are sent to a PacketListener.
			xmppc.addPacketListenerAndFilter(this,this);
			// run the subclass's init method, if there is one
			el.addEvent("Agent started. Calling init() for local initializations.");
			running = true;
		}
		catch (Exception e) {
			el.addEvent("Exception during pre-init() startup. Agent startup cancelled. Exception: "+e.getMessage());
			running = false;
			if (xmppc != null)
				xmppc.disconnect();
		}
		
		try {
			// only proceed if the above did not blow up
			if (running) {
				init(); // call the subclass's init() to set up things
				
				// init may set up the composite filter, so don't bind it til here...
				/// IQ PACKETS -- are gathered in a PacketCollector and processed in the main loop
				System.out.println("iqCompositeFilter is: "+iqCompositeFilter);
				iqPc = xmppc.getXMPPConnection().createPacketCollector(iqCompositeFilter);
				running = true;
				// ready to go, so show the agent presence as "available"
				xmppc.sendPresence(Presence.Type.available,Presence.Mode.available);
			}
		}
		catch (Exception e) {
			el.addEvent("Exception during call to init(). Agent startup cancelled. Exception: "+e.getMessage());
			running = false;
		}
		

		// main processing loop
		// make the agent 'available' then enter the servicing loop
		while (running) {
			long startTime = System.currentTimeMillis();
			processIQ(iqPc);
			int runtime = (int) (System.currentTimeMillis() - startTime);

			status.setProperty(Vocabulary.Status.RUNTIME_LAST_PASS,runtime);
			status.setProperty(Vocabulary.Status.RUNTIME_TOTAL,status.getIntProperty(Vocabulary.Status.RUNTIME_TOTAL, 0)+runtime);
			status.setProperty(Vocabulary.Status.RUNTIME_NRUNS,status.getIntProperty(Vocabulary.Status.RUNTIME_NRUNS, 0)+1);
			status.setProperty(Vocabulary.Status.RUNTIME_MEAN,
				status.getIntProperty(Vocabulary.Status.RUNTIME_TOTAL, 0)/status.getIntProperty(Vocabulary.Status.RUNTIME_NRUNS, 1));

			sleepMsec(loopSleepTime);
		}

		/// SHUTTING DOWN... After main loop terminates, these shutdown processes happen
		el.addEvent("Calling destroy() before shutdown");
		destroy(iqPc);
		
		// sleep briefly, to allow outgoing traffic to, well, go out
		el.addEvent("Agent disconnecting from server");
		sleepMsec(shutdownSleepTime);
		// drop the connection to the server. disconnect() sets an unavailable presence type
		xmppc.disconnect();

		//TODO: stash state somewhere before shutting down so that restart picks up where this left off
		//TODO: stash unprocessed packets too? should there be a shutdown arg to ask for that?
	}

	
	/**
	 * init() is called after the agent is connected to the server, and before the 
	 * event loop starts. Use init() for any one-time initializations to run at startup.
	 * 
	 * @throws Exception of your choice, if an error occurs during startup. If an exception is thrown by init() the agent will not start, but will terminate, logging the exception
	 */
	public abstract void init()
	throws Exception;
	
	
	/**
	 * To implement an agent using BaseAgent as a starting point, extend this class and implement
	 * the processIQ() method to operate on the messages in the PacketCollector. 
	 * processIQ is called and blocks until it's complete... processIQ can run as long 
	 * as it needs to... it's called from within a loop in the run() method of the BaseAgent
	 * and that loop doesn't do anything significant, other than calling processIQ().
	 * 
	 * If processing can block significantly at any point, implementations should spawn separate
	 * processing threads, so that overall throughput is not trashed by a single slow process.
	 * 
	 * @param pc a PacketCollector with inbound packets for this agent
	 */
	public abstract void processIQ(PacketCollector pc);
		
	/**
	 * The destroy() method is called as the agent is shutting down.
	 * 
	 * <p>At the time this is called:<br />
	 * The agent will be marked publicly "unavailable"<br />
	 * Everything possible will have been done to stop additional incoming messages<br />
	 * The agent's connection to the server is still up, and the agent is still logged in to the server<br />
	 * </p>
	 * destroy() is provided as a method to allow one final pass over the PacketCollector before the agent shutdown.
	 * 
	 * <p>The shutdown process blocks until destroy() returns.</p>
	 * 
	 * <p>IMPORTANT: pc or xmppc or both could be NULL -- for example, if startup fails
	 * and no xmpp connection could be made. However, destroy is always called so that
	 * other finishing steps (started in init()) can be taken care of, such as closing
	 * database files.
	 * 
	 * <p>It is OK to have an empty destroy()  method that just returns, but probably
	 * something like the below one-liner would be more useful...<br />
	 * That is, if you have nothing special to do at shutdown time (for example,
	 * caching any unfinished work, whatever) just call back to the usual processor, 
	 * and run it one more time to be sure it's empty.<br />
	 * <code>
	 * public void destroy(PacketCollector pc, XMPPConnection xmppc) {<br />
	 * &nbsp; if ((pc != null) && (xmppc != null))<br />
	 * &nbsp;&nbsp;&nbsp; processIQ(pc);<br />
	 * &nbsp; // and the rest of the agent's shutting-down code can go here<br />
	 * }<br />
	 * </code>
	 * </p>
	 * 
	 * @param pc a PacketCollector with inbound packets for this agent
	 */
	public abstract void destroy(PacketCollector pc);
	
	
	////// CONNECTION LISTENER STUFF
	
	/**
	 * The base agent implements all the methods of ConnectionListener interface.
	 * These methods may be overridden in subclasses to provide more functionality.
	 * In the base agent, the ConnectionListener methods simply post connection-related
	 * events to the EventLogger
	 */
	public void connectionClosed() {
		el.addEvent("Connection closed");
	}

	public void connectionClosedOnError(Exception e) {
		el.addEvent("Connection closed on error. Agent terminating: "+e.getMessage());
		running = false;
	}
	
	public void reconnectingIn(int seconds) {
		el.addEvent("Connection reconnecting in "+seconds+" seconds");
	}

	public void reconnectionFailed(Exception e) {
		el.addEvent("Connection reconnect failed: "+e.getMessage());
	}

	public void reconnectionSuccessful() {
		el.addEvent("Connection reconnect successful");
		xmppc.sendPresence(Presence.Type.available,Presence.Mode.available);
	}
	

	/**
	 * this method is provided so that subclasses can use the eventLogger that they
	 * configure in the constructor, without having to keep a private reference to it
	 * @return the EventLogger associated with this agent, or null if there is one.
	 */
	public final EventLoggerI getEventLogger() {
		return el;
	}



	/**
	 * Convenience method to delay for a number of msec
	 * @param msec
	 */
	public static void sleepMsec(int msec) {
		try {
			Thread.sleep(msec);
		}
		catch (InterruptedException e) { 
		}
	}

	
	
}
