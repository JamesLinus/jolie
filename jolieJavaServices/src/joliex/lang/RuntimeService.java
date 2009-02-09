/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
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

package joliex.lang;

import jolie.lang.Constants;
import jolie.ExecutionThread;
import jolie.Interpreter;
import jolie.lang.Constants.EmbeddedServiceType;
import jolie.net.CommListener;
import jolie.net.CommMessage;
import jolie.net.LocalCommChannel;
import jolie.net.OutputPort;
import jolie.runtime.embedding.EmbeddedServiceLoader;
import jolie.runtime.embedding.EmbeddedServiceLoaderCreationException;
import jolie.runtime.embedding.EmbeddedServiceLoadingException;
import jolie.runtime.FaultException;
import jolie.runtime.InvalidIdException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;

public class RuntimeService extends JavaService
{
	private Interpreter interpreter;
	
	public RuntimeService()
	{
		this.interpreter = Interpreter.getInstance();
	}
	
	public CommMessage getLocalLocation( CommMessage message )
	{
		Value v = Value.create();
		v.setValue( new LocalCommChannel( interpreter, interpreter.commCore().localListener() ) );
		return CommMessage.createResponse( message, v );
	}
	
	public CommMessage setOutputPort( CommMessage message )
		throws FaultException
	{
		String name = message.value().getFirstChild( "name" ).strValue();
		Value locationValue = message.value().getFirstChild( "location" );
		Value protocolValue = message.value().getFirstChild( "protocol" );
		OutputPort port =
			new OutputPort(
					interpreter(),
					name
				);
		Value l;
		Value r = interpreter.mainThread().state().root();
		l =	r.getFirstChild( name ).getFirstChild( Constants.LOCATION_NODE_NAME );
		if ( locationValue.isChannel() ) {
			l.setValue( locationValue.channelValue() );
		} else {
			l.setValue( locationValue.strValue() );
		}
		r.getFirstChild( name ).getFirstChild( Constants.PROTOCOL_NODE_NAME ).deepCopy( protocolValue );

		r = ExecutionThread.currentThread().state().root();
		l =	r.getFirstChild( name ).getFirstChild( Constants.LOCATION_NODE_NAME );
		if ( locationValue.isChannel() ) {
			l.setValue( locationValue.channelValue() );
		} else {
			l.setValue( locationValue.strValue() );
		}
		r.getFirstChild( name ).getFirstChild( Constants.PROTOCOL_NODE_NAME ).deepCopy( protocolValue );

		interpreter.register( name, port );
		return null;
	}
	
	public CommMessage removeOutputPort( CommMessage message )
	{
		interpreter.removeOutputPort( message.value().strValue() );
		return null;
	}
	
	public CommMessage setRedirection( CommMessage message )
		throws FaultException
	{
		CommMessage ret = null;
		String serviceName = message.value().getChildren( "inputPortName" ).first().strValue();
		CommListener listener =
			interpreter.commCore().getListenerByInputPortName( serviceName );
		if ( listener == null )
			throw new FaultException( "RuntimeException", "Unknown inputPort: " + serviceName );
		
		String resourceName = message.value().getChildren( "resourceName" ).first().strValue();
		String opName = message.value().getChildren( "outputPortName" ).first().strValue();
		try {
			OutputPort port = interpreter.getOutputPort( opName );
			listener.redirectionMap().put( resourceName, port );
		} catch( InvalidIdException e ) {
			throw new FaultException( "RuntimeException", e );
		}
		
		ret = CommMessage.createResponse( message, Value.create() );
		return ret;
	}
	
	public CommMessage removeRedirection( CommMessage message )
		throws FaultException
	{
		CommMessage ret = null;
		String serviceName = message.value().getChildren( "inputPortName" ).first().strValue();
		CommListener listener =
			interpreter.commCore().getListenerByInputPortName( serviceName );
		if ( listener == null )
			throw new FaultException( "RuntimeException", "Unknown inputPort: " + serviceName );
		
		String resourceName = message.value().getChildren( "resourceName" ).first().strValue();
		listener.redirectionMap().remove( resourceName );
		ret = CommMessage.createResponse( message, Value.create() );
		return ret;
	}

	public CommMessage getRedirection( CommMessage message )
		throws FaultException
	{
		CommMessage ret = null;
		String inputPortName = message.value().getChildren( "inputPortName" ).first().strValue();
		CommListener listener =
			interpreter.commCore().getListenerByInputPortName( inputPortName );
		if ( listener == null ) {
			throw new FaultException( "RuntimeException", Value.create( "Invalid input port: " + inputPortName ) );
		}
		
		String resourceName = message.value().getChildren( "resourceName" ).first().strValue();
		OutputPort p = listener.redirectionMap().get( resourceName );
		if ( p == null ) {
			ret = CommMessage.createResponse( message, Value.create() );
		} else {
			ret = CommMessage.createResponse( message, Value.create( p.id() ) );
		}
		return ret;
	}
	
	public CommMessage loadEmbeddedService( CommMessage message )
		throws FaultException
	{
		try {
			Value channel = Value.create();
			String filePath = message.value().getFirstChild( "filepath" ).strValue();
			String typeStr = message.value().getFirstChild( "type" ).strValue();
			EmbeddedServiceType type =
				jolie.lang.Constants.stringToEmbeddedServiceType( typeStr );
			EmbeddedServiceLoader loader =
				EmbeddedServiceLoader.create( interpreter(), type, filePath, channel );
			loader.load();

			return CommMessage.createResponse( message, channel );
		} catch( EmbeddedServiceLoaderCreationException e ) {
			e.printStackTrace();
			throw new FaultException( "RuntimeException", e );
		} catch( EmbeddedServiceLoadingException e ) {
			e.printStackTrace();
			throw new FaultException( "RuntimeException", e );
		}
	}

	public CommMessage callExit( CommMessage request )
	{
		Object o = request.value().valueObject();
		if ( o instanceof LocalCommChannel ) {
			((LocalCommChannel)o).interpreter().exit();
		}
		return CommMessage.createResponse( request, Value.create() );
	}
}
