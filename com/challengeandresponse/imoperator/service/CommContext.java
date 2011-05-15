package com.challengeandresponse.imoperator.service;

import java.util.HashSet;

import com.challengeandresponse.utils.PropertyThang;


/**
 * CommContext is a PropertyThang, with a dictionary locked in.
 * CommContext provides the source and destination of a message, the persistent
 * local ID of a message (the IMOperator id), and info or pointers to info about the
 * sender that is useful for processing the message.
 * This is still not well defined. For now we focus on which service the message
 * came from, the username at that service for writing back to, and the user's
 * IMOperator identity.
 * 
 * @author jim
 *
 */
public class CommContext extends PropertyThang {

	public static final String PROP_SERVICE="service";
	public static final String PROP_SERVICE_USERNAME = "serviceUsername";
	public static final String PROP_SERVICE_USERNAME_NO_RESOURCE = "serviceUsernameNoResource";
	public static final String PROP_IMOP_USERNAME = "imoperatorUsername";
	public static final String PROP_MESSAGE_TO = "messageTo";
	
	public static final int	VALUE_SERVICE_XMPP = 1;
	public static final int	VALUE_SERVICE_AIM = 2;
	public static final int	VALUE_SERVICE_YAHOO = 3;
	
	// build the dictionary of named properties 
	private static HashSet <String> PROP_DICTIONARY;
	static {
		PROP_DICTIONARY = new HashSet <String> ();
		PROP_DICTIONARY.add(PROP_SERVICE);
		PROP_DICTIONARY.add(PROP_SERVICE_USERNAME);
		PROP_DICTIONARY.add(PROP_IMOP_USERNAME);
		PROP_DICTIONARY.add(PROP_MESSAGE_TO);
		PROP_DICTIONARY.add(PROP_SERVICE_USERNAME_NO_RESOURCE);
	}
	

	

	public CommContext(String namespace) {
		super(namespace);
		setDictionary(PROP_DICTIONARY);
	}
	
	

}
