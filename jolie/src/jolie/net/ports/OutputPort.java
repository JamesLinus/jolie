/***************************************************************************
 *   Copyright 2006-2011 (C) by Fabrizio Montesi <famontesi@gmail.com>     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.net.ports;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import jolie.Interpreter;
import jolie.StatefulContext;
import jolie.behaviours.AssignmentBehaviour;
import jolie.behaviours.Behaviour;
import jolie.behaviours.NullBehaviour;
import jolie.behaviours.SequentialBehaviour;
import jolie.lang.Constants;
import jolie.net.CommChannel;
import jolie.net.CommMessage;
import jolie.net.protocols.AsyncCommProtocol;
import jolie.net.protocols.CommProtocol;
import jolie.runtime.AbstractIdentifiableObject;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;
import jolie.runtime.VariablePathBuilder;
import jolie.runtime.expression.Expression;
import jolie.runtime.typing.OperationTypeDescription;
import jolie.util.LocationParser;

/**
 * This class represents a JOLIE output port, offering methods for getting
 * proper communication channels for it.
 *
 * @author Fabrizio Montesi
 */
public class OutputPort extends AbstractIdentifiableObject implements Port
{
	private final Interpreter interpreter;
	private final Behaviour configurationProcess;
	private Expression locationExpression;
	private final VariablePath locationVariablePath, protocolVariablePath;
	private final boolean isConstant;
	private final Interface iface;

	/* To be called at runtime, after main is run.
	 * Requires the caller to set the variables by itself.
	 */
	public OutputPort( Interpreter interpreter, String id )
	{
		super( id );
		this.interpreter = interpreter;

		this.protocolVariablePath =
					new VariablePathBuilder( false )
					.add( id(), 0 )
					.add( Constants.PROTOCOL_NODE_NAME, 0 )
					.toVariablePath();
		
		this.locationVariablePath =
					new VariablePathBuilder( false )
					.add( id(), 0 )
					.add( Constants.LOCATION_NODE_NAME, 0 )
					.toVariablePath();

		this.locationExpression = this.locationVariablePath;

		this.configurationProcess = null;
		this.isConstant = false;

		this.iface = Interface.UNDEFINED;
	}
	
	public OutputPort(
		Interpreter interpreter,
		String id,
		VariablePath locationVariablePath,
		VariablePath protocolVariablePath,
		Interface iface,
		boolean isConstant
	) {
		super( id );
		this.isConstant = isConstant;
		this.interpreter = interpreter;
		this.locationVariablePath = locationVariablePath;
		this.protocolVariablePath = protocolVariablePath;
		this.iface = iface;
		this.locationExpression = locationVariablePath;
		this.configurationProcess = NullBehaviour.getInstance();
	}
	
	/**
	 * To be called by OOITBuilder
	 * @param interpreter
	 * @param id
	 * @param protocolId
	 * @param protocolConfigurationProcess
	 * @param locationURI
	 * @param iface
	 * @param isConstant
	 */
	public OutputPort(
			Interpreter interpreter,
			String id,
			String protocolId,
			Behaviour protocolConfigurationProcess,
			URI locationURI,
			Interface iface,
			boolean isConstant
	) {
		super( id );
		this.isConstant = isConstant;
		this.interpreter = interpreter;
		this.iface = iface;

		this.protocolVariablePath =
					new VariablePathBuilder( false )
					.add( id(), 0 )
					.add( Constants.PROTOCOL_NODE_NAME, 0 )
					.toVariablePath();

		this.locationVariablePath =
					new VariablePathBuilder( false )
					.add( id(), 0 )
					.add( Constants.LOCATION_NODE_NAME, 0 )
					.toVariablePath();

		this.locationExpression = locationVariablePath;
		
		// Create the configuration Process
		Behaviour a = ( locationURI == null ) ? NullBehaviour.getInstance() : 
			new AssignmentBehaviour( this.locationVariablePath, Value.create( locationURI.toString() ) );

		List< Behaviour > children = new LinkedList<>();
		children.add( a );
		if ( protocolId != null ) {
			children.add(new AssignmentBehaviour( this.protocolVariablePath, Value.create( protocolId ) ) );
		}
		children.add( protocolConfigurationProcess );
		this.configurationProcess = new SequentialBehaviour( children.toArray(new Behaviour[ children.size() ] ) );
	}
	
	/**
	 * Returns a new message with same operation and value, but resourcePath
	 * updated to the current one of this output port.
	 * @param message the original message
	 * @return a new message with same operation and value, but updated resource
	 * @throws java.net.URISyntaxException
	 */
	public CommMessage createAggregatedRequest(CommMessage message )
		throws URISyntaxException
	{
		return new CommMessage(
			CommMessage.getNewMessageId(),
			message.operationName(),
			getResourcePath(),
			message.value(),
			message.fault()
		);
	}

	@Override
	public Interface getInterface()
	{
		return iface;
	}

	public void optimizeLocation( StatefulContext ctx )
	{
		if ( isConstant ) {
			locationExpression = locationVariablePath.getValue( ctx );
		}
	}

	@Override
	public VariablePath protocolConfigurationPath()
	{
		return protocolVariablePath;
	}

	/**
	 * Gets the protocol to be used for communicating with this output port.
	 * @return the protocol to be used for communicating with this output port.
	 * @throws java.io.IOException
	 * @throws java.net.URISyntaxException
	 */
	public CommProtocol getProtocol(StatefulContext ctx)
		throws IOException, URISyntaxException
	{
		String protocolId = protocolVariablePath.getValue( ctx ).strValue();
		if ( protocolId.isEmpty() ) {
			throw new IOException( "Unspecified protocol for output port " + id() );
		}
		AsyncCommProtocol protocol = (AsyncCommProtocol)interpreter.commCore().createOutputCommProtocol(
			protocolId,
			protocolVariablePath,
			new URI( locationExpression.evaluate().strValue() )
		);
		protocol.initialize( ctx );
		return protocol;
	}

	
	private CommChannel getCommChannel(StatefulContext ctx,  boolean forceNew )
		throws URISyntaxException, IOException
	{
		CommChannel ret;
		Value loc = locationExpression.evaluate();
		if ( loc.isChannel() ) {
			// It's a local channel
			ret = loc.channelValue();
			if ( forceNew ) {
				ret = ret.createDuplicate();
			}
		} else {
			URI uri = getLocation( loc );
			if ( forceNew ) {
				// A fresh channel was requested
				ret = interpreter.commCore().createCommChannel( uri, this, ctx );
			} else {
				// Try reusing an existing channel first
				String protocol = protocolVariablePath.getValue( ctx ).strValue();
				ret = interpreter.commCore().getPersistentChannel( uri, protocol, ctx );
				if ( ret == null ) {
					ret = interpreter.commCore().createCommChannel( uri, this, ctx );
				}
			}
		}

		ret.setParentOutputPort( this );
		return ret;
	}

	private static class LazyLocalUriHolder {
		private LazyLocalUriHolder() {}
		private static final URI uri = URI.create( "local" );
	}

	private static final Map< String, URI > uriCache = new WeakHashMap<>();

	/**
	 * Returns the resource path of the location of this output port.
	 * @return the resource path of the location of this output port
	 * @throws java.net.URISyntaxException
	 */
	public String getResourcePath()
		throws URISyntaxException
	{
		Value location = locationExpression.evaluate();
		if ( location.isChannel() ) {
			return "/";
		}
		return LocationParser.getResourcePath( getLocation( location ) );
	}

	private URI getLocation( Value location )
		throws URISyntaxException
	{
		if ( location.isChannel() ) {
			return LazyLocalUriHolder.uri;
		}
		String s = location.strValue();
		URI ret;
		synchronized( uriCache ) {
			if ( (ret=uriCache.get( s )) == null ) {
				ret = new URI( s );
				uriCache.put( s, ret );
			}
		}
		return ret;
	}

	/**
	 * Returns a new and unused CommChannel for this OutputPort
	 * @return a CommChannel for this OutputPort
	 * @throws java.net.URISyntaxException
	 * @throws java.io.IOException
	 */
	public final CommChannel getNewCommChannel(StatefulContext ctx)
		throws URISyntaxException, IOException
	{
		return getCommChannel(ctx, true );
	}

	/**
	 * Returns a CommChannel for this OutputPort, possibly reusing an
	 * open persistent channel.
	 * @return a CommChannel for this OutputPort
	 * @throws java.net.URISyntaxException
	 * @throws java.io.IOException
	 */
	public final CommChannel getCommChannel(StatefulContext ctx)
		throws URISyntaxException, IOException
	{
		return getCommChannel(ctx, false );
	}

	/**
	 * Returns the location variable path of this output port.
	 * @return the location variable path of this output port
	 */
	public VariablePath locationVariablePath()
	{
		return locationVariablePath;
	}

	/**
	 * Returns the protocol configuration process of this output port.
	 * @return the protocol configuration process of this output port
	 */
	public Behaviour configurationProcess()
	{
		return configurationProcess;
	}
	
	public OperationTypeDescription getOperationTypeDescription( String operationName, String resourcePath )
	{
		OperationTypeDescription ret = iface.oneWayOperations().get( operationName );
		if ( ret == null ) {
			ret = iface.requestResponseOperations().get( operationName );
		}
		
		return ret;
	}
}
