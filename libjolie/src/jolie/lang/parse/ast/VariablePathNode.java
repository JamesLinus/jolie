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

package jolie.lang.parse.ast;

import java.util.LinkedList;
import java.util.List;

import jolie.util.Pair;


public class VariablePathNode
{
	private List< Pair< OLSyntaxNode, OLSyntaxNode > > path;
	private OLSyntaxNode attribute = null;
	private boolean global;

	public VariablePathNode( boolean global )
	{
		path = new LinkedList< Pair< OLSyntaxNode, OLSyntaxNode > >();
		this.global = global;
	}
	
	public boolean isGlobal()
	{
		return global;
	}
	
	public void append( Pair< OLSyntaxNode, OLSyntaxNode > node )
	{
		path.add( node );
	}
	
	public List< Pair< OLSyntaxNode, OLSyntaxNode > > path()
	{
		return path;
	}
	
	public OLSyntaxNode attribute()
	{
		return attribute;
	}
	
	public void setAttribute( OLSyntaxNode attribute )
	{
		this.attribute = attribute;
	}
}
